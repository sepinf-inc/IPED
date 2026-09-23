package iped.engine.graph.server;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

/**
 * Runs the post-import graph operations against the embedded Neo4j 5 engine, in the isolated graph
 * JVM (antlr 4.13.x). Invoked once by {@code iped.engine.graph.GraphGenerator} after the bulk CSV
 * import. The logic was moved here (from GraphGenerator) so the embedded Cypher engine never loads
 * in the main IPED process, which keeps antlr 4.9.2 for libfqlite. Java 21 migration.
 *
 * <p>
 * Arguments: {@code <dbHome> <dbName> <defaultRelationship> <postGenStatementsFile>}. The statements
 * file holds the config post-generation Cypher statements, one per line.
 * </p>
 */
public class GraphPostImport {

    private static final Pattern HASH_LIKE_CONTACT = Pattern
            .compile("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}");

    private static final String[] CONTACT_GROUP_LABELS = { "TELEFONE", "EMAIL", "FACEBOOK" };

    private final String defaultRelationship;

    private GraphPostImport(String defaultRelationship) {
        this.defaultRelationship = defaultRelationship;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: GraphPostImport <dbHome> <dbName> <defaultRelationship> <statementsFile>");
            System.exit(2);
        }
        File dbHome = new File(args[0]);
        String dbName = args[1];
        String defaultRelationship = args[2];
        File statementsFile = new File(args[3]);

        List<String> statements = statementsFile.isFile()
                ? Files.readAllLines(statementsFile.toPath(), StandardCharsets.UTF_8)
                : Collections.emptyList();

        DatabaseManagementService dms = new DatabaseManagementServiceBuilder(dbHome.toPath())
                .setConfig(GraphDatabaseSettings.initial_default_database, dbName)
                .setConfig(GraphDatabaseSettings.auth_enabled, false)
                .build();
        try {
            GraphDatabaseService db = dms.database(dbName);
            db.isAvailable(30000);

            GraphPostImport postImport = new GraphPostImport(defaultRelationship);
            postImport.runPostGenerationStatements(db, statements);
            postImport.groupContacts(db);
        } finally {
            dms.shutdown();
        }
    }

    private void runPostGenerationStatements(GraphDatabaseService graphDB, List<String> statements) {
        if (statements.isEmpty()) {
            return;
        }
        System.out.println("Running post generation statements.");
        for (String stmt : statements) {
            if (stmt.trim().isEmpty()) {
                continue;
            }
            // Each statement runs in its own transaction: Neo4j 5 forbids mixing schema commands (e.g.
            // CREATE INDEX) with data operations in a single transaction, and isolating them keeps one
            // failing/legacy statement from aborting the whole post-import (including contact grouping).
            try (Transaction tx = graphDB.beginTx()) {
                System.out.println("Running " + stmt);
                tx.execute(stmt);
                tx.commit();
            } catch (Exception e) {
                System.out.println(
                        "WARNING: post-generation statement failed and was skipped: " + stmt + " -> " + e.getMessage());
            }
        }
    }

    private void groupContacts(GraphDatabaseService graphDB) {
        for (String label : CONTACT_GROUP_LABELS) {
            try {
                groupContacts(graphDB, label);
            } catch (Exception e) {
                System.out.println("WARNING: contact grouping failed for label " + label + ": " + e.getMessage());
            }
        }
    }

    private void groupContacts(GraphDatabaseService graphDB, String label) {
        String query = "MATCH (c: " + label
                + ")--(n:EVIDENCIA {category:'Contatos'})--(in:EVIDENCIA {source:'UfedXmlReader'}) WHERE NOT (n.name ENDS WITH 'sqlite') RETURN in as input, n.name as name, n as evidence, c as con ORDER BY c.nodeId";

        try (Transaction tx = graphDB.beginTx()) {
            Result result = tx.execute(query);
            Node currentContact = null;
            String currentName = null;
            Map<Long, Node> inputs = new HashMap<>();
            Map<Long, Node> evidences = new HashMap<>();

            int count = 0;
            System.out.println("Grouping " + label + " contacts.");

            while (result.hasNext()) {
                Map<String, Object> cols = result.next();
                Node contact = (Node) cols.get("con");

                if (currentContact != null && contact.getId() != currentContact.getId()) {
                    createGroup(tx, currentName, inputs, evidences, currentContact);
                    currentName = null;
                    currentContact = null;
                    inputs.clear();
                    evidences.clear();
                }

                Node input = (Node) cols.get("input");
                inputs.put(input.getId(), input);

                Node evidence = (Node) cols.get("evidence");
                evidences.put(evidence.getId(), evidence);

                String name = getName((String) cols.get("name"));
                if (currentName == null || (name.length() > currentName.length() && !isHashLikeContact(name))) {
                    currentName = name;
                }

                currentContact = contact;
                count++;

                if (count % 1000 == 0) {
                    System.out.println("Grouped " + count + " " + label + " contacts.");
                }
            }

            // Flush the last accumulated contact group: the in-loop createGroup only runs on a contact
            // change, so without this the final contact of each label would be dropped.
            if (currentContact != null) {
                createGroup(tx, currentName, inputs, evidences, currentContact);
            }

            tx.commit();
            System.out.println("Grouped " + count + " " + label + " contacts.");
        }
    }

    private void createGroup(Transaction tx, String name, Map<Long, Node> inputs, Map<Long, Node> evidences,
            Node contact) {
        Node newGroup = tx.createNode(Label.label("CONTACT_GROUP"));
        newGroup.setProperty("name", name);
        newGroup.setProperty("isGroup", true);

        // Neo4j stores array properties as primitive arrays.
        long[] groupedIds = evidences.values().stream().mapToLong(Node::getId).toArray();
        newGroup.setProperty("groupedIds", groupedIds);

        RelationshipType relType = RelationshipType.withName(defaultRelationship);
        for (Node inputNode : inputs.values()) {
            inputNode.createRelationshipTo(newGroup, relType);
        }
        for (Node evidence : evidences.values()) {
            newGroup.createRelationshipTo(evidence, relType);
        }
        newGroup.createRelationshipTo(contact, relType);
    }

    private boolean isHashLikeContact(String name) {
        return HASH_LIKE_CONTACT.matcher(name).matches();
    }

    private String getName(String name) {
        String[] split = name.split("_|:", 2);
        if (split.length > 1) {
            name = split[1];
        }
        return name.trim();
    }
}
