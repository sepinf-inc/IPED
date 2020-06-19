package br.gov.pf.labld.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.neo4j.tooling.ImportTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  
  public static interface ImportListener{
      
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
          if(listener != null) listener.output(line);
          LOGGER.info(line);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

  }

  public void run(File databaseDir, String dbName, boolean highIO) throws IOException {

    List<String> args = new ArrayList<>();

    args.add(getJreExecutable().getAbsolutePath());
    args.add("-cp");
    args.add(ImportTool.class.getProtectionDomain().getCodeSource().getLocation().getPath());

    args.add(ImportTool.class.getName());

    File argsFile = writeArgsFile(databaseDir, dbName, highIO);
    args.add("--f");
    args.add(argsFile.getAbsolutePath());

    ExecutorService executorService = null;

    LOGGER.info("Running " + args.stream().collect(Collectors.joining(" ")));

    ProcessBuilder processBuilder = new ProcessBuilder(args);
    processBuilder.redirectErrorStream(true);
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

  public File writeArgsFile(File databaseDir, String dbName, boolean highIO) throws IOException {
    File file = File.createTempFile(ARGS_FILE_NAME, ".txt");
    try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(file), Charset.forName("utf-8")))) {

      writer.write("--into ");
      writer.write("\"" + databaseDir.getAbsolutePath() + "\"");
      writer.write("\r\n");
      writer.write("--input-encoding utf-8\r\n");
      writer.write("--bad-tolerance 0\r\n");
      writer.write("--database ");
      writer.write(dbName);
      writer.write("\r\n");
      writer.write("--high-io ");
      writer.write(Boolean.toString(highIO));
      writer.write("\r\n");
      writer.write("--ignore-empty-strings true\r\n");
      writer.write("--skip-duplicate-nodes true\r\n");

      for(File input : inputs) {
          File[] argsFiles = input.listFiles(new ArgsFileFilter());
          for (File argFile : argsFiles) {
            writer.write(IOUtils.toString(argFile.toURI(), Charset.forName("utf-8")));
          }
      }
    }
    return file;
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
