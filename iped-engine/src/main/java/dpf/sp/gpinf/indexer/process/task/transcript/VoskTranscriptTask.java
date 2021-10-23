package dpf.sp.gpinf.indexer.process.task.transcript;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.sound.sampled.AudioSystem;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.vosk.Model;
import org.vosk.Recognizer;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;

public class VoskTranscriptTask extends AbstractTranscriptTask {

    private static Model model;

    private Recognizer recognizer;

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!this.isEnabled()) {
            return;
        }

        if (model == null) {
            model = new Model(new File(Configuration.getInstance().appRoot, "models/vosk").getAbsolutePath());
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
    protected TextAndScore transcribeWav(File tmpFile) throws Exception {

        TextAndScore textAndScore = null;
        recognizer.reset();

        try (InputStream ais = AudioSystem.getAudioInputStream(tmpFile)) {

            StringBuilder totalText = new StringBuilder();
            double totalScore = 0;
            int words = 0;

            int nbytes;
            byte[] b = new byte[1 << 14];
            while ((nbytes = ais.read(b)) >= 0) {
                if (recognizer.acceptWaveForm(b, nbytes)) {
                    TextScoreWords result = decodeFromJson(recognizer.getResult());
                    if (result != null) {
                        totalText.append(result.text).append(" ");
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

            textAndScore = new TextAndScore();
            textAndScore.text = totalText.toString().trim();
            textAndScore.score = totalScore / words;
        }

        return textAndScore;
    }

    private TextScoreWords decodeFromJson(String text) throws ParseException {
        String str = new String(text.getBytes(), StandardCharsets.UTF_8);
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(str);
        JSONArray array = (JSONArray) root.get("result");
        if (array == null) {
            return null;
        }

        double score = 0;
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = (JSONObject) array.get(i);
            score += (Double) obj.get("conf");
        }

        TextScoreWords result = new TextScoreWords();
        result.text = (String) root.get("text");
        result.score = score;
        result.words = array.size();
        return result;
    }

    private static class TextScoreWords extends TextAndScore {
        int words = 0;
    }

}
