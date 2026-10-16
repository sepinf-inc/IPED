package iped.engine.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link GraphService} backed by an out-of-process Neo4j 5 database reached over Bolt.
 *
 * <p>
 * {@link #start(File)} spawns the {@code GraphServer} child (isolated {@code lib/neo4j/}
 * classpath with antlr 4.13.x), reads back its Bolt port and opens a {@link Driver}. The
 * Cypher engine therefore never loads in this JVM, which keeps antlr 4.9.2 for libfqlite.
 * Driver results are wrapped in {@link BoltNode}/{@link BoltRelationship}/{@link BoltPath}
 * (which implement the {@code org.neo4j.graphdb} interfaces) so the graph UI is unchanged.
 * Java 21 migration.
 * </p>
 */
public class GraphServiceImpl implements GraphService {

    private static Logger LOGGER = LoggerFactory.getLogger(GraphServiceImpl.class);

    // Wire protocol with GraphServer (in module iped-graph-server). Kept as literals to avoid a
    // compile dependency from iped-engine on the isolated server module.
    private static final String GRAPH_SERVER_CLASS = "iped.engine.graph.server.GraphServer";
    private static final String BOLT_PORT_PREFIX = "BOLT_PORT=";
    private static final String READY_TOKEN = "GRAPH_SERVER_READY";
    private static final String STOP_COMMAND = "STOP";

    private Process serverProcess;
    private BufferedWriter serverStdin;
    private Driver driver;
    private boolean started = false;
    private File dbHome;

    @Override
    public synchronized void start(File dbHome) throws IOException {
        if (started) {
            LOGGER.info("Graph service already started.");
            return;
        }
        this.dbHome = dbHome;
        dbHome.mkdirs();
        LOGGER.info("Starting out-of-process neo4j graph server at " + dbHome.getAbsolutePath());

        List<String> cmd = Neo4jChildLauncher.baseCommand();
        cmd.add(GRAPH_SERVER_CLASS);
        cmd.add(dbHome.getAbsolutePath());
        cmd.add(GraphTask.DB_NAME);

        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.redirectErrorStream(true);
        serverProcess = processBuilder.start();
        serverStdin = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream(), StandardCharsets.UTF_8));

        int port = readBoltPort(serverProcess);
        if (port <= 0) {
            stop();
            throw new IOException("Could not start neo4j graph server (no Bolt port reported).");
        }

        driver = GraphDatabase.driver("bolt://127.0.0.1:" + port, AuthTokens.none());
        driver.verifyConnectivity();
        started = true;
        LOGGER.info("Neo4j graph server ready on Bolt port " + port);
    }

    private int readBoltPort(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        int port = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(BOLT_PORT_PREFIX)) {
                try {
                    port = Integer.parseInt(line.substring(BOLT_PORT_PREFIX.length()).trim());
                } catch (NumberFormatException e) {
                    LOGGER.warn("Invalid bolt port line from graph server: {}", line);
                }
            } else if (READY_TOKEN.equals(line)) {
                break;
            } else {
                LOGGER.debug("[graph-server] {}", line);
            }
        }
        // Keep draining the child output so it never blocks on a full pipe.
        Thread drain = new Thread(() -> {
            try {
                String l;
                while ((l = reader.readLine()) != null) {
                    LOGGER.debug("[graph-server] {}", l);
                }
            } catch (IOException e) {
                // child gone
            }
        }, "graph-server-output");
        drain.setDaemon(true);
        drain.start();
        return port;
    }

    @Override
    public synchronized void stop() throws IOException {
        if (!started && serverProcess == null) {
            LOGGER.info("Graph service already stopped.");
            return;
        }
        LOGGER.info("Shutting down neo4j graph server.");
        try {
            if (driver != null) {
                driver.close();
            }
        } finally {
            driver = null;
            if (serverProcess != null) {
                try {
                    if (serverStdin != null) {
                        serverStdin.write(STOP_COMMAND);
                        serverStdin.newLine();
                        serverStdin.flush();
                        serverStdin.close();
                    }
                } catch (IOException e) {
                    // child may already be gone
                }
                try {
                    if (!serverProcess.waitFor(30, TimeUnit.SECONDS)) {
                        serverProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    serverProcess.destroyForcibly();
                    Thread.currentThread().interrupt();
                }
                serverProcess = null;
                serverStdin = null;
            }
            started = false;
        }
    }

    @Override
    public synchronized File getDbHome() {
        return dbHome;
    }

    // ------------------------------------------------------------------
    // Driver result -> graphdb-api snapshot helpers
    // ------------------------------------------------------------------

    private static BoltNode node(Record rec, String column) {
        return BoltNode.from(rec.get(column).asNode());
    }

    /**
     * Same as {@link #node(Record, String)} but also carries the node degree returned by the query in
     * {@code degreeColumn} (the {@code COUNT {{ (n)--() }}} columns), used by the UI to size nodes.
     * Falls back to an unknown degree when the column is absent/null.
     */
    private static BoltNode nodeWithDegree(Record rec, String nodeColumn, String degreeColumn) {
        int degree = rec.containsKey(degreeColumn) && !rec.get(degreeColumn).isNull() ? rec.get(degreeColumn).asInt()
                : -1;
        return BoltNode.from(rec.get(nodeColumn).asNode(), degree);
    }

    /** Builds a relationship carrying its start/end nodes (queries add startNode(r) as sn, endNode(r) as en). */
    private static BoltRelationship relWithEnds(Record rec) {
        return BoltRelationship.from(rec.get("edge").asRelationship(), node(rec, "sn"), node(rec, "en"));
    }

    private static Object convertValue(Value value) {
        switch (value.type().name()) {
            case "NODE":
                return BoltNode.from(value.asNode());
            case "RELATIONSHIP":
                org.neo4j.driver.types.Relationship r = value.asRelationship();
                return new BoltRelationship(r.id(), r.elementId(), r.type(), null, null, r.asMap());
            case "PATH":
                return BoltPath.from(value.asPath());
            default:
                return value.asObject();
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Override
    public List<Long> getMoreConnectedNodes(int maxNodes) {
        List<Long> ids = new ArrayList<>();
        try (Session session = driver.session()) {
            // Cypher 5 removed size(<pattern>); use COUNT { ... } instead.
            Result result = session.run(
                    "MATCH (n) RETURN id(n) as id, COUNT { (n)--() } as degree ORDER BY degree DESC LIMIT " + maxNodes);
            while (result.hasNext()) {
                Record rec = result.next();
                if (rec.get("degree").asLong() > 0) {
                    ids.add(rec.get("id").asLong());
                }
            }
        }
        return ids;
    }

    @Override
    public void getEdges(String[] ids, EdgeQueryListener listener) {
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH ()-[r]-() WHERE r.relId IN $param RETURN DISTINCT r as edge, startNode(r) as sn, endNode(r) as en",
                    Collections.singletonMap("param", Arrays.asList(ids)));
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.edgeFound(relWithEnds(result.next()));
            }
        }
    }

    @Override
    public void getNodes(Collection<Long> ids, NodeQueryListener listener) {
        try (Session session = driver.session()) {
            Result result = session.run("MATCH (n) WHERE id(n) IN $param RETURN n, COUNT { (n)--() } as deg",
                    Collections.singletonMap("param", new ArrayList<>(ids)));
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.nodeFound(nodeWithDegree(result.next(), "n", "deg"));
            }
        }
    }

    @Override
    public void getNeighbours(Long id, NodeEdgeQueryListener listener, int maxNodes) {
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH (n)-[r]-(m) WHERE id(n) = $param RETURN m as node, COUNT { (m)--() } as deg, r as edge, startNode(r) as sn, endNode(r) as en",
                    Collections.singletonMap("param", id));
            emitNeighbours(result, listener, maxNodes);
        }
    }

    @Override
    public void getNeighboursWithLabels(Collection<String> labels, Long nodeId, NodeEdgeQueryListener listener,
            int maxNodes) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("labels", new ArrayList<>(labels));
        params.put("nodeId", nodeId);
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH (n)-[r]-(m) WHERE id(n) = $nodeId AND ANY(l IN labels(m) WHERE l IN $labels) "
                            + "RETURN m as node, COUNT { (m)--() } as deg, r as edge, startNode(r) as sn, endNode(r) as en",
                    params);
            emitNeighbours(result, listener, maxNodes);
        }
    }

    @Override
    public void getNeighboursWithRelationships(Collection<String> relationships, Long nodeId,
            NodeEdgeQueryListener listener, int maxNodes) {
        // Cypher 5 type alternation is A|B (the old A|:B form was removed).
        String typeFilter = relationships.isEmpty() ? "" : ":" + relationships.stream().collect(Collectors.joining("|"));
        String query = "MATCH (n)-[r" + typeFilter + "]-(m) WHERE id(n) = $nodeId "
                + "RETURN m as node, COUNT { (m)--() } as deg, r as edge, startNode(r) as sn, endNode(r) as en";
        try (Session session = driver.session()) {
            Result result = session.run(query, Collections.singletonMap("nodeId", nodeId));
            emitNeighbours(result, listener, maxNodes);
        }
    }

    private void emitNeighbours(Result result, NodeEdgeQueryListener listener, int maxNodes) {
        if (maxNodes == -1) {
            emitAllNeighbours(result, listener);
        } else {
            emitTopNeighbours(result, listener, maxNodes);
        }
    }

    private void emitAllNeighbours(Result result, NodeEdgeQueryListener listener) {
        boolean proceed = true;
        while (result.hasNext() && proceed) {
            Record next = result.next();
            proceed = listener.nodeFound(nodeWithDegree(next, "node", "deg"));
            proceed = proceed && listener.edgeFound(relWithEnds(next));
        }
    }

    private static class NodeRels implements Comparable<NodeRels> {
        Node node;
        List<Relationship> rels = new ArrayList<>();

        @Override
        public int compareTo(NodeRels o) {
            return Integer.compare(o.rels.size(), this.rels.size());
        }
    }

    private void emitTopNeighbours(Result result, NodeEdgeQueryListener listener, int top) {
        Map<Long, NodeRels> edgeMap = new HashMap<>();
        while (result.hasNext()) {
            Record next = result.next();
            Node node = nodeWithDegree(next, "node", "deg");
            NodeRels nodeRels = edgeMap.computeIfAbsent(node.getId(), k -> {
                NodeRels nr = new NodeRels();
                nr.node = node;
                return nr;
            });
            nodeRels.rels.add(relWithEnds(next));
        }
        List<NodeRels> list = new ArrayList<>(edgeMap.values());
        Collections.sort(list);
        int added = 0;
        for (NodeRels nodeRels : list) {
            listener.nodeFound(nodeRels.node);
            for (Relationship edge : nodeRels.rels) {
                listener.edgeFound(edge);
            }
            if (++added == top) {
                break;
            }
        }
    }

    @Override
    public void getConnections(Set<Long> ids, EdgeQueryListener listener) {
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH (n)-[r]-(m) WHERE id(n) IN $param AND id(m) IN $param "
                            + "RETURN r as edge, startNode(r) as sn, endNode(r) as en",
                    Collections.singletonMap("param", new ArrayList<>(ids)));
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.edgeFound(relWithEnds(result.next()));
            }
        }
    }

    @Override
    public void getPaths(Long source, Long target, int maxDistance, PathQueryListener listener) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("source", source);
        params.put("target", target);
        String query = "MATCH p = allShortestPaths((n1)-[*1.." + maxDistance + "]-(n2))"
                + " WHERE id(n1) = $source AND id(n2) = $target RETURN p";
        try (Session session = driver.session()) {
            Result result = session.run(query, params);
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.pathFound(BoltPath.from(result.next().get("p").asPath()));
            }
        }
    }

    @Override
    public void search(String param, NodeQueryListener listener) {
        String query = "MATCH (n) WHERE ANY(prop IN keys(n) WHERE toUpper(toString(n[prop])) CONTAINS $param) "
                + "RETURN n, COUNT { (n)--() } as deg";
        try (Session session = driver.session()) {
            Result result = session.run(query, Collections.singletonMap("param", param.toUpperCase()));
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.nodeFound(nodeWithDegree(result.next(), "n", "deg"));
            }
        }
    }

    @Override
    public void findConnections(Long id, ConnectionQueryListener listener) {
        String query = "MATCH (n)--(m) WHERE id(n) = $param RETURN DISTINCT m as neighbour, labels(m) AS labels";
        try (Session session = driver.session()) {
            Result result = session.run(query, Collections.singletonMap("param", id));
            Map<String, Integer> accum = new HashMap<>();
            while (result.hasNext()) {
                List<Object> labels = result.next().get("labels").asList();
                for (Object label : labels) {
                    accum.merge(label.toString(), 1, Integer::sum);
                }
            }
            for (Entry<String, Integer> entry : accum.entrySet()) {
                listener.connectionsFound(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void findRelationships(Long id, ConnectionQueryListener listener) {
        try (Session session = driver.session()) {
            Result result = session.run("MATCH (n)-[r]-() WHERE id(n) = $param RETURN r as edge",
                    Collections.singletonMap("param", id));
            Map<String, Integer> accum = new HashMap<>();
            while (result.hasNext()) {
                String type = result.next().get("edge").asRelationship().type();
                accum.merge(type, 1, Integer::sum);
            }
            for (Entry<String, Integer> entry : accum.entrySet()) {
                listener.connectionsFound(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public List<Node> search(Label label, Map<String, Object> params) {
        final List<Node> result = new ArrayList<>();
        search(label, params, node -> {
            result.add(node);
            return true;
        });
        return result;
    }

    @Override
    public void search(Label label, Map<String, Object> params, NodeQueryListener listener, String... ordering) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("MATCH (n:").append(label.name()).append(") ");
        if (!params.isEmpty()) {
            queryBuilder.append(" WHERE ");
            Iterator<String> iterator = params.keySet().iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                queryBuilder.append(" n.").append(name);
                if (iterator.hasNext()) {
                    queryBuilder.append(" AND ");
                }
            }
        }
        queryBuilder.append(" RETURN n, COUNT { (n)--() } as deg ");
        if (ordering.length > 0) {
            queryBuilder.append(" ORDER BY ");
            queryBuilder.append(Arrays.stream(ordering).map(o -> "n." + o).collect(Collectors.joining(", ")));
        }
        try (Session session = driver.session()) {
            Result result = session.run(queryBuilder.toString(), params);
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.nodeFound(nodeWithDegree(result.next(), "n", "deg"));
            }
        }
    }

    @Override
    public void findLabels(LabelQueryListener listener) {
        String query = " MATCH (n) WITH DISTINCT labels(n) AS labels UNWIND labels AS label "
                + " RETURN DISTINCT label ORDER BY label ";
        try (Session session = driver.session()) {
            Result result = session.run(query);
            while (result.hasNext()) {
                listener.labelFound(result.next().get("label").asString());
            }
        }
    }

    @Override
    public void findLinks(ExportLinksQuery query, LinkQueryListener listener) {
        StringBuilder queryBuilder = new StringBuilder("MATCH ");
        StringBuilder whereBuilder = new StringBuilder(" WHERE ");
        StringBuilder returnBuilder = new StringBuilder(" RETURN DISTINCT ");

        int numOfLinks = query.getNumOfLinks();
        for (int index = 0; index < numOfLinks; index++) {
            List<String> types = query.getTypes(index);
            String node = "n_" + index;
            queryBuilder.append("(").append(node).append(")");

            Iterator<String> iterator = types.iterator();
            whereBuilder.append("(");
            while (iterator.hasNext()) {
                String type = iterator.next();
                whereBuilder.append(node).append(":").append(type).append(" ");
                if (iterator.hasNext()) {
                    whereBuilder.append("OR ");
                }
            }
            whereBuilder.append(")");

            returnBuilder.append(node);
            if (index + 1 < numOfLinks) {
                int distance = query.getDistance(index);
                queryBuilder.append("-[*").append(distance).append("]-");
                returnBuilder.append(",");
                whereBuilder.append(" AND ");
            }
        }

        queryBuilder.append(whereBuilder).append(returnBuilder);

        try (Session session = driver.session()) {
            Result result = session.run(queryBuilder.toString());
            while (result.hasNext()) {
                Record next = result.next();
                for (int index = 0; index < numOfLinks - 1; index++) {
                    Node node1 = node(next, "n_" + index);
                    Node node2 = node(next, "n_" + (index + 1));
                    listener.linkFound(node1, node2);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error executing query.", e);
        }
    }

    @Override
    public void advancedSearch(String query, FreeQueryListener listener) {
        try (Session session = driver.session()) {
            Result result = session.run(query);
            listener.columnsFound(result.keys());
            while (result.hasNext()) {
                Record rec = result.next();
                Map<String, Object> map = new LinkedHashMap<>();
                for (String key : rec.keys()) {
                    map.put(key, convertValue(rec.get(key)));
                }
                listener.resultFound(map);
            }
        }
    }

    @Override
    public void getRelationships(Collection<Long> ids, EdgeQueryListener listener) {
        try (Session session = driver.session()) {
            Result result = session.run(
                    "MATCH (n)-[r]-(m) WHERE id(r) IN $param RETURN r as edge, startNode(r) as sn, endNode(r) as en",
                    Collections.singletonMap("param", new ArrayList<>(ids)));
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.edgeFound(relWithEnds(result.next()));
            }
        }
    }

    @Override
    public void searchPaths(String cypher, Map<String, Object> params, PathQueryListener listener) {
        try (Session session = driver.session()) {
            Result result = session.run(cypher, params);
            boolean proceed = true;
            while (result.hasNext() && proceed) {
                proceed = listener.pathFound(BoltPath.from(result.next().get("path").asPath()));
            }
        }
    }

    @Override
    public int deleteRelationshipsFromDatasource(String evidenceUUID) {
        String query = "MATCH ()-[r]-() WHERE r." + GraphTask.RELATIONSHIP_SOURCE
                + " = $param WITH DISTINCT r as dr DELETE dr RETURN count(dr) as size";
        try (Session session = driver.session()) {
            return session.executeWrite(tx -> {
                Result result = tx.run(query, Collections.singletonMap("param", evidenceUUID));
                return result.hasNext() ? (int) result.next().get("size").asLong() : 0;
            });
        }
    }
}
