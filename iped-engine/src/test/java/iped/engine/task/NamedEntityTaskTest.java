package iped.engine.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.tika.mime.MediaType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import iped.engine.config.NamedEntityTaskConfig;
import iped.engine.data.Item;
import iped.engine.task.ner.INamedEntityRecognizer;

public class NamedEntityTaskTest {

    private NamedEntityTask task;
    private NamedEntityTaskConfig config;

    @Before
    public void setUp() {
        task = new NamedEntityTask();
        config = new NamedEntityTaskConfig();
        config.setEnabled(true);
    }

    @After
    public void tearDown() throws Exception {
        if (task != null) {
            task.finish();
        }
    }

    @Test
    public void testTaskDisabled() throws Exception {
        config.setEnabled(false);
        setField(task, "nerConfig", config);

        assertFalse(task.isEnabled());
    }

    @Test
    public void testProcessWithMockRecognizer() throws Exception {
        setField(task, "nerConfig", config);

        // Inject mock recognizer
        INamedEntityRecognizer mockRecognizer = new INamedEntityRecognizer() {
            @Override
            public void init(NamedEntityTaskConfig config) {
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Map<String, Set<String>> recognize(String text, String lang) {
                Map<String, Set<String>> map = new HashMap<>();
                if (text.contains("João")) {
                    map.put("PERSON", Set.of("João da Silva"));
                    map.put("LOCATION", Set.of("Brasília"));
                    map.put("ORGANIZATION", Set.of("Polícia Federal"));
                }
                return map;
            }

            @Override
            public void finish() {
            }
        };

        setField(null, "recognizer", mockRecognizer);

        TestItem item = new TestItem("O perito João da Silva chegou em Brasília para trabalhar na Polícia Federal.");
        item.setMediaType(MediaType.TEXT_PLAIN);
        item.setCategory("Documentos");

        task.process(item);

        String[] persons = item.getMetadata().getValues("NER_PERSON");
        assertNotNull(persons);
        assertEquals(1, persons.length);
        assertEquals("João da Silva", persons[0]);

        String[] locations = item.getMetadata().getValues("NER_LOCATION");
        assertNotNull(locations);
        assertEquals(1, locations.length);
        assertEquals("Brasília", locations[0]);

        String[] orgs = item.getMetadata().getValues("NER_ORGANIZATION");
        assertNotNull(orgs);
        assertEquals(1, orgs.length);
        assertEquals("Polícia Federal", orgs[0]);
    }

    private static class TestItem extends Item {
        private String text;

        public TestItem(String text) {
            this.text = text;
            setParsedTextCache(text);
        }

        @Override
        public Reader getTextReader() {
            return new StringReader(text);
        }

        @Override
        public boolean isToAddToCase() {
            return true;
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = NamedEntityTask.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
