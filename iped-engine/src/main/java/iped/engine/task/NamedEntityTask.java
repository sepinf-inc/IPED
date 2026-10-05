package iped.engine.task;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.parser.ner.NamedEntityParser;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.NamedEntityTaskConfig;
import iped.engine.data.Item;
import iped.engine.task.ner.INamedEntityRecognizer;
import iped.engine.task.ner.spacy.SpaCyNERecogniser;
import iped.engine.task.ner.tika.TikaNERAdapter;
import iped.parsers.standard.StandardParser;

public class NamedEntityTask extends AbstractTask {

    public static final String NER_PREFIX = NamedEntityParser.MD_KEY_PREFIX;

    private static final int MAX_TEXT_LEN = 100000;

    private static final int MAX_ENTITY_BYTES_LEN = 32766;

    private static INamedEntityRecognizer recognizer;

    private NamedEntityTaskConfig nerConfig;

    @Override
    public boolean isEnabled() {
        return nerConfig.isEnabled();
    }

    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new NamedEntityTaskConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        nerConfig = configurationManager.findObject(NamedEntityTaskConfig.class);

        if (!nerConfig.isEnabled())
            return;

        synchronized (NamedEntityTask.class) {
            if (recognizer == null) {
                String impl = nerConfig.getNerImpl();
                if (impl != null && (impl.contains("SpaCyNERecogniser") || "spacy".equalsIgnoreCase(impl.trim()))) { //$NON-NLS-1$ //$NON-NLS-2$
                    recognizer = new SpaCyNERecogniser();
                } else {
                    recognizer = new TikaNERAdapter();
                }
                recognizer.init(nerConfig);
            }
        }

    }

    @Override
    public void finish() throws Exception {
        synchronized (NamedEntityTask.class) {
            if (recognizer != null) {
                recognizer.finish();
                recognizer = null;
            }
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isEnabled() || !evidence.isToAddToCase() || recognizer == null || !recognizer.isAvailable())
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

        Float langScore = (Float) evidence.getExtraAttribute("language:detected_score_1"); //$NON-NLS-1$
        String lang = (String) evidence.getExtraAttribute("language:detected_1"); //$NON-NLS-1$
        if (langScore == null || langScore < nerConfig.getMinLangScore()) {
            langScore = (Float) evidence.getExtraAttribute("language:detected_score_2"); //$NON-NLS-1$
            lang = (String) evidence.getExtraAttribute("language:detected_2"); //$NON-NLS-1$
            if (langScore == null || langScore < nerConfig.getMinLangScore()) {
                lang = "default"; //$NON-NLS-1$
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

                if (textFrag.trim().isEmpty()) {
                    continue;
                }

                Map<String, Set<String>> entities = recognizer.recognize(textFrag, lang);
                if (entities != null && !entities.isEmpty()) {
                    for (Map.Entry<String, Set<String>> entry : entities.entrySet()) {
                        String key = entry.getKey().startsWith(NER_PREFIX) ? entry.getKey() : (NER_PREFIX + entry.getKey());
                        for (String val : entry.getValue()) {
                            if (val != null && val.getBytes(StandardCharsets.UTF_8).length <= MAX_ENTITY_BYTES_LEN) {
                                evidence.getMetadata().add(key, val);
                            }
                        }
                    }
                }
            }
        }

    }

}
