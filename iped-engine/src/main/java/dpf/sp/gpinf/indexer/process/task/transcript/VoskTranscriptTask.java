package dpf.sp.gpinf.indexer.process.task.transcript;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioSystem;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.Model;
import org.vosk.Recognizer;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;

public class VoskTranscriptTask extends AbstractTranscriptTask {

    private static Logger logger = LoggerFactory.getLogger(VoskTranscriptTask.class);

    private static Model model;

    private Recognizer recognizer;

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!this.isEnabled()) {
            return;
        }

        if (model == null) {
            List<String> langs = transcriptConfig.getLanguages();
            if (langs.size() > 1) {
                logger.error("Vosk transcription supports only 1 language, '{}' will be used.", langs.get(0));
            }
            File modelDir = new File(Configuration.getInstance().appRoot, "models/vosk/" + langs.get(0));
            if (!modelDir.exists() || !modelDir.isDirectory() || modelDir.listFiles().length == 0) {
                logger.error("Invalid Vosk transcription model {}. English (en) will be used instead.",
                        modelDir.getAbsolutePath());
                modelDir = new File(Configuration.getInstance().appRoot, "models/vosk/en");
            }
            model = new Model(modelDir.getAbsolutePath());
        }

        recognizer = new Recognizer(model, 16000);
        recognizer.setWords(true);
        recognizer.setMaxAlternatives(3);

    }

    @Override
    public void finish() throws Exception {
        super.finish();
        if (this.isEnabled()) {
            recognizer.close();
            if (model != null) {
                model.close();
                model = null;
            }
        }
    }

    @Override
    protected List<TextAndScore> transcribeWav(File tmpFile) throws Exception {

        List<TextAndScore> results = new ArrayList<>();
        recognizer.reset();

        try (InputStream ais = AudioSystem.getAudioInputStream(tmpFile)) {

            int nbytes;
            byte[] buf = new byte[1 << 20];
            while ((nbytes = ais.read(buf)) >= 0) {
                if (recognizer.acceptWaveForm(buf, nbytes)) {
                    List<TextScoreWords> alternatives = decodeFromJson(recognizer.getResult());
                    for (int i = 0; i < alternatives.size(); i++) {
                        TextScoreWords alternative = alternatives.get(i);
                        if (i == results.size()) {
                            results.add(alternative);
                        } else {
                            TextScoreWords result = (TextScoreWords) results.get(i);
                            result.text += alternative.text + " ";
                            result.score += alternative.score;
                            result.chunks += alternative.chunks;
                        }
                    }

                } else {
                    // System.out.println(recognizer.getPartialResult());
                }
            }

            List<TextScoreWords> alternatives = decodeFromJson(recognizer.getFinalResult());
            for (int i = 0; i < alternatives.size(); i++) {
                TextScoreWords alternative = alternatives.get(i);
                if (i == results.size()) {
                    results.add(alternative);
                } else {
                    TextScoreWords result = (TextScoreWords) results.get(i);
                    result.text += alternative.text;
                    result.score += alternative.score;
                    result.chunks += alternative.chunks;
                }
            }

            for (TextAndScore result : results) {
                int chunks = ((TextScoreWords) result).chunks;
                if (chunks > 0) {
                    result.score /= chunks;
                }
            }
        }

        return results;
    }

    private List<TextScoreWords> decodeFromJson(String json) throws ParseException {
        List<TextScoreWords> results = new ArrayList<>();
        String str = new String(json.getBytes(), StandardCharsets.UTF_8);
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(str);
        JSONArray alternatives = (JSONArray) root.get("alternatives");
        if (alternatives == null || alternatives.size() == 0) {
            return results;
        }
        for (int a = 0; a < alternatives.size(); a++) {
            JSONObject alternative = (JSONObject) alternatives.get(a);

            TextScoreWords result = new TextScoreWords();
            result.text = (String) alternative.get("text");
            // this confidence score is not normalized, how to normalize it between 0-1?
            // when just setWords(true) is used, normalized scores are returned per word
            // but when alternatives are enabled, confidence is not normalized...
            result.score = (Double) alternative.get("confidence");
            if (!result.text.trim().isEmpty()) {
                result.chunks = 1;
            }
            results.add(result);
        }

        return results;
    }

    private static class TextScoreWords extends TextAndScore {
        int chunks = 0;
    }

}
