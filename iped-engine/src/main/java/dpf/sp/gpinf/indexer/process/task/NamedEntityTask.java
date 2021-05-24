package dpf.sp.gpinf.indexer.process.task;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ner.NamedEntityParser;
import org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.NamedEntityTaskConfig;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.util.IgnoreContentHandler;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import dpf.sp.gpinf.indexer.util.IPEDException;
import gpinf.dev.data.Item;
import iped3.IItem;

public class NamedEntityTask extends AbstractTask {

    public static final String NER_PREFIX = NamedEntityParser.MD_KEY_PREFIX;

    private static final int MAX_TEXT_LEN = 100000;

    private static final Logger LOGGER = LoggerFactory.getLogger(NamedEntityTask.class);

    private static AtomicBoolean inited = new AtomicBoolean();

    private static Map<String, NamedEntityParser> nerParserPerLang = new HashMap<String, NamedEntityParser>();

    private NamedEntityTaskConfig nerConfig;

    @Override
    public boolean isEnabled() {
        return nerConfig.isTaskEnabled();
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        nerConfig = ConfigurationManager.findObject(NamedEntityTaskConfig.class);

        if (inited.getAndSet(true))
            return;

        if (!nerConfig.isTaskEnabled())
            return;

        System.setProperty(NamedEntityParser.SYS_PROP_NER_IMPL, nerConfig.getNerImpl());

        for (Entry<String, String> entry : nerConfig.getLangToModelMap().entrySet()) {
            String lang = entry.getKey();
            String modelPath = entry.getValue();

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

        Metadata metadata = evidence.getMetadata();
        String originalContentType = metadata.get(Metadata.CONTENT_TYPE);

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
                    int k = textFrag.lastIndexOf(IndexerDefaultParser.METADATA_HEADER);
                    if (k != -1)
                        textFrag = textFrag.substring(0, k);
                }

                metadata.set(Metadata.CONTENT_TYPE, MediaType.TEXT_PLAIN.toString());
                // try to solve #329
                metadata.remove(null);

                try (InputStream is = new ByteArrayInputStream(textFrag.getBytes(StandardCharsets.UTF_8))) {

                    nerParser.parse(is, new IgnoreContentHandler(), metadata, new ParseContext());

                } finally {
                    metadata.set(Metadata.CONTENT_TYPE, originalContentType);
                }
            }
        }

    }

}
