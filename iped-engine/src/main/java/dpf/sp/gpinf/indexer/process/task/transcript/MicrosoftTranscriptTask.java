package dpf.sp.gpinf.indexer.process.task.transcript;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.AtomicDouble;
import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.OutputFormat;
import com.microsoft.cognitiveservices.speech.ProfanityOption;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.util.IPEDException;

public class MicrosoftTranscriptTask extends AbstractTranscriptTask {

    private static Logger LOGGER = LoggerFactory.getLogger(MicrosoftTranscriptTask.class);

    private static final String SUBSCRIPTION_KEY = "azureSubscriptionKey";

    private static Semaphore maxConcurrentRequests;

    private SpeechConfig config;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        super.init(confParams, confDir);

        if (!transcriptConfig.isEnabled()) {
            return;
        }

        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        String speechSubscriptionKey = args.getExtraParams().get(SUBSCRIPTION_KEY);
        if (speechSubscriptionKey == null && !caseData.isIpedReport()) {
            throw new IPEDException(
                    "You must pass -X" + SUBSCRIPTION_KEY + "=XXX param to enable audio transcription.");
        }

        config = SpeechConfig.fromSubscription(speechSubscriptionKey, transcriptConfig.getServiceRegion());

        config.setProfanity(ProfanityOption.Raw);

        config.setOutputFormat(OutputFormat.Detailed);

        if (maxConcurrentRequests == null) {
            maxConcurrentRequests = new Semaphore(transcriptConfig.getMaxConcurrentRequests());
        }
    }

    @Override
    public void finish() throws Exception {
        super.finish();
    }

    @Override
    protected TextAndScore transcribeWav(File tmpFile) throws Exception {

        int tries = 0;
        AtomicBoolean ok = new AtomicBoolean();
        TextAndScore textAndScore = null;
        while (!ok.get() && ++tries <= 3) {
            ok.set(true);
            AutoDetectSourceLanguageConfig langConfig = AutoDetectSourceLanguageConfig
                    .fromLanguages(transcriptConfig.getLanguages());
            AudioConfig audioInput = AudioConfig.fromWavFileInput(tmpFile.getAbsolutePath());
            maxConcurrentRequests.acquire();
            try (SpeechRecognizer recognizer = new SpeechRecognizer(config, langConfig, audioInput)) {

                Semaphore stopTranslationWithFileSemaphore = new Semaphore(0);

                StringBuilder result = new StringBuilder();
                AtomicDouble score = new AtomicDouble();
                AtomicInteger frags = new AtomicInteger();

                recognizer.recognizing.addEventListener((s, e) -> {
                    // System.out.println("RECOGNIZING: Text=" + e.getResult().getText());
                });

                recognizer.recognized.addEventListener((s, e) -> {
                    if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                        if (frags.get() > 0) {
                            result.append(' ');
                        }
                        result.append(e.getResult().getText());

                        try {
                            String details = e.getResult().getProperties()
                                    .getProperty(PropertyId.SpeechServiceResponse_JsonResult);
                            JSONObject json = (JSONObject) new JSONParser().parse(details);
                            score.addAndGet(
                                    (Double) ((JSONObject) ((JSONArray) json.get("NBest")).get(0)).get("Confidence"));
                            frags.incrementAndGet();

                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }

                    } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                        ok.set(false);
                        LOGGER.warn("NOMATCH: Speech could not be recognized with {}", evidence.getPath());
                    }
                });

                recognizer.canceled.addEventListener((s, e) -> {
                    if (e.getReason() == CancellationReason.Error) {
                        ok.set(false);
                        LOGGER.error("Transcription of {} failed errorCode={} details={}", evidence.getPath(),
                                e.getErrorCode(), e.getErrorDetails());
                    }
                    stopTranslationWithFileSemaphore.release();
                });

                recognizer.sessionStopped.addEventListener((s, e) -> {
                    stopTranslationWithFileSemaphore.release();
                });

                // Starts continuous recognition. Uses StopContinuousRecognitionAsync() to stop
                // recognition.
                recognizer.startContinuousRecognitionAsync().get();

                // Waits for completion.
                boolean acquired = stopTranslationWithFileSemaphore.tryAcquire(
                        MIN_TIMEOUT + (transcriptConfig.getTimeoutPerSec() * tmpFile.length() / WAV_BYTES_PER_SEC),
                        TimeUnit.SECONDS);
                if (!acquired) {
                    ok.set(false);
                    throw new TimeoutException("Timeout waiting for transcription.");
                }

                // Stops recognition.
                recognizer.stopContinuousRecognitionAsync().get();

                textAndScore = new TextAndScore();
                textAndScore.text = result.toString();
                textAndScore.score = score.doubleValue() / (frags.intValue() != 0 ? frags.intValue() : 1);

                LOGGER.debug("MS Transcript of {}: {}", evidence.getPath(), result.toString());

            } catch (Exception ex) {
                LOGGER.error("Error transcribing {} {}", evidence.getPath(), ex.toString());
                LOGGER.warn("", ex);

            } finally {
                maxConcurrentRequests.release();
            }
        }

        return textAndScore;

    }

}
