package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.SystemUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Pragma;
import org.sqlite.SQLiteConfig.SynchronousMode;

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
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.IItem;
import iped3.util.ExtraProperties;

public class AudioTranscriptTask extends AbstractTask{
    
    private static Logger LOGGER = LoggerFactory.getLogger(AudioTranscriptTask.class);
    
    private static final String ENABLE_KEY = "enableAudioTranscription";
    
    private static final String CONF_FILE = "AudioTranscriptConfig.txt";
    
    private static final String LANG_KEY = "language";
    
    private static final String REGION_KEY = "serviceRegion";
    
    private static final String SUBSCRIPTION_KEY = "azureSubscriptionKey";
    
    private static final String MIMES_KEY = "mimesToProcess";
    
    private static final String CONVERT_CMD_KEY = "convertCommand";
    
    private static final String MAX_REQUESTS_KEY = "maxRequests";
    
    private static final String TEST_CMD = "ffmpeg -version";
    
    private static final MediaType wav = MediaType.audio("vnd.wave");
    
    private static final String TEXT_STORAGE = "text/transcriptions.db"; //$NON-NLS-1$
    
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transcriptions(id TEXT PRIMARY KEY, text TEXT, score REAL);"; //$NON-NLS-1$
    
    private static final String INSERT_DATA = "INSERT INTO transcriptions(id, text, score) VALUES(?,?,?) ON CONFLICT(id) DO NOTHING"; //$NON-NLS-1$
    
    private static final String SELECT_EXACT = "SELECT text, score FROM transcriptions WHERE id=?;"; //$NON-NLS-1$
    
    private static boolean ffmpegTested = false;
    
    private static boolean ffmpegDetected = false;
    
    private static Semaphore maxRequests;
    
    private Connection conn;
    
    private List<String> languages = new ArrayList<>();
    
    private List<String> mimesToProcess = new ArrayList<>();
    
    private String convertCmd;
    
    private SpeechConfig config;
    
    private boolean isEnabled = false;
    
    @Override
    public boolean isEnabled() {
        return this.isEnabled;
    }
    
    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        String enabled = confParams.getProperty(ENABLE_KEY);
        if(enabled != null) {
            isEnabled = Boolean.valueOf(enabled.trim());
        }
        if(!isEnabled) {
            return;
        }
        
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
        
        String langs = props.getProperty(LANG_KEY);
        for(String lang : langs.split(";")) {
            languages.add(lang.trim());
        }
        
        convertCmd = props.getProperty(CONVERT_CMD_KEY).trim();
        
        String mimes = props.getProperty(MIMES_KEY).trim();
        for(String mime : mimes.split(";")) {
            mimesToProcess.add(mime.trim());
        }
        
        if(conn == null) {
            createConnection();
        }
        
        //testFfmpeg();
    }
    
    private void testFfmpeg() {
        if(!ffmpegTested) {
            try {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(TEST_CMD.split(" "));
                pb.redirectErrorStream(true);
                Process p = pb.start();
                IOUtil.loadInputStream(p.getInputStream());
                int exit = p.waitFor();
                if(exit == 0) {
                    ffmpegDetected = true;
                }
            }catch(Exception e) {
                e.printStackTrace();
            }
            if(!ffmpegDetected) {
                LOGGER.error("Error testing ffmpeg, is it on path? Just wav files will be transcribed.");
            }
            ffmpegTested = true;
        }
    }
    
    private void createConnection() {
        File db = new File(output, TEXT_STORAGE);
        db.getParentFile().mkdirs();
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setSynchronous(SynchronousMode.OFF);
            config.setBusyTimeout(3600000);
            conn = config.createConnection("jdbc:sqlite:" + db.getAbsolutePath());
            
            try(Statement stmt = conn.createStatement()){
                stmt.executeUpdate(CREATE_TABLE);
            }
            
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    private class TextAndScore{
        String text;
        double score;
    }
    
    private TextAndScore getTextFromDb(String id) throws IOException {
        try(PreparedStatement ps = conn.prepareStatement(SELECT_EXACT)){
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if(rs.next()) {
                TextAndScore result = new TextAndScore();
                result.text = rs.getString(1);
                result.score = rs.getDouble(2);
                return result;
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return null;
    }
    
    private void storeTextInDb(String id, String text, double score) throws IOException {
        try(PreparedStatement ps = conn.prepareStatement(INSERT_DATA)){
            ps.setString(1, id);
            ps.setString(2, text);
            ps.setDouble(3, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
    
    @Override
    public void finish() throws Exception {
        if(conn != null) {
            conn.close();
            conn = null;
        }
    }
    
    private File getWavFile(IItem evidence) throws IOException, InterruptedException {
        if(ffmpegTested && !ffmpegDetected) {
            return null;
        }
        File input = evidence.getTempFile();
        File tmpFile = File.createTempFile("iped", ".wav");
        tmpFile.delete();
        ProcessBuilder pb = new ProcessBuilder();
        String[] cmd = convertCmd.split(" ");
        if(SystemUtils.IS_OS_WINDOWS) {
            cmd[0] = cmd[0].replace("mplayer", Configuration.getInstance().appRoot + "/" + VideoThumbTask.mplayerWin);
        }
        for(int i = 0; i < cmd.length; i++) {
            cmd[i] = cmd[i].replace("$INPUT", input.getAbsolutePath());
            cmd[i] = cmd[i].replace("$OUTPUT", tmpFile.getAbsolutePath());
        }
        pb.command(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        IOUtil.loadInputStream(p.getInputStream());
        int exit = p.waitFor();
        if(exit != 0) {
            tmpFile.delete();
            return null;
        }
        return tmpFile;
    }
    
    @Override
    protected void process(IItem evidence) throws Exception {
        
        boolean process = false;
        for(String mime : mimesToProcess) {
            if(evidence.getMediaType().toString().startsWith(mime)) {
                process = true;
                break;
            }
        }
        if (!process || evidence.getLength() == null || evidence.getLength() == 0 || !evidence.isToAddToCase() || 
                evidence.getHash() == null || evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null) {
            return;
        }
        
        TextAndScore prevResult = getTextFromDb(evidence.getHash());
        if(prevResult != null) {
            evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(prevResult.score));
            evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, prevResult.text);
            return;
        }
        
        File wavFile = null, tmpFile;
        if(evidence.getMediaType().equals(wav)) {
            tmpFile = evidence.getTempFile();
        }else {
            wavFile = getWavFile(evidence);
            if(wavFile == null) {
                LOGGER.warn("Error converting to wav {}", evidence.getPath());
                return;
            }
            tmpFile = wavFile;
        }
        
        int tries = 0;
        AtomicBoolean ok = new AtomicBoolean();
        while(!ok.get() && tries < 2) {
            tries++;
            ok.set(true);
            AutoDetectSourceLanguageConfig langConfig = AutoDetectSourceLanguageConfig.fromLanguages(languages);
            AudioConfig audioInput = AudioConfig.fromWavFileInput(tmpFile.getAbsolutePath());
            maxRequests.acquire();
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
                        ok.set(false);
                        LOGGER.warn("NOMATCH: Speech could not be recognized with {}", evidence.getPath());
                    }
                });
                
                recognizer.canceled.addEventListener((s, e) -> {
                    if (e.getReason() == CancellationReason.Error) {
                        ok.set(false);
                        LOGGER.error("Transcription of {} failed errorCode={} details={}", evidence.getPath(), e.getErrorCode(), e.getErrorDetails());
                    }
                    stopTranslationWithFileSemaphore.release();
                });
                
                recognizer.sessionStopped.addEventListener((s, e) -> {
                    stopTranslationWithFileSemaphore.release();
                });
                
                // Starts continuous recognition. Uses StopContinuousRecognitionAsync() to stop recognition.
                recognizer.startContinuousRecognitionAsync().get();
                
                // Waits for completion.
                stopTranslationWithFileSemaphore.acquire();
                
                // Stops recognition.
                recognizer.stopContinuousRecognitionAsync().get();
                
                Double finalScore = score.doubleValue() / (frags.intValue() != 0 ? frags.intValue() : 1);
                evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, finalScore.toString());
                evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, result.toString());
                
                storeTextInDb(evidence.getHash(), result.toString(), finalScore);
                
                LOGGER.debug("MS Transcript of {}: {}", evidence.getPath(), result.toString());
                
            } catch (Exception ex) {
                LOGGER.error("Error transcribing {} {}", evidence.getPath(), ex.toString());
                LOGGER.warn("", ex);
                
            }finally {
                maxRequests.release();
            }
        }
        
        if(wavFile != null) {
            wavFile.delete();
        }
        
    }
    
}
