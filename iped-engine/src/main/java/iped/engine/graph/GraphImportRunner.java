package iped.engine.graph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the Neo4j bulk CSV import in a child JVM with the isolated {@code lib/neo4j/} classpath
 * (embedded engine + antlr 4.13.x), via {@link Neo4jChildLauncher}. The import does not use the
 * Cypher parser, but it shares the same isolated stack as the Bolt {@code GraphServer}.
 */
public class GraphImportRunner {

    private static Logger LOGGER = LoggerFactory.getLogger(GraphImportRunner.class);

    public static final String ARGS_FILE_NAME = "import-tool-args";

    // neo4j-admin entry point, referenced by name so iped-engine needs no compile dependency on it.
    private static final String ADMIN_TOOL_CLASS = "org.neo4j.cli.AdminTool";

    private File[] inputs;
    private ImportListener listener;

    public GraphImportRunner(ImportListener listener, File... inputFolders) {
        super();
        this.inputs = inputFolders;
        this.listener = listener;
    }

    public static interface ImportListener {

        public void output(String line);
    }

    private class InputReader implements Runnable {

        private InputStream in;
        private ImportListener listener;

        public InputReader(InputStream in, ImportListener listener) {
            super();
            this.in = in;
            this.listener = listener;
        }

        @Override
        public void run() {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()));
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    if (listener != null)
                        listener.output(line);
                    LOGGER.info(line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public void run(File neo4jHome, String dbName, boolean highIO) throws IOException {

        List<String> args = Neo4jChildLauncher.baseCommand();
        args.add(ADMIN_TOOL_CLASS);
        // Neo4j 5 moved the former top-level "import" command under "database import full".
        args.add("database");
        args.add("import");
        args.add("full");

        writeArgs(args, dbName, highIO);

        ExecutorService executorService = null;

        LOGGER.info("Running " + args.stream().collect(Collectors.joining(" ")));

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        neo4jHome.mkdirs();

        File emptyConf = new File(System.getProperty("java.io.tmpdir"), "neo4j.conf");
        if (!emptyConf.exists()) {
            emptyConf.createNewFile();
        }
        emptyConf.deleteOnExit();

        processBuilder.environment().put("NEO4J_HOME", neo4jHome.getAbsolutePath());
        processBuilder.environment().put("NEO4J_CONF", emptyConf.getParent());
        processBuilder.directory(new File(neo4jHome, GraphTask.CSVS_DIR));
        Process process = processBuilder.start();
        try {
            executorService = Executors.newFixedThreadPool(1);
            executorService.submit(new InputReader(process.getInputStream(), listener));
            int result = process.waitFor();
            if (result != 0) {
                throw new RuntimeException("Could not import graph database.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            process.destroy();
            if (executorService != null) {
                executorService.shutdown();
            }
        }
    }

    public void writeArgs(List<String> args, String dbName, boolean highIO) throws IOException {

        // Neo4j 5 CLI options use the --opt=value form: boolean options no longer accept a
        // space-separated value, --high-io was renamed to --high-parallel-io=on|off|auto, and the
        // legacy --database flag was replaced by a trailing <database> positional argument.
        args.add("--input-encoding=utf-8");
        args.add("--bad-tolerance=0");
        if (highIO) {
            args.add("--high-parallel-io=on");
        }
        args.add("--ignore-empty-strings=true");
        args.add("--skip-duplicate-nodes=true");

        for (File input : inputs) {
            File[] argsFiles = input.listFiles(new ArgsFileFilter());
            for (File argFile : argsFiles) {
                for (String line : Files.readAllLines(argFile.toPath())) {
                    args.add(line);
                }
            }
        }

        // End-of-options marker so the variadic --nodes/--relationships do not swallow the
        // database name as an extra file; <database> is the trailing positional in Neo4j 5.
        args.add("--");
        args.add(dbName);
    }

    private static class ArgsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return pathname.getName().startsWith(ARGS_FILE_NAME) && !pathname.getName().equals(ARGS_FILE_NAME + ".txt");
        }

    }

}
