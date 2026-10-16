package iped.engine.graph;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.graph.GraphImportRunner.ImportListener;

/**
 * Orchestrates graph database generation from the CSV files produced by {@link GraphFileWriter}.
 *
 * <p>
 * Both the bulk import and the post-import operations (config post-generation Cypher statements +
 * contact grouping) run in child JVMs with the isolated {@code lib/neo4j/} stack (embedded engine
 * + antlr 4.13.x). They must not run in this process, which keeps antlr 4.9.2 for libfqlite.
 * See {@link GraphImportRunner} and {@code iped.engine.graph.server.GraphPostImport}.
 * </p>
 */
public class GraphGenerator {

    private static Logger LOGGER = LoggerFactory.getLogger(GraphGenerator.class);

    private static final String POST_IMPORT_CLASS = "iped.engine.graph.server.GraphPostImport";

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

    private boolean importDB(ImportListener listener, File output, File... input) {
        try {
            LocalConfig localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
            GraphImportRunner runner = new GraphImportRunner(listener, input);
            runner.run(output, GraphTask.DB_NAME, localConfig.isOutputOnSSD());
            return true;
        } catch (Exception e) {
            LOGGER.error("Error generating database.", e);
            return false;
        }
    }

    private void runPostImportOps(File neo4jHome) throws IOException {
        GraphConfiguration config = (GraphConfiguration) ConfigurationManager.get().findObject(GraphTaskConfig.class)
                .getConfiguration();

        // Post-generation statements are passed to the child via a temp file (one statement per line).
        File statementsFile = File.createTempFile("iped-graph-postgen", ".txt");
        try {
            List<String> statements = config.getPostGenerationStatements();
            Files.write(statementsFile.toPath(), statements == null ? Collections.emptyList() : statements,
                    StandardCharsets.UTF_8);

            List<String> cmd = Neo4jChildLauncher.baseCommand();
            cmd.add(POST_IMPORT_CLASS);
            cmd.add(neo4jHome.getAbsolutePath());
            cmd.add(GraphTask.DB_NAME);
            cmd.add(config.getDefaultRelationship());
            cmd.add(statementsFile.getAbsolutePath());

            LOGGER.info("Running graph post-import operations.");
            runChild(cmd);
        } finally {
            statementsFile.delete();
        }
    }

    private void runChild(List<String> cmd) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(cmd);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info(line);
                }
            } catch (IOException e) {
                // child gone
            }
        });
        try {
            int result = process.waitFor();
            if (result != 0) {
                throw new IOException("Graph post-import operations failed with exit code " + result + ".");
            }
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }
    }
}
