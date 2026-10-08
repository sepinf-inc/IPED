package iped.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

import iped.utils.UTF8Properties;

public class NamedEntityTaskConfigTest {

    @Test
    public void testConfigLoadingFromShippedFile() throws IOException {
        NamedEntityTaskConfig config = new NamedEntityTaskConfig();
        config.processTaskConfig(Paths.get("../iped-app/resources/config/conf/NamedEntityRecognitionConfig.txt"));

        assertEquals("iped.engine.task.ner.spacy.SpaCyNERecogniser", config.getNerImpl());
        assertEquals("en_core_web_sm", config.getLangToModelMap().get("default"));
        assertEquals("pt_core_news_sm", config.getLangToModelMap().get("pt"));
        assertEquals(0.2f, config.getMinLangScore(), 0.001f);
        assertTrue(config.getMimeTypesToIgnore().contains("audio"));
        assertTrue(config.getMimeTypesToIgnore().contains("video"));
        assertTrue(config.getCategoriesToIgnore().contains("Programs and Libraries"));
    }

    @Test
    public void testCustomProperties() {
        NamedEntityTaskConfig config = new NamedEntityTaskConfig();
        UTF8Properties props = new UTF8Properties();
        props.setProperty("NERImpl", "iped.engine.task.ner.spacy.SpaCyNERecogniser");
        props.setProperty("langModel_0", "default:en_core_web_sm");
        props.setProperty("langModel_1", "pt:pt_core_news_sm");
        props.setProperty("langModel_2", "es:es_core_news_sm");
        props.setProperty("numProcesses", "4");
        props.setProperty("pythonPath", "/usr/bin/python3");
        props.setProperty("minLangScore", "0.35");
        props.setProperty("mimeTypesToIgnore", "image/png; image/jpeg");
        props.setProperty("categoriesToIgnore", "Other files");

        config.processProperties(props);

        assertEquals("iped.engine.task.ner.spacy.SpaCyNERecogniser", config.getNerImpl());
        assertEquals("en_core_web_sm", config.getLangToModelMap().get("default"));
        assertEquals("pt_core_news_sm", config.getLangToModelMap().get("pt"));
        assertEquals("es_core_news_sm", config.getLangToModelMap().get("es"));
        assertEquals(4, config.getNumProcesses());
        assertEquals("/usr/bin/python3", config.getPythonPath());
        assertEquals(0.35f, config.getMinLangScore(), 0.001f);
        assertTrue(config.getMimeTypesToIgnore().contains("image/png"));
        assertTrue(config.getMimeTypesToIgnore().contains("image/jpeg"));
        assertTrue(config.getCategoriesToIgnore().contains("Other files"));
    }

    @Test
    public void testEnableProperties() throws IOException {
        NamedEntityTaskConfig config = new NamedEntityTaskConfig();
        assertFalse(config.isEnabled());

        // Test with legacy spelling (enableNamedEntityRecogniton)
        File legacyFile = File.createTempFile("IPEDConfig", ".txt");
        legacyFile.deleteOnExit();
        Files.writeString(legacyFile.toPath(), "enableNamedEntityRecogniton = true\n");

        config.processConfig(legacyFile.toPath());
        assertTrue(config.isEnabled());

        // Test with correct spelling (enableNamedEntityRecognition)
        File correctFile = File.createTempFile("IPEDConfig", ".txt");
        correctFile.deleteOnExit();
        Files.writeString(correctFile.toPath(), "enableNamedEntityRecognition = true\n");

        NamedEntityTaskConfig config2 = new NamedEntityTaskConfig();
        config2.processConfig(correctFile.toPath());
        assertTrue(config2.isEnabled());
    }

    @Test
    public void testWindowsModelPathWithDriveLetter() {
        NamedEntityTaskConfig config = new NamedEntityTaskConfig();
        UTF8Properties props = new UTF8Properties();
        props.setProperty("langModel_0", "default : C:\\models\\en_core_web_sm");
        props.setProperty("langModel_1", "pt : D:\\shared\\models\\pt_core_news_sm");

        config.processProperties(props);

        assertEquals("C:\\models\\en_core_web_sm", config.getLangToModelMap().get("default"));
        assertEquals("D:\\shared\\models\\pt_core_news_sm", config.getLangToModelMap().get("pt"));
    }
}
