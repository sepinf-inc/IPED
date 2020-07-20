package br.gov.pf.labld.graph;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pf.labld.graph.GraphImportRunner.ImportListener;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.LocalConfig;

public class GraphGenerator {

    private static Logger LOGGER = LoggerFactory.getLogger(GraphGenerator.class);

    private static final Pattern HASH_LIKE_CONTACT = Pattern
            .compile("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{4}-[a-zA-Z0-9]{12}");

    public boolean generate(File output, File... input) throws IOException {
        return this.generate(null, output, input);
    }

    public boolean generate(ImportListener listener, File output, File... input) throws IOException {
        boolean imported = importDB(listener, output, input);

        if (imported) {
            runPostImportOps(output);
        }

        return imported;
    }

    private void runPostImportOps(File output) throws IOException {
        GraphService graphService = null;
        try {
            graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
            graphService.start(output);
            GraphConfiguration config = GraphTask
                    .loadConfiguration(new File(Configuration.getInstance().configPath, "conf"));

            runPostGenerationStatements(graphService, config);
            groupContacts(graphService, config);

        } finally {
            if (graphService != null) {
                graphService.stop();
            }
        }
    }

    private boolean importDB(ImportListener listener, File output, File... input) {
        try {
            LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                    .iterator().next();
            GraphImportRunner runner = new GraphImportRunner(listener, input);
            runner.run(output, GraphTask.DB_NAME, localConfig.isOutputOnSSD());
            return true;
        } catch (Exception e) {
            LOGGER.error("Error generating database.", e);
            return false;
        }
    }

    public void runPostGenerationStatements(GraphService graphService, GraphConfiguration config) {
        long start = System.currentTimeMillis();
        LOGGER.info("Running post generation statements.");
        GraphDatabaseService graphDB = graphService.getGraphDb();
        Transaction tx = null;
        try {
            tx = graphDB.beginTx();

            for (String stmt : config.getPostGenerationStatements()) {
                LOGGER.info("Running {}", stmt);
                graphDB.execute(stmt);
            }

            tx.success();
        } finally {
            tx.close();
        }
        LOGGER.info("Finished running post generation statements in " + (System.currentTimeMillis() - start) + "ms.");
    }

    public static void main(String[] args) throws Exception {
        // String path = args[0];
        // String configPath = args[1];
        String path = "/media/positivo/707e68d4-1326-40a5-81b6-65c842c4240d/databases";
        String configPath = "/home/positivo/projetos/iped/iped/resources/config/conf";

        GraphService graphService = null;
        try {
            graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
            graphService.start(new File(path));
            GraphConfiguration config = GraphTask.loadConfiguration(new File(configPath));

            GraphGenerator graphGenerator = new GraphGenerator();
            graphGenerator.groupContacts(graphService, config);
        } catch (Exception e) {
            LOGGER.error("Error generating database.", e);
        } finally {
            if (graphService != null) {
                graphService.stop();
            }
        }

    }

    private void groupContacts(GraphService graphService, GraphConfiguration config, String label) {
        String query = "MATCH (c: " + label
                + ")--(n:EVIDENCIA {category:'Contatos'})--(in:EVIDENCIA {source:'UfedXmlReader'}) WHERE NOT (n.name ENDS WITH 'sqlite') RETURN in as input, n.name as name, n as evidence, c as con ORDER BY c.nodeId";

        GraphDatabaseService graphDB = graphService.getGraphDb();
        Transaction tx = null;
        try {
            tx = graphDB.beginTx();

            Result result = graphDB.execute(query);
            Node currentContact = null;
            String currentName = null;
            Map<Long, Node> inputs = new HashMap<>();
            Map<Long, Node> evidences = new HashMap<>();

            int count = 0;

            LOGGER.info("Grouping " + label + " contacts.");

            while (result.hasNext()) {
                Map<String, Object> cols = result.next();
                Node contact = (Node) cols.get("con");

                if (currentContact != null && contact.getId() != currentContact.getId()) {
                    Node newGroup = graphDB.createNode(DynLabel.label("CONTACT_GROUP"));
                    newGroup.setProperty("name", currentName);
                    newGroup.setProperty("isGroup", true);

                    List<Long> groupedIds = evidences.values().stream().map(e -> new Long(e.getId()))
                            .collect(Collectors.toList());
                    newGroup.setProperty("groupedIds", (Long[]) groupedIds.toArray(new Long[groupedIds.size()]));

                    for (Node inputNode : inputs.values()) {
                        inputNode.createRelationshipTo(newGroup,
                                DynRelationshipType.withName(config.getDefaultRelationship()));
                    }

                    for (Node evidence : evidences.values()) {
                        newGroup.createRelationshipTo(evidence,
                                DynRelationshipType.withName(config.getDefaultRelationship()));
                    }

                    newGroup.createRelationshipTo(currentContact,
                            DynRelationshipType.withName(config.getDefaultRelationship()));

                    currentName = null;
                    currentContact = null;
                    inputs.clear();
                    evidences.clear();
                }

                Node input = (Node) cols.get("input");
                inputs.put(input.getId(), input);

                Node evidence = (Node) cols.get("evidence");
                inputs.put(evidence.getId(), evidence);

                String name = getName((String) cols.get("name"));
                if (currentName == null || (name.length() > currentName.length() && !isHashLikeContact(name))) {
                    currentName = name;
                }

                currentContact = contact;
                count++;

                if (count % 1000 == 0) {
                    LOGGER.info("Grouped " + count + " " + label + " contacts.");
                }
            }

            tx.success();
            LOGGER.info("Grouped " + count + " " + label + " contacts.");
        } finally {
            tx.close();
        }
    }

    public void groupContacts(GraphService graphService, GraphConfiguration config) {
        groupContacts(graphService, config, "TELEFONE");
        groupContacts(graphService, config, "EMAIL");
        groupContacts(graphService, config, "FACEBOOK");
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
