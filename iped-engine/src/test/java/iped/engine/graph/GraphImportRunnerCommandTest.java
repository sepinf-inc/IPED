package iped.engine.graph;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

import iped.engine.graph.GraphImportRunner;

public class GraphImportRunnerCommandTest {

    @Test
    public void classpathWildcardIsSingleArgument() throws IOException {
        File neo4jHome = Files.createTempDirectory("neo4j-home").toFile();
        GraphImportRunner runner = new GraphImportRunner(null);
        List<String> command = runner.buildCommand(neo4jHome, "graph.db", false);

        boolean hasWildcardClasspath = false;
        for (String arg : command) {
            if (arg.endsWith("/*")) {
                hasWildcardClasspath = true;
                break;
            }
        }
        assertTrue("classpath wildcard must be a single argument", hasWildcardClasspath);
        assertTrue(command.contains(org.neo4j.cli.AdminTool.class.getName()));
        assertTrue(command.contains("import"));
    }
}