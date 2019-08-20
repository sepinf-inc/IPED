package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;

import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.Item;
import iped3.IItem;

public class LanguageDetectTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(LanguageDetectTask.class);

    private static final String ENABLE_PARAM = "enableLanguageDetect"; //$NON-NLS-1$

    public static final String LANGUAGE_PREFIX = "language:"; //$NON-NLS-1$

    private static final String LANGUAGE_NAMES = LANGUAGE_PREFIX + "all_detected"; //$NON-NLS-1$
    private static final String LANGUAGE_NAME = LANGUAGE_PREFIX + "detected_"; //$NON-NLS-1$
    private static final String LANGUAGE_SCORE = LANGUAGE_PREFIX + "detected_score_"; //$NON-NLS-1$

    private static final int MAX_LANGS = 2;

    private static final int MAX_CHARS = 20000;

    private static LanguageDetector detector;

    private boolean isEnabled = true;

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        String enabled = confParams.getProperty(ENABLE_PARAM);
        if (enabled != null && !enabled.trim().isEmpty())
            isEnabled = Boolean.valueOf(enabled.trim());

        if (isEnabled && detector == null)
            detector = loadModels();
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (evidence.getMediaType().equals(MediaType.OCTET_STREAM))
            return;

        if (((Item) evidence).getTextCache() == null)
            return;

        char[] cbuf = new char[MAX_CHARS];
        int i = 0, off = 0;
        try (Reader reader = evidence.getTextReader()) {
            while (i != -1 && (off += i) < MAX_CHARS)
                i = reader.read(cbuf, off, MAX_CHARS - off);
        }

        if (off == 0)
            return;

        String text = new String(cbuf, 0, off);

        int start = text.lastIndexOf(IndexerDefaultParser.METADATA_HEADER);
        if (start != -1)
            text = text.substring(0, start);

        i = 0;
        List<DetectedLanguage> langs = null;
        try {
            langs = detector.getProbabilities(text);
        } catch (RuntimeException e) {
            LOGGER.info("Error detecting language from " + evidence.getPath(), e); //$NON-NLS-1$
            return;
        }

        List<String> langList = new ArrayList<String>();
        for (DetectedLanguage lang : langs) {
            if (++i > MAX_LANGS)
                break;
            evidence.setExtraAttribute(LANGUAGE_NAME + i, lang.getLocale().toString());
            evidence.setExtraAttribute(LANGUAGE_SCORE + i, (float) lang.getProbability());
            langList.add(lang.getLocale().toString());
        }
        if (!langList.isEmpty())
            evidence.setExtraAttribute(LANGUAGE_NAMES, langList);

    }

    private LanguageDetector loadModels() throws IOException {

        List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();
        LanguageDetectorBuilder builder = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles);

        return builder.build();

    }

}
