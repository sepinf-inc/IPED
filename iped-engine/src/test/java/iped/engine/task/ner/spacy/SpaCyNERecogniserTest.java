package iped.engine.task.ner.spacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import iped.engine.config.NamedEntityTaskConfig;

public class SpaCyNERecogniserTest {

    @Test
    public void testEmptyOrNullTextReturnsEmptyMap() throws Exception {
        SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
        Map<String, Set<String>> resultNull = recogniser.recognize(null, "pt");
        assertNotNull(resultNull);
        assertTrue(resultNull.isEmpty());

        Map<String, Set<String>> resultEmpty = recogniser.recognize("   ", "pt");
        assertNotNull(resultEmpty);
        assertTrue(resultEmpty.isEmpty());
    }

    @Test
    public void testGracefulHandlingWhenDisabled() throws Exception {
        SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
        assertFalse(recogniser.isAvailable());

        Map<String, Set<String>> result = recogniser.recognize("Teste de texto", "pt");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGracefulHandlingOnMissingPythonOrModule() throws Exception {
        NamedEntityTaskConfig config = new NamedEntityTaskConfig();
        config.setPythonPath("non_existent_python_binary_xyz");
        config.getLangToModelMap().put("default", "en_core_web_sm");
        config.getLangToModelMap().put("pt", "pt_core_news_sm");
        config.setEnabled(true);

        SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
        recogniser.init(config);

        // When python binary does not exist, the task should be gracefully disabled
        assertFalse(recogniser.isAvailable());
        assertFalse(config.isEnabled());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testJsonParsing() throws Exception {
        SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
        Method method = SpaCyNERecogniser.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        String json = "{\"PERSON\": [\"João da Silva\", \"Maria Souza\"], \"LOCATION\": [\"Brasília\"], \"ORGANIZATION\": [\"Polícia Federal\"]}";
        Map<String, Set<String>> map = (Map<String, Set<String>>) method.invoke(recogniser, json);

        assertNotNull(map);
        assertEquals(3, map.size());

        Set<String> persons = map.get("PERSON");
        assertNotNull(persons);
        assertEquals(2, persons.size());
        assertTrue(persons.contains("João da Silva"));
        assertTrue(persons.contains("Maria Souza"));

        Set<String> locations = map.get("LOCATION");
        assertNotNull(locations);
        assertEquals(1, locations.size());
        assertTrue(locations.contains("Brasília"));

        Set<String> orgs = map.get("ORGANIZATION");
        assertNotNull(orgs);
        assertEquals(1, orgs.size());
        assertTrue(orgs.contains("Polícia Federal"));
    }

    @Test
    public void testEndToEndMockPythonProcess() throws Exception {
        String pythonBinary = null;
        for (String candidate : new String[] { "python3", "python" }) {
            try {
                Process p = new ProcessBuilder(candidate, "--version").start();
                if (p.waitFor() == 0) {
                    pythonBinary = candidate;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        org.junit.Assume.assumeNotNull(pythonBinary);

        java.io.File tempScript = java.io.File.createTempFile("mock_spacy", ".py");
        tempScript.deleteOnExit();

        String scriptContent = ""
                + "import sys\n"
                + "print('spacy_loaded')\n"
                + "sys.stdout.flush()\n"
                + "print('model_loaded')\n"
                + "sys.stdout.flush()\n"
                + "for line in sys.stdin:\n"
                + "    line = line.strip()\n"
                + "    if not line:\n"
                + "        continue\n"
                + "    if line == 'ping':\n"
                + "        print('pong')\n"
                + "        sys.stdout.flush()\n"
                + "    elif line == 'terminate_process':\n"
                + "        sys.exit(0)\n"
                + "    elif line.startswith('RECOGNIZE'):\n"
                + "        print('{\"PERSON\": [\"John Doe\"], \"LOCATION\": [\"New York\"]}')\n"
                + "        sys.stdout.flush()\n";

        java.nio.file.Files.write(tempScript.toPath(), scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            NamedEntityTaskConfig config = new NamedEntityTaskConfig();
            config.setPythonPath(pythonBinary);
            config.setNumProcesses(2);
            config.getLangToModelMap().put("default", "mock_model");
            config.setEnabled(true);

            SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
            recogniser.setScriptPath(tempScript.getAbsolutePath());
            recogniser.init(config);

            assertTrue("Recogniser should be available after starting mock python process", recogniser.isAvailable());

            Map<String, Set<String>> entities = recogniser.recognize("Hello from John Doe in New York", "en");
            assertNotNull(entities);
            assertEquals(2, entities.size());
            assertTrue(entities.containsKey("PERSON"));
            assertTrue(entities.get("PERSON").contains("John Doe"));
            assertTrue(entities.containsKey("LOCATION"));
            assertTrue(entities.get("LOCATION").contains("New York"));

            recogniser.finish();
            assertFalse(recogniser.isAvailable());
        } finally {
            tempScript.delete();
        }
    }
}

