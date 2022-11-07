package iped.engine.task;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ner.NamedEntityParser;
import org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.NamedEntityTaskConfig;
import iped.engine.data.Item;
import iped.exception.IPEDException;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.IgnoreContentHandler;
import iped.utils.EmptyInputStream;

public class NamedEntityTask extends AbstractTask {

    public static final String NER_PREFIX = NamedEntityParser.MD_KEY_PREFIX;

    private static final int MAX_TEXT_LEN = 100000;

    private static final int MAX_ENTITY_BYTES_LEN = 32766;

    private static final Logger LOGGER = LoggerFactory.getLogger(NamedEntityTask.class);

    private static AtomicBoolean inited = new AtomicBoolean();

    private static Map<String, NamedEntityParser> nerParserPerLang = new HashMap<String, NamedEntityParser>();

    private NamedEntityTaskConfig nerConfig;

    @Override
    public boolean isEnabled() {
        return nerConfig.isEnabled();
    }

    public List<Configurable<?>> getConfigurables() {
        NamedEntityTaskConfig result = ConfigurationManager.get().findObject(NamedEntityTaskConfig.class);
        if(result == null) {
            result = new NamedEntityTaskConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        nerConfig = configurationManager.findObject(NamedEntityTaskConfig.class);

        if (inited.getAndSet(true))
            return;

        if (!nerConfig.isEnabled())
            return;

        if (nerConfig.getNerImpl().contains("CoreNLPNERecogniser")) { //$NON-NLS-1$
            try {
                Class.forName("edu.stanford.nlp.ie.crf.CRFClassifier"); //$NON-NLS-1$

            } catch (ClassNotFoundException e) {
                LOGGER.error("StanfordCoreNLP not found. Did you put the jar in the optional lib folder?");
                nerConfig.setEnabled(false);
                return;
            }
        }

        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, nerConfig.getNerImpl());

        for (Entry<String, String> entry : nerConfig.getLangToModelMap().entrySet()) {
            String lang = entry.getKey();
            String modelPath = entry.getValue();

            URL modelResource = this.getClass().getResource("/" + modelPath); //$NON-NLS-1$
            if (modelResource == null) {
                LOGGER.error(modelPath + " not found. Did you put the model in the optional lib folder?");
                nerConfig.setEnabled(false);
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

    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isEnabled() || !evidence.isToAddToCase())
            return;

        String mime = evidence.getMediaType().toString();
        String categories = evidence.getCategories();

        if (((Item) evidence).getTextCache() == null)
            return;

        for (String ignore : nerConfig.getMimeTypesToIgnore())
            if (mime.startsWith(ignore))
                return;

        for (String ignore : nerConfig.getCategoriesToIgnore())
            if (categories.contains(ignore))
                return;

        NamedEntityParser nerParser = null;
        Float langScore = (Float) evidence.getExtraAttribute("language:detected_score_1"); //$NON-NLS-1$
        String lang = (String) evidence.getExtraAttribute("language:detected_1"); //$NON-NLS-1$
        if (langScore != null && langScore >= nerConfig.getMinLangScore())
            nerParser = nerParserPerLang.get(lang);
        if (nerParser == null) {
            langScore = (Float) evidence.getExtraAttribute("language:detected_score_2"); //$NON-NLS-1$
            lang = (String) evidence.getExtraAttribute("language:detected_2"); //$NON-NLS-1$
            if (langScore != null && langScore >= nerConfig.getMinLangScore())
                nerParser = nerParserPerLang.get(lang);
        }
        if (nerParser == null) {
            nerParser = nerParserPerLang.get("default"); //$NON-NLS-1$
            if (nerParser == null) {
                throw new IPEDException(
                        "No 'default' NER language model configured in " + NamedEntityTaskConfig.CONF_FILE);
            }
        }

        char[] cbuf = new char[MAX_TEXT_LEN];
        int i = 0;
        try (Reader textReader = evidence.getTextReader()) {
            while (i != -1) {
                int off = 0;
                i = 0;
                while (i != -1 && (off += i) < cbuf.length)
                    i = textReader.read(cbuf, off, cbuf.length - off);

                String textFrag = new String(cbuf, 0, off);
                // filter out metadata from last frag
                if (i == -1) {
                    int k = textFrag.lastIndexOf(StandardParser.METADATA_HEADER);
                    if (k != -1)
                        textFrag = textFrag.substring(0, k);
                }

                Metadata metadata = new Metadata();
                metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());

                try (InputStream is = new ByteArrayInputStream(textFrag.getBytes(StandardCharsets.UTF_8))) {

                    nerParser.parse(is, new IgnoreContentHandler(), metadata, new ParseContext());

                } finally {
                    cleanHugeResults(metadata);
                    // save results in item metadata
                    for (String key : metadata.names()) {
                        if (key.startsWith(NER_PREFIX)) {
                            for (String val : metadata.getValues(key)) {
                                evidence.getMetadata().add(key, val);
                            }
                        }
                    }
                }
            }
        }

    }

    // workaround for issue #783
    private void cleanHugeResults(Metadata meta) {
        for (String key : meta.names()) {
            if (key.startsWith(NER_PREFIX)) {
                ArrayList<String> list = new ArrayList<>();
                String[] vals = meta.getValues(key);
                boolean remove = false;
                for (String val : vals) {
                    if (val.getBytes(StandardCharsets.UTF_8).length <= MAX_ENTITY_BYTES_LEN) {
                        list.add(val);
                    } else {
                        remove = true;
                    }
                }
                if (remove) {
                    meta.remove(key);
                    for (String val : list) {
                        meta.add(key, val);
                    }
                }
            }
        }

    }

}
