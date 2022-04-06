package dpf.sp.gpinf.indexer.process.task.transcript;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1p1beta1.RecognitionAudio;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig.Builder;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.exception.IPEDException;

public class GoogleTranscriptTask extends AbstractTranscriptTask {

    private static Logger LOGGER = LoggerFactory.getLogger(GoogleTranscriptTask.class);

    // must be set in environment variable
    private static final String CREDENTIAL_KEY = "GOOGLE_APPLICATION_CREDENTIALS";

    private static final MediaType mp3 = MediaType.audio("mpeg");
    private static final MediaType ogg = MediaType.audio("vorbis");
    private static final MediaType flac = MediaType.audio("x-flac");
    private static final MediaType oggflac = MediaType.audio("x-oggflac");
    private static final MediaType oggopus = MediaType.audio("opus");
    private static final MediaType amr = MediaType.audio("amr");
    private static final MediaType aac = MediaType.audio("x-aac");
    private static final MediaType speex = MediaType.audio("speex");

    private static final int MAX_WAV_TIME = 59;
    private static final int MAX_WAV_SIZE = 16000 * 2 * MAX_WAV_TIME;
    private static final String SPLIT_CMD = "ffmpeg -i $INPUT -f segment -segment_time " + MAX_WAV_TIME
            + " -c copy $OUTPUT%03d.wav";

    private static Object lock = new Object();
    private static long lastTime = 0;

    private SpeechClient speechClient;

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);
        if (!transcriptConfig.isEnabled()) {
            return;
        }

        // TODO how to allow user enable this task when creating report from GUI?
        // Plugin folder does not exist in case folder, so lib won't be found...
        if (caseData.isIpedReport()) {
            transcriptConfig.setEnabled(false);
            return;
        }

        try {
            Class.forName("com.google.cloud.speech.v1p1beta1.SpeechClient");

        } catch (ClassNotFoundException e) {
            throw new IPEDException(
                    "Coud not found required class. Do you put google-cloud-speech.jar and its dependencies in plugin dir?");
        }

        String credential = System.getenv(CREDENTIAL_KEY);
        if (credential == null || credential.trim().isEmpty()) {
            throw new IPEDException(
                    "To use Google transcription, you must specify environment variable " + CREDENTIAL_KEY);
        }

        if (!super.isFfmpegOk()) {
            LOGGER.error("FFmpeg not detected, audios longer than 1min will not be transcribed!");
        }

        speechClient = SpeechClient.create();
    }

    @Override
    public void finish() throws Exception {
        super.finish();
        if (!transcriptConfig.isEnabled()) {
            return;
        }
        speechClient.close();
    }

    protected TextAndScore transcribeWav(File tmpFile) throws Exception {

        if (tmpFile.length() <= MAX_WAV_SIZE || !isFfmpegOk()) {
            return transcribeWavPart(tmpFile);
        } else {
            Collection<File> parts = getAudioSplits(tmpFile);
            StringBuilder sb = new StringBuilder();
            double score = 0;
            for (File part : parts) {
                TextAndScore partResult = transcribeWavPart(part);
                if (partResult != null) {
                    if (score > 0)
                        sb.append(" ");
                    sb.append(partResult.text);
                    score += partResult.score;
                }
                part.delete();
            }
            TextAndScore result = new TextAndScore();
            result.text = sb.toString();
            result.score = score / parts.size();
            return result;
        }
    }

    private Collection<File> getAudioSplits(File tmpFile) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder();
        File outFile = File.createTempFile("iped", "");
        outFile.delete();
        String cmd[] = SPLIT_CMD.split(" ");
        for (int i = 0; i < cmd.length; i++) {
            cmd[i] = cmd[i].replace("$INPUT", tmpFile.getAbsolutePath());
            cmd[i] = cmd[i].replace("$OUTPUT", outFile.getAbsolutePath());
        }
        pb.command(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        IOUtil.loadInputStream(p.getInputStream());
        int exit = p.waitFor();
        if (exit == 0) {
            File[] files = outFile.getParentFile().listFiles(new PrefixFilter(outFile.getName()));
            return new TreeSet<>(Arrays.asList(files));
        } else {
            LOGGER.error("Failed to split audio file " + evidence.getPath());
            return Collections.EMPTY_LIST;
        }
    }

    private class PrefixFilter implements FilenameFilter {

        private String prefix;

        PrefixFilter(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith(prefix);
        }
    }

    protected TextAndScore transcribeWavPart(File tmpFile) throws Exception {

        // google has request per time quota limits
        synchronized (lock) {
            long t = System.currentTimeMillis();
            long dif = t - lastTime;
            if (dif < transcriptConfig.getRequestIntervalMillis()) {
                Thread.sleep(transcriptConfig.getRequestIntervalMillis() - dif);
            }
            lastTime = System.currentTimeMillis();
        }

        TextAndScore textAndScore = null;
        try {
            // The language of the supplied audio
            String languageCode = transcriptConfig.getLanguages().get(0);

            List<String> alternativeLangs = transcriptConfig.getLanguages().subList(1,
                    transcriptConfig.getLanguages().size());

            Builder builder = RecognitionConfig.newBuilder().setLanguageCode(languageCode)
                    .addAllAlternativeLanguageCodes(alternativeLangs);

            /*
             * // Sample rate in Hertz of the audio data sent int sampleRateHertz = 48000;
             * 
             * // Encoding of audio data sent. This sample sets this explicitly. // This
             * field is optional for FLAC and WAV audio formats.
             * RecognitionConfig.AudioEncoding encoding =
             * RecognitionConfig.AudioEncoding.MP3;
             * 
             * boolean wavOrflac = false; if(evidence.getMediaType().equals(oggopus)) {
             * //does not work... //encoding = RecognitionConfig.AudioEncoding.OGG_OPUS;
             * 
             * }else if(evidence.getMediaType().equals(flac)) { encoding =
             * RecognitionConfig.AudioEncoding.FLAC; wavOrflac = true; }else
             * if(evidence.getMediaType().equals(wav)) { encoding =
             * RecognitionConfig.AudioEncoding.LINEAR16; wavOrflac = true; }
             * 
             * if(!wavOrflac) { builder.setEncoding(encoding);
             * builder.setSampleRateHertz(sampleRateHertz); }
             */

            RecognitionConfig config = builder.build();

            ByteString content;
            try (InputStream is = Files.newInputStream(tmpFile.toPath())) {
                content = ByteString.readFrom(is);
            }
            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(content).build();

            LongRunningRecognizeRequest request = LongRunningRecognizeRequest.newBuilder().setConfig(config)
                    .setAudio(audio).build();

            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> future = speechClient
                    .longRunningRecognizeAsync(request);

            LongRunningRecognizeResponse response = future.get(
                    MIN_TIMEOUT + (transcriptConfig.getTimeoutPerSec() * tmpFile.length() / WAV_BYTES_PER_SEC),
                    TimeUnit.SECONDS);

            StringBuilder text = new StringBuilder();
            float confidence = 0;
            int i = 0;
            for (SpeechRecognitionResult result : response.getResultsList()) {
                // First alternative is the most probable result
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                confidence += alternative.getConfidence();
                i++;
                text.append(alternative.getTranscript()).append(" ");
            }
            if (i == 0)
                i = 1;

            textAndScore = new TextAndScore();
            textAndScore.text = text.toString();
            textAndScore.score = confidence / i;

            LOGGER.debug("GG Transcript of {} : {}", evidence.getPath(), text.toString());

        } catch (Exception e) {
            LOGGER.error("Failed to transcript {} : {}", evidence.getPath(), e);
            LOGGER.warn("", e);
        }

        return textAndScore;

    }

}
