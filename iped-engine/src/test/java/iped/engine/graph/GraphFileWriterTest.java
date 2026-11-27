package iped.engine.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.neo4j.graphdb.Label;

public class GraphFileWriterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testPadColumns() {
        assertEquals("\"a\",\"b\"", GraphFileWriter.padColumns("\"a\",\"b\"", 2));
        assertEquals("\"a\",\"\",\"\",\"\"", GraphFileWriter.padColumns("\"a\",\"\"", 4));
        assertEquals("\"a\",\"b\",\"c\"", GraphFileWriter.padColumns("\"a\",\"b\",\"c\"", 2));
        assertEquals("\"a\",\"\"", GraphFileWriter.padColumns("\"a\"", 2));
        assertEquals("", GraphFileWriter.padColumns("", 2));
    }

    /**
     * A node written before a new field shows up must have the same number of
     * columns of the header, the neo4j importer fails otherwise.
     */
    @Test
    public void testNodesWrittenBeforeNewFieldArePadded() throws Exception {
        File root = tempFolder.newFolder("csv");
        Label person = DynLabel.label("PERSON");

        try (GraphFileWriter writer = new GraphFileWriter(root, null)) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("userName", "first");
            props.put("userNotes", "");
            writer.writeCreateNode("userName", "first", props, person);

            props = new LinkedHashMap<>();
            props.put("userName", "second");
            props.put("userNotes", "");
            props.put("userAccount", "account");
            writer.writeCreateNode("userName", "second", props, person);
        }

        String header = Files.readString(new File(root, "nodes_PERSON_headers_iped.csv").toPath(), StandardCharsets.UTF_8);
        assertEquals("nodeId:ID,label:LABEL,userName:string,userNotes:string,userAccount:string", header);

        List<String> lines = Files.readAllLines(new File(root, "nodes_PERSON_iped.csv").toPath(), StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        boolean firstFound = false;
        for (String line : lines) {
            assertEquals(line, 5, line.split("\",\"", -1).length);
            if (line.contains("\"first\"")) {
                firstFound = true;
                assertTrue(line, line.endsWith(",\"first\",\"\",\"\""));
            }
        }
        assertTrue(firstFound);
    }

}
