package iped.engine.graph.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;

/**
 * Standalone launcher for an out-of-process Neo4j 5 database exposed over Bolt.
 *
 * <p>
 * The main IPED process keeps antlr4-runtime 4.9.2 (required by libfqlite). The
 * Neo4j 5 Cypher engine needs antlr 4.13.x, whose ATN serialization is incompatible
 * with 4.9.x. To avoid that hard classpath clash, the embedded engine runs here, in a
 * dedicated JVM whose classpath ({@code lib/neo4j/}) carries antlr 4.13.x and no
 * libfqlite. The IPED UI talks to this process through the Neo4j Bolt driver
 * (see {@code iped.engine.graph.GraphServiceImpl}).
 * </p>
 *
 * <p>
 * Wire protocol over the child's stdio:
 * </p>
 * <ul>
 * <li>once the database is available, prints {@code BOLT_PORT=<port>} then
 * {@code GRAPH_SERVER_READY} to stdout;</li>
 * <li>shuts down cleanly when it reads {@code STOP} on stdin, or when stdin reaches
 * EOF (parent process gone).</li>
 * </ul>
 *
 * <p>
 * Arguments: {@code <dbHome> <dbName> [port]}. When {@code port} is omitted or 0, a
 * free ephemeral port is chosen and reported back via {@code BOLT_PORT=}.
 * </p>
 */
public class GraphServer {

    public static final String PORT_PREFIX = "BOLT_PORT=";
    public static final String READY_TOKEN = "GRAPH_SERVER_READY";
    public static final String STOP_COMMAND = "STOP";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: GraphServer <dbHome> <dbName> [port]");
            System.exit(2);
        }

        File dbHome = new File(args[0]);
        String dbName = args[1];
        int port = (args.length > 2) ? Integer.parseInt(args[2]) : 0;
        if (port == 0) {
            port = findFreePort();
        }

        // The store was created by "neo4j-admin database import full <dbName>" under
        // dbHome/data/databases/<dbName>. Neo4j Community starts a single user database:
        // make <dbName> the default so the imported store is the one that comes up.
        DatabaseManagementService dms = new DatabaseManagementServiceBuilder(dbHome.toPath())
                .setConfig(GraphDatabaseSettings.initial_default_database, dbName)
                .setConfig(GraphDatabaseSettings.auth_enabled, false)
                .setConfig(BoltConnector.enabled, true)
                .setConfig(BoltConnector.listen_address, new SocketAddress("127.0.0.1", port))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(dms::shutdown));

        GraphDatabaseService db = dms.database(dbName);
        db.isAvailable(30000);

        System.out.println(PORT_PREFIX + port);
        System.out.println(READY_TOKEN);
        System.out.flush();

        waitForStop();

        dms.shutdown();
    }

    private static void waitForStop() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            if (STOP_COMMAND.equals(line.trim())) {
                break;
            }
        }
        // EOF (parent gone) also falls through here and triggers shutdown.
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
