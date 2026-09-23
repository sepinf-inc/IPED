package iped.engine.graph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import iped.io.URLUtil;

/**
 * Builds the base command line for the out-of-process Neo4j child JVMs (import,
 * post-import and the Bolt {@code GraphServer}).
 *
 * <p>
 * Those children run with the isolated {@code lib/neo4j/} classpath, which carries
 * the embedded Neo4j 5 engine and antlr4-runtime 4.13.x. The main IPED process keeps
 * antlr 4.9.2 (required by libfqlite) and never loads the embedded engine, avoiding
 * the incompatible-ATN clash between the two antlr versions. See {@code GraphServer}
 * and {@code GraphServiceImpl}. Part of the Java 21 migration.
 * </p>
 */
public class Neo4jChildLauncher {

    /** Sub-directory of the runtime {@code lib/} folder holding the isolated Neo4j stack. */
    public static final String NEO4J_LIB_DIR = "neo4j";

    // The embedded Neo4j 5 engine needs reflective access to a few JDK internals on Java 17+.
    private static final String[] ADD_OPENS = {
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens=java.base/java.nio=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" };

    private Neo4jChildLauncher() {
    }

    /**
     * Locates {@code lib/neo4j/} relative to the jar this class is loaded from (the
     * iped-engine jar lives in the runtime {@code lib/} folder).
     */
    public static File getNeo4jLibDir() throws IOException {
        try {
            URL url = URLUtil.getURL(Neo4jChildLauncher.class);
            File jarDir = new File(url.toURI()).getParentFile();
            return new File(jarDir, NEO4J_LIB_DIR);
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    /**
     * Base command: {@code <java> -XX:+IgnoreUnrecognizedVMOptions <add-opens...> -cp lib/neo4j/*}.
     * Callers append the main class and its arguments.
     */
    public static List<String> baseCommand() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(getJreExecutable().getAbsolutePath());
        cmd.add("-XX:+IgnoreUnrecognizedVMOptions");
        for (String addOpens : ADD_OPENS) {
            cmd.add(addOpens);
        }
        cmd.add("-cp");
        cmd.add(getNeo4jLibDir().getAbsolutePath() + File.separator + "*");
        return cmd;
    }

    public static File getJreExecutable() throws FileNotFoundException {
        String jreDirectory = System.getProperty("java.home");
        if (jreDirectory == null) {
            throw new IllegalStateException("java.home");
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        File exe = new File(jreDirectory, os.startsWith("windows") ? "bin/java.exe" : "bin/java");
        if (!exe.isFile()) {
            throw new FileNotFoundException(exe.toString());
        }
        return exe;
    }
}
