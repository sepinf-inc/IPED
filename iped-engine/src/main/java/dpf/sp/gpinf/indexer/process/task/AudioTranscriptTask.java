package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.mime.MediaType;
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
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.IItem;
import iped3.util.ExtraProperties;

public class AudioTranscriptTask extends AbstractAudioTranscriptTask{
    
    private static Logger LOGGER = LoggerFactory.getLogger(AudioTranscriptTask.class);
    
    private static final String REGION_KEY = "serviceRegion";
    
    private static final String SUBSCRIPTION_KEY = "azureSubscriptionKey";
    
    private static final String MAX_REQUESTS_KEY = "maxRequests";
    
    private static Semaphore maxRequests;
    
    private SpeechConfig config;
    
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        super.init(confParams, confDir);
        
        UTF8Properties props = new UTF8Properties();
        props.load(new File(confDir, CONF_FILE));
        
        String serviceRegion = props.getProperty(REGION_KEY).trim();
        
        CmdLineArgs args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        String speechSubscriptionKey = args.getExtraParams().get(SUBSCRIPTION_KEY);
        if(speechSubscriptionKey == null) {
            throw new IPEDException("You must pass -X" + SUBSCRIPTION_KEY + "=XXX param to enable audio transcription.");
        }
        
        config = SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion);
        
        config.setProfanity(ProfanityOption.Raw);
        
        config.setOutputFormat(OutputFormat.Detailed);
        
        if(maxRequests == null) {
            int max = Integer.valueOf(props.getProperty(MAX_REQUESTS_KEY).trim());
            maxRequests = new Semaphore(max);
        }
    }
    
    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    protected void process(IItem evidence) throws Exception {
        
        if(!isToProcess(evidence)) {
            return;
        }
        
        File wavFile = null, tmpFile;
        if(evidence.getMediaType().equals(wav)) {
            tmpFile = evidence.getTempFile();
        }else {
            wavFile = getWavFile(evidence);
            if(wavFile == null) {
                return;
            }
            tmpFile = wavFile;
        }
        
        AudioConfig audioInput = AudioConfig.fromWavFileInput(tmpFile.getAbsolutePath());
        
        AutoDetectSourceLanguageConfig langConfig = AutoDetectSourceLanguageConfig.fromLanguages(languages);
        
        try (SpeechRecognizer recognizer = new SpeechRecognizer(config, langConfig, audioInput)){
            
            Semaphore stopTranslationWithFileSemaphore = new Semaphore(0);
            
            StringBuilder result = new StringBuilder();
            AtomicDouble score = new AtomicDouble();
            AtomicInteger frags = new AtomicInteger();
            
            recognizer.recognizing.addEventListener((s, e) -> {
                //System.out.println("RECOGNIZING: Text=" + e.getResult().getText());
            });
            
            recognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    if(frags.get() > 0) {
                        result.append(' ');
                    }
                    result.append(e.getResult().getText());
                    
                    try {
                        String details = e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult);
                        JSONObject json = (JSONObject) new JSONParser().parse(details);
                        score.addAndGet((Double)((JSONObject)((JSONArray)json.get("NBest")).get(0)).get("Confidence"));
                        frags.incrementAndGet();
                        
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                    
                }
                else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    LOGGER.warn("NOMATCH: Speech could not be recognized with {}", evidence.getPath());
                }
            });
            
            recognizer.canceled.addEventListener((s, e) -> {
                if (e.getReason() == CancellationReason.Error) {
                    LOGGER.error("Transcription of {} failed errorCode={} details={}", evidence.getPath(), e.getErrorCode(), e.getErrorDetails());
                }
                stopTranslationWithFileSemaphore.release();
            });
            
            recognizer.sessionStopped.addEventListener((s, e) -> {
                stopTranslationWithFileSemaphore.release();
            });
            
            maxRequests.acquire();
            
            // Starts continuous recognition. Uses StopContinuousRecognitionAsync() to stop recognition.
            recognizer.startContinuousRecognitionAsync().get();
            
            // Waits for completion.
            stopTranslationWithFileSemaphore.acquire();
            
            // Stops recognition.
            recognizer.stopContinuousRecognitionAsync().get();
            
            maxRequests.release();
            
            if(frags.get() == 0) {
                frags.set(1);
            }
            evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(score.doubleValue() / frags.intValue()));
            evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, result.toString());
            
            LOGGER.debug("MS Transcript of {}: {}", evidence.getPath(), result.toString());
            
        } catch (Exception ex) {
            LOGGER.error("Error transcribing {} {}", evidence.getPath(), ex.toString());
            LOGGER.warn("", ex);
            
        }finally {
            if(wavFile != null) {
                wavFile.delete();
            }
        }
        
    }
    
}
