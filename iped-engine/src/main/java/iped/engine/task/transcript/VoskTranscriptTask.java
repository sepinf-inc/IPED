package iped.engine.task.transcript;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.Model;
import org.vosk.Recognizer;

import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.exception.IPEDException;

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
            String language = langs.get(0);
            File modelDir = new File(Configuration.getInstance().appRoot, "models/vosk/" + language);
            if (!language.equals("en")
                    && (!modelDir.exists() || !modelDir.isDirectory() || modelDir.listFiles().length == 0)) {
                File enModelDir = new File(Configuration.getInstance().appRoot, "models/vosk/en");
                if (enModelDir.exists() && enModelDir.isDirectory() && enModelDir.listFiles().length != 0) {
                    logger.error("Invalid Vosk transcription model {}. English (en) will be used instead.",
                            modelDir.getAbsolutePath());
                    modelDir = enModelDir;
                }
            }
            if (!modelDir.exists() || !modelDir.isDirectory() || modelDir.listFiles().length == 0) {
                String msg = "Invalid Vosk transcription model: " + modelDir.getAbsolutePath();
                if (hasIpedDatasource()) {
                    transcriptConfig.setEnabled(false);
                    logger.warn(msg);
                    return;
                }
                throw new IPEDException(msg);
            }
            model = new Model(modelDir.getAbsolutePath());
        }

        recognizer = new Recognizer(model, 16000);
        recognizer.setWords(true);

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
    protected TextAndScore transcribeAudio(File tmpFile) throws Exception {

        TextAndScore textAndScore = null;
        recognizer.reset();

        try (InputStream ais = AudioSystem.getAudioInputStream(tmpFile)) {

            StringBuilder totalText = new StringBuilder();
            double totalScore = 0;
            int words = 0;

            int nbytes;
            // Buffer must be small (see #1909)
            byte[] buf = new byte[(int) Math.min(tmpFile.length(), 1 << 16)];
            while ((nbytes = ais.read(buf)) >= 0) {
                if (recognizer.acceptWaveForm(buf, nbytes)) {
                    TextScoreWords result = decodeFromJson(recognizer.getResult());
                    if (result != null) {
                        totalText.append(result.text);
                        totalScore += result.score;
                        words += result.words;
                    }
                } else {
                    // System.out.println(recognizer.getPartialResult());
                }
            }

            TextScoreWords result = decodeFromJson(recognizer.getFinalResult());
            if (result != null) {
                totalText.append(result.text);
                totalScore += result.score;
                words += result.words;
            }

            if (words > 0) {
                textAndScore = new TextAndScore();
                textAndScore.text = totalText.toString().trim();
                textAndScore.score = totalScore / words;
            }
        } catch (IOException | UnsupportedAudioFileException e) {
            logger.warn("Fail to transcribe audio file " + evidence.getPath(), e);
        }

        return textAndScore;
    }

    private TextScoreWords decodeFromJson(String json) throws ParseException {
        // workaround for https://github.com/sepinf-inc/IPED/issues/1058
        json = fixJsonNumberFormatting(json);

        String str = new String(json.getBytes(), StandardCharsets.UTF_8);
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(str);
        JSONArray array = (JSONArray) root.get("result");
        if (array == null || array.size() == 0) {
            return null;
        }

        double totScore = 0;
        int words = array.size();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < words; i++) {
            JSONObject obj = (JSONObject) array.get(i);
            double score = (Double) obj.get("conf");
            if (score >= transcriptConfig.getMinWordScore()) {
                text.append(obj.get("word")).append(" ");
            } else {
                text.append("* ");
            }
            totScore += score;
        }

        TextScoreWords result = new TextScoreWords();
        result.text = text.toString();
        result.score = totScore;
        result.words = words;
        return result;
    }

    private static String fixJsonNumberFormatting(String json) {
        if (System.getProperty("os.name").equalsIgnoreCase("windows")) {
            return json;
        }
        int lastWordIdx = 0;
        while (true) {
            int confIdx = json.indexOf("\"conf\"", lastWordIdx);
            if (confIdx == -1)
                break;
            int endIdx = json.indexOf("\"end\"", confIdx);
            int startIdx = json.indexOf("\"start\"", endIdx);
            int wordIdx = json.indexOf("\"word\"", startIdx);
            int[] idx = { confIdx, endIdx, startIdx, wordIdx };
            int i = -1;
            while (++i < 3) {
                int comma1Idx = json.indexOf(',', idx[i]);
                if (comma1Idx != -1 && comma1Idx < idx[i + 1]) {
                    int comma2Idx = json.indexOf(',', comma1Idx + 1);
                    if (comma2Idx != -1 && comma2Idx < idx[i + 1]) {
                        json = json.substring(0, comma1Idx) + '.' + json.substring(comma1Idx + 1);
                    } else if (comma2Idx > idx[i + 1]) {
                        return json;
                    }
                }
            }
            lastWordIdx = wordIdx;
        }
        return json;
    }

    private static class TextScoreWords extends TextAndScore {
        int words = 0;
    }

}
