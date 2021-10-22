package dpf.sp.gpinf.indexer.process.task.transcript;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.sound.sampled.AudioSystem;

import org.vosk.Model;
import org.vosk.Recognizer;

import dpf.sp.gpinf.indexer.Configuration;

public class VoskTranscriptTask extends AbstractTranscriptTask {

    private static Model model;

    @Override
    protected TextAndScore transcribeWav(File tmpFile) throws Exception {
        
        if (model == null) {
            synchronized (this.getClass()) {
                if (model == null) {
                    model = new Model(
                            new File(Configuration.getInstance().appRoot, "models/vosk-model").getAbsolutePath());
                }
            }
        }

        TextAndScore textAndScore = null;

        try (InputStream ais = AudioSystem.getAudioInputStream(tmpFile);
                Recognizer recognizer = new Recognizer(model, 16000)) {

            StringBuilder text = new StringBuilder();

            int nbytes;
            byte[] b = new byte[4096];
            while ((nbytes = ais.read(b)) >= 0) {
                if (recognizer.acceptWaveForm(b, nbytes)) {
                    text.append(decodeFromJson(recognizer.getResult())).append(" ");
                } else {
                    // System.out.println(recognizer.getPartialResult());
                }
            }

            text.append(decodeFromJson(recognizer.getFinalResult()));

            textAndScore = new TextAndScore();
            textAndScore.text = text.toString();
            textAndScore.score = Double.NaN;
        }

        return textAndScore;
    }

    private String decodeFromJson(String text) {
        String str = new String(text.getBytes(), StandardCharsets.UTF_8);
        int idx = str.indexOf(':');
        idx = str.indexOf('\"', idx + 1);
        return str.substring(idx + 1, str.lastIndexOf("\""));
    }

}
