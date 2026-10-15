package iped.engine.task.ner.tika;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ner.NamedEntityParser;
import org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.NamedEntityTaskConfig;
import iped.engine.task.NamedEntityTask;
import iped.engine.task.ner.INamedEntityRecognizer;
import iped.parsers.util.IgnoreContentHandler;
import iped.utils.EmptyInputStream;

/**
 * Adapter for Apache Tika NERecogniser implementations (e.g. StanfordCoreNLP, OpenNLP),
 * providing backward compatibility for existing configurations.
 */
public class TikaNERAdapter implements INamedEntityRecognizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TikaNERAdapter.class);

    private final Map<String, NamedEntityParser> nerParserPerLang = new HashMap<>();
    private boolean available = false;

    @Override
    public void init(NamedEntityTaskConfig config) throws Exception {
        if (config.getNerImpl().contains("CoreNLPNERecogniser")) { //$NON-NLS-1$
            try {
                Class.forName("edu.stanford.nlp.ie.crf.CRFClassifier"); //$NON-NLS-1$
            } catch (ClassNotFoundException e) {
                LOGGER.error("StanfordCoreNLP not found. Did you put the jar in 'plugins' folder?");
                config.setEnabled(false);
                return;
            }
        }

        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, config.getNerImpl());

        for (Entry<String, String> entry : config.getLangToModelMap().entrySet()) {
            String lang = entry.getKey();
            String modelPath = entry.getValue();

            URL modelResource = this.getClass().getResource("/" + modelPath); //$NON-NLS-1$
            if (modelResource == null) {
                LOGGER.error(modelPath + " not found. Did you put the model in 'plugins' folder?");
                config.setEnabled(false);
                return;
            }

            System.setProperty(CoreNLPNERecogniser.MODEL_PROP_NAME, modelPath);
            NamedEntityParser nerParser = new NamedEntityParser();
            // first call to initialize
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
            nerParser.parse(new EmptyInputStream(), new IgnoreContentHandler(), metadata, new ParseContext());
            nerParserPerLang.put(lang, nerParser);
        }

        available = !nerParserPerLang.isEmpty();
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Map<String, Set<String>> recognize(String text, String lang) throws Exception {
        NamedEntityParser parser = nerParserPerLang.get(lang);
        if (parser == null) {
            parser = nerParserPerLang.get("default"); //$NON-NLS-1$
        }
        if (parser == null) {
            return Map.of();
        }

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());

        try (InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
            parser.parse(is, new IgnoreContentHandler(), metadata, new ParseContext());
        }

        Map<String, Set<String>> result = new HashMap<>();
        for (String key : metadata.names()) {
            if (key.startsWith(NamedEntityTask.NER_PREFIX)) {
                String entityType = key.substring(NamedEntityTask.NER_PREFIX.length());
                Set<String> set = result.computeIfAbsent(entityType, k -> new HashSet<>());
                for (String val : metadata.getValues(key)) {
                    set.add(val);
                }
            }
        }
        return result;
    }

    @Override
    public void finish() {
        nerParserPerLang.clear();
    }
}
