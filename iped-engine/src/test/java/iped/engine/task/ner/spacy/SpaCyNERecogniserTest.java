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
    @SuppressWarnings("unchecked")
    public void testJsonParsingWithUnicodeEscapes() throws Exception {
        SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
        Method method = SpaCyNERecogniser.class.getDeclaredMethod("parseJsonResponse", String.class);
        method.setAccessible(true);

        String json = "{\"PERSON\": [\"Jo\\u00e3o da Silva\"], \"LOCATION\": [\"Bras\\u00edlia\"]}";
        Map<String, Set<String>> map = (Map<String, Set<String>>) method.invoke(recogniser, json);

        assertNotNull(map);
        assertEquals(2, map.size());

        Set<String> persons = map.get("PERSON");
        assertNotNull(persons);
        assertTrue(persons.contains("João da Silva"));

        Set<String> locations = map.get("LOCATION");
        assertNotNull(locations);
        assertTrue(locations.contains("Brasília"));
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

    @Test
    public void testProcessCrashRecovery() throws Exception {
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

        java.io.File tempScript = java.io.File.createTempFile("mock_crash_spacy", ".py");
        tempScript.deleteOnExit();

        // Script that crashes (exits) when input contains "crash_now", otherwise responds normally
        String scriptContent = ""
                + "import sys, base64\n"
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
                + "        parts = line.split(' ', 2)\n"
                + "        text = base64.b64decode(parts[2]).decode('utf-8', errors='ignore')\n"
                + "        if 'crash_now' in text:\n"
                + "            sys.exit(1)\n"
                + "        print('{\"PERSON\": [\"Recovered Person\"]}')\n"
                + "        sys.stdout.flush()\n";

        java.nio.file.Files.write(tempScript.toPath(), scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            NamedEntityTaskConfig config = new NamedEntityTaskConfig();
            config.setPythonPath(pythonBinary);
            config.setNumProcesses(1);
            config.getLangToModelMap().put("default", "mock_model");
            config.setEnabled(true);

            SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
            recogniser.setScriptPath(tempScript.getAbsolutePath());
            recogniser.init(config);

            assertTrue(recogniser.isAvailable());

            // 1. Send crashing input: process should exit, get detected, and trigger replacement
            Map<String, Set<String>> crashedResult = recogniser.recognize("crash_now please", "en");
            assertNotNull(crashedResult);
            assertTrue(crashedResult.isEmpty());

            // 2. Subsequent call should succeed through the replacement process
            Map<String, Set<String>> recoveredResult = recogniser.recognize("normal text", "en");
            assertNotNull(recoveredResult);
            assertEquals(1, recoveredResult.size());
            assertTrue(recoveredResult.containsKey("PERSON"));
            assertTrue(recoveredResult.get("PERSON").contains("Recovered Person"));

            recogniser.finish();
            assertFalse(recogniser.isAvailable());
        } finally {
            tempScript.delete();
        }
    }

    @Test
    public void testReinitializationAfterFinish() throws Exception {
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

        java.io.File tempScript = java.io.File.createTempFile("mock_reinit_spacy", ".py");
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
                + "        print('{\"PERSON\": [\"Reinit Person\"]}')\n"
                + "        sys.stdout.flush()\n";

        java.nio.file.Files.write(tempScript.toPath(), scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            NamedEntityTaskConfig config = new NamedEntityTaskConfig();
            config.setPythonPath(pythonBinary);
            config.setNumProcesses(1);
            config.getLangToModelMap().put("default", "mock_model");
            config.setEnabled(true);

            SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
            recogniser.setScriptPath(tempScript.getAbsolutePath());

            // First initialization
            recogniser.init(config);
            assertTrue(recogniser.isAvailable());

            // First finish
            recogniser.finish();
            assertFalse(recogniser.isAvailable());

            // Second initialization (must succeed because isInitialized was reset in finish)
            recogniser.init(config);
            assertTrue(recogniser.isAvailable());

            Map<String, Set<String>> result = recogniser.recognize("test text", "en");
            assertNotNull(result);
            assertTrue(result.containsKey("PERSON"));
            assertTrue(result.get("PERSON").contains("Reinit Person"));

            recogniser.finish();
            assertFalse(recogniser.isAvailable());
        } finally {
            tempScript.delete();
        }
    }

    @Test
    public void testFinishDuringConcurrentRecognitionDestroysCheckedOutServer() throws Exception {
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

        java.io.File tempScript = java.io.File.createTempFile("mock_concurrent_finish_spacy", ".py");
        tempScript.deleteOnExit();

        String scriptContent = ""
                + "import sys, time\n"
                + "print('spacy_loaded')\n"
                + "sys.stdout.flush()\n"
                + "print('model_loaded')\n"
                + "sys.stdout.flush()\n"
                + "for line in sys.stdin:\n"
                + "    line = line.strip()\n"
                + "    if not line:\n"
                + "        continue\n"
                + "    if line == 'terminate_process':\n"
                + "        sys.exit(0)\n"
                + "    elif line.startswith('RECOGNIZE'):\n"
                + "        time.sleep(0.5)\n"
                + "        print('{\"PERSON\": [\"Slow Person\"]}')\n"
                + "        sys.stdout.flush()\n";

        java.nio.file.Files.write(tempScript.toPath(), scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            NamedEntityTaskConfig config = new NamedEntityTaskConfig();
            config.setPythonPath(pythonBinary);
            config.setNumProcesses(1);
            config.getLangToModelMap().put("default", "mock_model");
            config.setEnabled(true);

            SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
            recogniser.setScriptPath(tempScript.getAbsolutePath());
            recogniser.init(config);
            assertTrue(recogniser.isAvailable());

            // Run recognize in background thread
            java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean threadDone = new java.util.concurrent.atomic.AtomicBoolean(false);

            Thread workerThread = new Thread(() -> {
                started.countDown();
                try {
                    recogniser.recognize("slow text", "en");
                } catch (Exception ignored) {
                }
                threadDone.set(true);
            });
            workerThread.start();

            started.await();
            // Wait slightly so worker thread checks out the server from serverQueue
            Thread.sleep(150);

            // Call finish while worker thread is mid-recognition
            recogniser.finish();
            assertFalse(recogniser.isAvailable());

            workerThread.join(5000);
            assertTrue("Worker thread should have completed", threadDone.get());

            // Check that serverQueue did NOT retain the server
            java.lang.reflect.Field queueField = SpaCyNERecogniser.class.getDeclaredField("serverQueue");
            queueField.setAccessible(true);
            java.util.concurrent.BlockingQueue<?> queue = (java.util.concurrent.BlockingQueue<?>) queueField.get(recogniser);
            assertTrue("Server queue should be empty and not leak checked-out server", queue.isEmpty());

        } finally {
            tempScript.delete();
        }
    }

    @Test
    public void testBlankLinesDoNotCrashProcess() throws Exception {
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

        java.io.File tempScript = java.io.File.createTempFile("mock_blank_lines_spacy", ".py");
        tempScript.deleteOnExit();

        String scriptContent = ""
                + "import sys\n"
                + "print('spacy_loaded')\n"
                + "sys.stdout.flush()\n"
                + "print('model_loaded')\n"
                + "sys.stdout.flush()\n"
                + "while True:\n"
                + "    try:\n"
                + "        line = input()\n"
                + "    except EOFError:\n"
                + "        break\n"
                + "    line = line.strip()\n"
                + "    if not line:\n"
                + "        continue\n"
                + "    if line == 'terminate_process':\n"
                + "        break\n"
                + "    elif line.startswith('RECOGNIZE'):\n"
                + "        print('{\"PERSON\": [\"Active Person\"]}')\n"
                + "        sys.stdout.flush()\n";

        java.nio.file.Files.write(tempScript.toPath(), scriptContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            NamedEntityTaskConfig config = new NamedEntityTaskConfig();
            config.setPythonPath(pythonBinary);
            config.setNumProcesses(1);
            config.getLangToModelMap().put("default", "mock_model");
            config.setEnabled(true);

            SpaCyNERecogniser recogniser = new SpaCyNERecogniser();
            recogniser.setScriptPath(tempScript.getAbsolutePath());
            recogniser.init(config);
            assertTrue(recogniser.isAvailable());

            // Send blank lines directly through server writer to test resilience
            java.lang.reflect.Field queueField = SpaCyNERecogniser.class.getDeclaredField("serverQueue");
            queueField.setAccessible(true);
            java.util.concurrent.BlockingQueue<?> queue = (java.util.concurrent.BlockingQueue<?>) queueField.get(recogniser);
            Object server = queue.peek();
            assertNotNull(server);
            java.lang.reflect.Field writerField = server.getClass().getDeclaredField("writer");
            writerField.setAccessible(true);
            java.io.BufferedWriter writer = (java.io.BufferedWriter) writerField.get(server);
            writer.write("\n\n   \n");
            writer.flush();

            // Next recognition must succeed because blank lines were ignored
            Map<String, Set<String>> result = recogniser.recognize("sample query", "en");
            assertNotNull(result);
            assertTrue(result.containsKey("PERSON"));
            assertTrue(result.get("PERSON").contains("Active Person"));

            recogniser.finish();
            assertFalse(recogniser.isAvailable());

        } finally {
            tempScript.delete();
        }
    }
}





