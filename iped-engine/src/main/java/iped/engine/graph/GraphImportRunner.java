package iped.engine.graph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.neo4j.cli.AdminTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.io.URLUtil;

public class GraphImportRunner {

    private static Logger LOGGER = LoggerFactory.getLogger(GraphImportRunner.class);

    public static final String ARGS_FILE_NAME = "import-tool-args";

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

        List<String> args = new ArrayList<>();

        args.add(getJreExecutable().getAbsolutePath());
        args.add("-cp");
        try {
            URL url = URLUtil.getURL(AdminTool.class);
            args.add(new File(url.toURI()).getParentFile().getAbsolutePath() + "/*");
        } catch (URISyntaxException e1) {
            throw new IOException(e1);
        }

        args.add(AdminTool.class.getName());
        args.add("import");

        writeArgs(args, dbName, highIO);

        ExecutorService executorService = null;

        LOGGER.info("Running " + args.stream().collect(Collectors.joining(" ")));

        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.redirectErrorStream(true);
        neo4jHome.mkdirs();

        File emptyConf = new File(System.getProperty("java.io.tmpdir"), "neo4j.conf");
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

        args.add("--input-encoding");
        args.add("utf-8");
        args.add("--bad-tolerance");
        args.add("0");
        args.add("--database");
        args.add(dbName);
        if (highIO) {
            args.add("--high-io");
            args.add("true");
        }
        
        args.add("--ignore-empty-strings");
        args.add("true");
        args.add("--skip-duplicate-nodes");
        args.add("true");

        for (File input : inputs) {
            File[] argsFiles = input.listFiles(new ArgsFileFilter());
            for (File argFile : argsFiles) {
                for (String line : Files.readAllLines(argFile.toPath())) {
                    args.add(line);
                }
            }
        }
    }

    private static class ArgsFileFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return pathname.getName().startsWith(ARGS_FILE_NAME) && !pathname.getName().equals(ARGS_FILE_NAME + ".txt");
        }

    }

    private boolean isWindows() {
        String os = System.getProperty("os.name");
        if (os == null) {
            throw new IllegalStateException("os.name");
        }
        os = os.toLowerCase();
        return os.startsWith("windows");
    }

    private File getJreExecutable() throws FileNotFoundException {
        String jreDirectory = System.getProperty("java.home");
        if (jreDirectory == null) {
            throw new IllegalStateException("java.home");
        }
        File exe;
        if (isWindows()) {
            exe = new File(jreDirectory, "bin/java.exe");
        } else {
            exe = new File(jreDirectory, "bin/java");
        }
        if (!exe.isFile()) {
            throw new FileNotFoundException(exe.toString());
        }
        return exe;
    }

}
