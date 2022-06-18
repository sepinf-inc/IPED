package iped.engine.task.transcript;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.SynchronousMode;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.task.AbstractTask;
import iped.properties.ExtraProperties;
import iped.util.IOUtil;

public abstract class AbstractTranscriptTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractTranscriptTask.class);

    private static final String TEST_FFMPEG = "ffmpeg -version";

    protected static final MediaType wav = MediaType.audio("vnd.wave");

    private static final String TEXT_STORAGE = "text/transcriptions.db"; //$NON-NLS-1$

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS transcriptions(id TEXT PRIMARY KEY, text TEXT, score REAL);"; //$NON-NLS-1$

    private static final String INSERT_DATA = "INSERT INTO transcriptions(id, text, score) VALUES(?,?,?) ON CONFLICT(id) DO NOTHING"; //$NON-NLS-1$

    private static final String SELECT_EXACT = "SELECT text, score FROM transcriptions WHERE id=?;"; //$NON-NLS-1$

    protected static final int MIN_TIMEOUT = 10;

    protected static final int WAV_BYTES_PER_SEC = 16000 * 2; // 16khz sample rate and 16bits per sample

    private static boolean ffmpegTested = false;

    private static boolean ffmpegDetected = false;

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

    @Override
    public boolean isEnabled() {
        return transcriptConfig.isEnabled();
    }

    protected boolean isToProcess(IItem evidence) {

        if (evidence.getLength() == null || evidence.getLength() == 0 || !evidence.isToAddToCase()
                || evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null) {
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

    protected boolean isFfmpegOk() {
        if (!ffmpegTested) {
            try {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(TEST_FFMPEG.split(" "));
                pb.redirectErrorStream(true);
                Process p = pb.start();
                IOUtil.loadInputStream(p.getInputStream());
                int exit = p.waitFor();
                if (exit == 0) {
                    ffmpegDetected = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (!ffmpegDetected) {
                LOGGER.error("Error testing ffmpeg, that could hurt transcription. Is it on path?");
            }
            ffmpegTested = true;
        }
        return ffmpegDetected;
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

        if (conn == null && transcriptConfig.isEnabled()) {
            createConnection();
        }

        // testFfmpeg();

    }

    protected File getWavFile(IItem evidence) throws IOException, InterruptedException {
        File input = evidence.getTempFile();
        File tmpFile = File.createTempFile("iped", ".wav");
        Files.delete(tmpFile.toPath());
        ProcessBuilder pb = new ProcessBuilder();
        String[] cmd = transcriptConfig.getConvertCmd().split(" ");
        if (SystemUtils.IS_OS_WINDOWS) {
            LocalConfig localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
            String mplayerWin = localConfig.getMplayerWinPath();
            cmd[0] = cmd[0].replace("mplayer", Configuration.getInstance().appRoot + "/" + mplayerWin);
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
        byte[] out = IOUtil.loadInputStream(p.getInputStream());
        int exit = p.waitFor();
        if (exit != 0) {
            tmpFile.delete();
            LOGGER.warn("Error converting to wav {} {}", evidence.getPath(), new String(out, StandardCharsets.UTF_8));
            return null;
        } else {
            LOGGER.debug(new String(out, StandardCharsets.UTF_8));
            if (!tmpFile.exists()) {
                LOGGER.warn("Conversion to wav failed, no wav generated: {} ", evidence.getPath());
                return null;
            }
            if (tmpFile.length() == 0) {
                tmpFile.delete();
                LOGGER.warn("Conversion to wav failed, empty wav generated: {} ", evidence.getPath());
                return null;
            }
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
            LOGGER.info(
                    "Average transcription time (ms/audio): " + (transcriptionTime.longValue() / totTranscriptions));
            transcriptionSuccess.set(0);
            transcriptionFail.set(0);
        }
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

        long t = System.currentTimeMillis();
        File tempWav = getWavFile(evidence);
        wavTime.addAndGet(System.currentTimeMillis() - t);
        if (tempWav == null) {
            wavFail.incrementAndGet();
            return;
        }
        wavSuccess.incrementAndGet();

        try {
            this.evidence = evidence;
            t = System.currentTimeMillis();
            TextAndScore result = transcribeWav(tempWav);
            transcriptionTime.addAndGet(System.currentTimeMillis() - t);
            if (result != null) {
                evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(result.score));
                evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, result.text);
                storeTextInDb(evidence.getHash(), result.text, result.score);
                transcriptionSuccess.incrementAndGet();
                if (result.text != null) {
                    transcriptionChars.addAndGet(result.text.length());
                }
            } else {
                transcriptionFail.incrementAndGet();
            }

        } finally {
            if (tempWav != null) {
                tempWav.delete();
            }
        }

    }

    protected abstract TextAndScore transcribeWav(File tmpFile) throws Exception;

}
