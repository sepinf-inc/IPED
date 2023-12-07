package iped.engine.task.transcript;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.NoRouteToHostException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.SynchronousMode;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.data.IItem;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.io.TimeoutException;
import iped.engine.task.AbstractTask;
import iped.engine.task.HashDBLookupTask;
import iped.engine.task.video.VideoThumbTask;
import iped.exception.IPEDException;
import iped.properties.ExtraProperties;
import iped.utils.IOUtil;

public abstract class AbstractTranscriptTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractTranscriptTask.class);

    protected static final MediaType wav = MediaType.audio("vnd.wave");

    private static final String TEXT_STORAGE = "text/transcriptions.db"; //$NON-NLS-1$

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transcriptions(id TEXT PRIMARY KEY, text TEXT, score REAL);"; //$NON-NLS-1$

    private static final String INSERT_DATA = "INSERT INTO transcriptions(id, text, score) VALUES(?,?,?) ON CONFLICT(id) DO NOTHING"; //$NON-NLS-1$

    private static final String SELECT_EXACT = "SELECT text, score FROM transcriptions WHERE id=?;"; //$NON-NLS-1$

    protected static final int TIMEOUT_PER_MB = 100;

    protected static int MIN_TIMEOUT = 180;

    protected static final int WAV_BYTES_PER_SEC = 16000 * 2; // 16khz sample rate and 16bits per sample

    private static final int MAX_WAV_TIME = 59;
    private static final int MAX_WAV_SIZE = 16000 * 2 * MAX_WAV_TIME;

    protected AudioTranscriptConfig transcriptConfig;
    
    // Variables to store some statistics
    private static final AtomicLong wavTime = new AtomicLong();
    private static final AtomicLong transcriptionTime = new AtomicLong();
    private static final AtomicInteger wavSuccess = new AtomicInteger();
    private static final AtomicInteger wavFail = new AtomicInteger();
    private static final AtomicInteger transcriptionSuccess = new AtomicInteger();
    private static final AtomicInteger transcriptionFail = new AtomicInteger();
    private static final AtomicLong transcriptionChars = new AtomicLong();

    private Connection conn;

    protected IItem evidence;

    private boolean remoteTask = false;
    private boolean requeueHeuristic = false;
    private boolean clientTranscriptHelp = false;
    private String classNameFallBack = "";
    private int requeueRatio = 4;
    private long requeueDeltaTime = 5000;

    private static int numRemoteTasks = 1;
    private static int minRemoteTasks = 2;
    private static int maxRemoteTasks = 2;
    private static int currentRemoteTasks = 0;
    private static int queueTranscriptions = 0;
    private static int tasksCount = 0;
    private static int tasksChars = 0;
    private static int tasksTime = 0;
    private static int numWorkers = 1;
    private static int currentFallBackTasks = 0;
    private static AtomicInteger lastQueueIndex = new AtomicInteger(-1);
    private static AtomicLong lastQueueTime = new AtomicLong(-1);    

    @Override
    public boolean isEnabled() {
        return transcriptConfig.isEnabled();
    }

    protected boolean isToProcess(IItem evidence) {

        if (evidence.getLength() == null || evidence.getLength() == 0 || !evidence.isToAddToCase()
                || evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null) {
            return false;
        }
        if (transcriptConfig.getSkipKnownFiles() && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null) {
            return false;
        }

        boolean supported = false;
        for (String mime : transcriptConfig.getMimesToProcess()) {
            if (evidence.getMediaType().toString().startsWith(mime)) {
                supported = true;
                break;
            }
        }
        return supported;
    }

    private void createConnection() {
        this.conn = createConnection(output);
    }

    private Connection createConnection(File output) {
        File db = new File(output, TEXT_STORAGE);
        db.getParentFile().mkdirs();
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setSynchronous(SynchronousMode.OFF);
            config.setBusyTimeout(3600000);
            Connection conn = config.createConnection("jdbc:sqlite:" + db.getAbsolutePath());

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_TABLE);
            }

            return conn;

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected static class TextAndScore {
        String text;
        double score;
    }

    private TextAndScore getTextFromDb(String id) throws IOException {
        TextAndScore result = getTextFromDb(this.conn, id);
        return result;
    }

    private TextAndScore getTextFromDb(Connection conn, String id) throws IOException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_EXACT)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
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
        try (PreparedStatement ps = conn.prepareStatement(INSERT_DATA)) {
            ps.setString(1, id);
            ps.setString(2, text);
            ps.setDouble(3, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new AudioTranscriptConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        transcriptConfig = configurationManager.findObject(AudioTranscriptConfig.class);
        MIN_TIMEOUT = transcriptConfig.getMinTimeout();

        numWorkers = this.worker.manager.getNumWorkers();
        numRemoteTasks = (((int)numWorkers/2)>0)?(int)numWorkers/2:1;
        minRemoteTasks = (numRemoteTasks>2)?2:1; // at leat 2 workers for remote tasks
        maxRemoteTasks = (numWorkers>1)?numWorkers-1:1; // at leat 1 worker for client usage
        tasksCount = numRemoteTasks;

        // clear default config service address in output
        this.transcriptConfig.clearTranscriptionServiceAddress(output);
        // clear profile config service address in output
        this.transcriptConfig.clearTranscriptionServiceAddress(new File(output, "profile"));

        if (conn == null && transcriptConfig.isEnabled()) {
            createConnection();
        }

    }

    public static TextAndScore transcribeWavBreaking(File tmpFile, String itemPath,
            Function<File, TextAndScore> transcribeWavPart) throws Exception {
        if (tmpFile.length() <= MAX_WAV_SIZE) {
            return transcribeWavPart.apply(tmpFile);
        } else {
            Collection<File> parts = getAudioSplits(tmpFile, itemPath);
            StringBuilder sb = new StringBuilder();
            double score = 0;
            for (File part : parts) {
                TextAndScore partResult = transcribeWavPart.apply(part);
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

    protected static Collection<File> getAudioSplits(File inFile, String itemPath) {
        List<File> splitFiles = new ArrayList<File>();
        AudioInputStream aIn = null;
        AudioInputStream aOut = null;
        try {
            File outFile = File.createTempFile("iped", "");
            outFile.delete();
            aIn = AudioSystem.getAudioInputStream(inFile);
            int bytesPerFrame = aIn.getFormat().getFrameSize();
            int framesPerPart = Math.round(aIn.getFormat().getFrameRate() * MAX_WAV_TIME);
            byte[] partBytes = new byte[framesPerPart * bytesPerFrame];
            int numBytesRead = 0;
            int seq = 0;
            while ((numBytesRead = aIn.readNBytes(partBytes, 0, partBytes.length)) > 0) {
                ByteArrayInputStream bais = new ByteArrayInputStream(partBytes, 0, numBytesRead);
                aOut = new AudioInputStream(bais, aIn.getFormat(), numBytesRead);
                File splitFile = new File(String.format("%s%03d.wav", outFile.getAbsolutePath(), ++seq));
                splitFiles.add(splitFile);
                AudioSystem.write(aOut, AudioFileFormat.Type.WAVE, splitFile);
                IOUtil.closeQuietly(aOut);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to split audio file: " + itemPath, e);
        } finally {
            IOUtil.closeQuietly(aOut);
            IOUtil.closeQuietly(aIn);
        }
        return splitFiles;
    }

    protected File getWavFile(File itemFile, String itemPath) throws IOException, InterruptedException {
        File input = itemFile;
        File tmpFile = File.createTempFile("iped", ".wav");
        Files.delete(tmpFile.toPath());
        ProcessBuilder pb = new ProcessBuilder();
        String[] cmd = transcriptConfig.getConvertCmd().split(" ");
        if (SystemUtils.IS_OS_WINDOWS) {
            String mplayerWin = VideoThumbTask.MPLAYER_WIN_PATH;
            String ipedRoot = System.getProperty(IConfigurationDirectory.IPED_ROOT);
            if (ipedRoot == null) {
                ipedRoot = Configuration.getInstance().appRoot;
            }
            cmd[0] = cmd[0].replace("mplayer", ipedRoot + "/" + mplayerWin);
        }
        for (int i = 0; i < cmd.length; i++) {
            cmd[i] = cmd[i].replace("$INPUT", input.getAbsolutePath());
            cmd[i] = cmd[i].replace("$OUTPUT", tmpFile.getName());
        }
        pb.command(cmd);
        if (tmpFile.getParentFile() != null) {
            pb.directory(tmpFile.getParentFile());
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        IOUtil.ignoreInputStream(p);
        long timeoutSecs = MIN_TIMEOUT / 3 + TIMEOUT_PER_MB * input.length() / (1 << 20);
        boolean finished = p.waitFor(timeoutSecs, TimeUnit.SECONDS);
        if (!finished) {
            LOGGER.warn("Timeout after {}s converting to wav: {}", timeoutSecs, itemPath);
            LOGGER.warn("Trying to kill mplayer process...");
            p.destroy();
            p.waitFor(3, TimeUnit.SECONDS);
            if (p.isAlive()) {
                LOGGER.warn("Trying to forcibly kill mplayer process...");
                p.destroyForcibly();
                p.waitFor(3, TimeUnit.SECONDS);
            }
        }
        int exit = p.exitValue();
        if (exit != 0) {
            tmpFile.delete();
            LOGGER.warn("Error converting to wav exitCode={} item={}", exit, itemPath);
            tmpFile = null;
        } else {
            if (!tmpFile.exists()) {
                LOGGER.warn("Conversion to wav failed, no wav generated: {} ", itemPath);
                tmpFile = null;
            } else if (tmpFile.length() == 0) {
                tmpFile.delete();
                LOGGER.warn("Conversion to wav failed, empty wav generated: {} ", itemPath);
                tmpFile = null;
            }
        }
        if (!finished) {
            throw new TimeoutException("Timeout while converting audio to wav.");
        }
        return tmpFile;
    }

    @Override
    public void finish() throws Exception {
        if (conn != null) {
            conn.close();
            conn = null;
        }
        
        long totWavConversions = wavSuccess.longValue() + wavFail.longValue();
        if (totWavConversions != 0) {
            LOGGER.info("Total conversions to WAV: " + totWavConversions);
            LOGGER.info("Successful conversions to WAV: " + wavSuccess.intValue());
            LOGGER.info("Failed conversions to WAV: " + wavFail.intValue());
            LOGGER.info("Average conversion to WAV time (ms/audio): " + (wavTime.longValue() / totWavConversions));
            wavSuccess.set(0);
            wavFail.set(0);
        }

        long totTranscriptions = transcriptionSuccess.longValue() + transcriptionFail.longValue();
        if (totTranscriptions != 0) {
            LOGGER.info("Total transcriptions: " + totTranscriptions);
            LOGGER.info("Successful transcriptions: " + transcriptionSuccess.intValue());
            LOGGER.info("Failed transcriptions: " + transcriptionFail.intValue());
            LOGGER.info("Total transcription output characters: " + transcriptionChars.longValue());
            LOGGER.info("Average transcription time (ms/audio): " + (transcriptionTime.longValue() / (totTranscriptions)));
            LOGGER.info("Total transcription throughput (audios/s): " + (1000 * this.worker.manager.getNumWorkers() * totTranscriptions / transcriptionTime.longValue()));
            transcriptionSuccess.set(0);
            transcriptionFail.set(0);
        }
    }

    protected File getTempFileToTranscript(IItem evidence, TemporaryResources tmp)
            throws IOException, InterruptedException {
        long t = System.currentTimeMillis();
        File tempWav = null;
        try {
            tempWav = getWavFile(evidence.getTempFile(), evidence.getPath());
        } catch (TimeoutException e) {
            evidence.setTimeOut(true);
            stats.incTimeouts();
        }
        wavTime.addAndGet(System.currentTimeMillis() - t);
        if (tempWav == null) {
            wavFail.incrementAndGet();
        } else {
            wavSuccess.incrementAndGet();
            File finalFile = tempWav;
            tmp.addResource(new Closeable() {
                @Override
                public void close() throws IOException {
                    finalFile.delete();
                }
            });
        }
        return tempWav;
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isToProcess(evidence)) {
            return;
        }

        if (evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null
                && evidence.getMetadata().get(ExtraProperties.CONFIDENCE_ATTR) != null)
            return;

        TextAndScore prevResult = getTextFromDb(evidence.getHash());
        if (prevResult != null) {
            evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(prevResult.score));
            evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, prevResult.text);
            return;
        }

        synchronized(this){
            if (this.getRequeueHeuristic() && this.isRemoteTask() && !evidence.isFallBackTask()){

                if (currentRemoteTasks >= numRemoteTasks){

                    if (!evidence.isReEnqueueItem()){
                        queueTranscriptions++;
                    }

                    int remaingQueues = this.worker.manager.getProcessingQueues().getCurrentQueueSize();

                    if (this.getClientTranscriptHelp() && remaingQueues <= (queueTranscriptions + (numWorkers*2))){
                        
                        if (currentFallBackTasks==0){
                            currentFallBackTasks++;
                            evidence.setFallBackTask(true);
                        }

                    }

                    evidence.setReEnqueueItem(true);
                    super.reEnqueueItemSpaced(evidence, numWorkers, lastQueueIndex, lastQueueTime, this.getRequeueRatio(), this.getRequeueDeltaTime());
                    return;         
  
                }else if (currentRemoteTasks < numRemoteTasks){
                    currentRemoteTasks ++;
                }
            }
        }

        try {
            evidence.getTempFile();
        } catch (IOException e) {
            LOGGER.warn("Error creating temp file {} ({} bytes) {}", evidence.getPath(), evidence.getLength(), e.toString());
            return;
        }

        TemporaryResources tmp = new TemporaryResources();
        File tmpFile = getTempFileToTranscript(evidence, tmp);
        if (tmpFile == null) {
            return;
        }

        int chars = 0;
        long time = 0;
        try {
            this.evidence = evidence;
            long t = System.currentTimeMillis();
            TextAndScore result = transcribeAudio(tmpFile);
            time = System.currentTimeMillis() - t;
            transcriptionTime.addAndGet(time);         
            if (result != null) {
                evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(result.score));
                evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, result.text);
                storeTextInDb(evidence.getHash(), result.text, result.score);
                transcriptionSuccess.incrementAndGet();
                if (result.text != null) {
                    chars = result.text.length();
                    transcriptionChars.addAndGet(chars);                
                }
            } else {
                transcriptionFail.incrementAndGet();
            }

        } catch (Exception e) {
            if (e instanceof TooManyConnectException || e instanceof IPEDException || e instanceof NoRouteToHostException) {
                throw e;
            }
            LOGGER.error("Unexpected exception while transcribing: " + evidence.getPath(), e);
        } finally {
            synchronized(this){
                if (this.getRequeueHeuristic() && this.isRemoteTask() && !evidence.isFallBackTask()){
                    if (evidence.isReEnqueueItem()){
                        queueTranscriptions--;
                    }
                    currentRemoteTasks--;
                    tasksCount--;
                    tasksChars += chars;
                    tasksTime += time;
                    if (tasksCount==0){
                        String info = "";                        
                        double speed = tasksChars;
                        speed = speed/(tasksTime/1000)/numRemoteTasks;
                        info = "Transcript "+tasksChars+" chars in "+(tasksTime/1000)+" seconds with "+numRemoteTasks+" tasks. Speed "+speed;
                        if (speed < 0.9){
                            numRemoteTasks--;
                            numRemoteTasks = (minRemoteTasks < numRemoteTasks)?numRemoteTasks:minRemoteTasks;
                            info += ". Decreasing number of tasks to "+numRemoteTasks;
                        }else if (speed > 1.1){
                            numRemoteTasks++;
                            numRemoteTasks = (maxRemoteTasks > numRemoteTasks)?numRemoteTasks:maxRemoteTasks;
                            info += ". Increasing number of tasks to "+numRemoteTasks;
                        }
                        LOGGER.info("{}", info);
                        tasksCount = numRemoteTasks;
                        tasksChars = 0;
                        tasksTime = 0;
                    }
                }else {
                    if (currentFallBackTasks > 0){
                        currentFallBackTasks--;
                    }
                }
            }            
            tmp.close();
        }

    }

    public void setRemoteTask(boolean remoteTask){
        this.remoteTask = remoteTask;
    }

    public boolean isRemoteTask(){
        return this.remoteTask;
    }

    public void setRequeueHeuristic(boolean requeueHeuristic){
        this.requeueHeuristic = requeueHeuristic;
    }
    
    public boolean getRequeueHeuristic(){
        return this.requeueHeuristic;
    }

    public void setClientTranscriptHelp(boolean clientTranscriptHelp){
        this.clientTranscriptHelp = clientTranscriptHelp;
    }

    public boolean getClientTranscriptHelp(){
        return this.clientTranscriptHelp;
    }

    public void setClassNameFallBack(String classNameFallBack){
        this.classNameFallBack = classNameFallBack;
    }

    public String getClassNameFallBack(){
        return this.classNameFallBack;
    }

    public void setRequeueRatio(int requeueRatio){
        this.requeueRatio = requeueRatio;
    }

    public int getRequeueRatio(){
        return this.requeueRatio;
    }

    public void setRequeueDeltaTime(Long requeueDeltaTime){
        this.requeueDeltaTime = requeueDeltaTime;
    }

    public Long getRequeueDeltaTime(){
        return this.requeueDeltaTime;
    }

    protected abstract TextAndScore transcribeAudio(File tmpFile) throws Exception;

}
