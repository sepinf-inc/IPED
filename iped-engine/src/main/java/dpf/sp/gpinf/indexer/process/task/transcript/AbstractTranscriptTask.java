package dpf.sp.gpinf.indexer.process.task.transcript;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.SynchronousMode;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.process.task.VideoThumbTask;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.IItem;
import iped3.util.ExtraProperties;

public abstract class AbstractTranscriptTask extends AbstractTask {

    private static Logger LOGGER = LoggerFactory.getLogger(AbstractTranscriptTask.class);

    protected static final String ENABLE_KEY = "enableAudioTranscription";

    static final String CONF_FILE = "AudioTranscriptConfig.txt";

    private static final String TIMEOUT_KEY = "timeout";

    private static final String LANG_KEY = "language";

    private static final String MIMES_KEY = "mimesToProcess";

    private static final String CONVERT_CMD_KEY = "convertCommand";

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

    protected UTF8Properties props = new UTF8Properties();

    protected List<String> languages = new ArrayList<>();

    protected int timeoutPerSec;

    private List<String> mimesToProcess = new ArrayList<>();

    private String convertCmd;

    private Connection conn;

    protected boolean isEnabled = false;

    protected IItem evidence;

    @Override
    public boolean isEnabled() {
        return this.isEnabled;
    }

    protected boolean isToProcess(IItem evidence) {

        if (evidence.getLength() == null || evidence.getLength() == 0 || !evidence.isToAddToCase()
                || evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null) {
            return false;
        }
        boolean supported = false;
        for (String mime : mimesToProcess) {
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
        File db = new File(output, TEXT_STORAGE);
        db.getParentFile().mkdirs();
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setSynchronous(SynchronousMode.OFF);
            config.setBusyTimeout(3600000);
            conn = config.createConnection("jdbc:sqlite:" + db.getAbsolutePath());

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(CREATE_TABLE);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected class TextAndScore {
        String text;
        double score;
    }

    private TextAndScore getTextFromDb(String id) throws IOException {
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
    public void init(Properties confParams, File confDir) throws Exception {

        String enabled = confParams.getProperty(ENABLE_KEY);
        if (enabled != null) {
            isEnabled = Boolean.valueOf(enabled.trim());
        }
        if (!isEnabled) {
            return;
        }

        props.load(new File(confDir, CONF_FILE));

        String langs = props.getProperty(LANG_KEY);
        for (String lang : langs.split(";")) {
            languages.add(lang.trim());
        }

        convertCmd = props.getProperty(CONVERT_CMD_KEY).trim();

        String mimes = props.getProperty(MIMES_KEY).trim();
        for (String mime : mimes.split(";")) {
            mimesToProcess.add(mime.trim());
        }

        timeoutPerSec = Integer.valueOf(props.getProperty(TIMEOUT_KEY).trim());

        if (conn == null) {
            createConnection();
        }

        // testFfmpeg();

    }

    protected File getWavFile(IItem evidence) throws IOException, InterruptedException {
        File input = evidence.getTempFile();
        File tmpFile = File.createTempFile("iped", ".wav");
        Files.delete(tmpFile.toPath());
        ProcessBuilder pb = new ProcessBuilder();
        String[] cmd = convertCmd.split(" ");
        if (SystemUtils.IS_OS_WINDOWS) {
            cmd[0] = cmd[0].replace("mplayer", Configuration.getInstance().appRoot + "/" + VideoThumbTask.mplayerWin);
        }
        for (int i = 0; i < cmd.length; i++) {
            if (SystemUtils.IS_OS_WINDOWS) {
                cmd[i] = cmd[i].replace("$OUTPUT", "\\\"$OUTPUT\\\"");
            }
            cmd[i] = cmd[i].replace("$INPUT", input.getAbsolutePath());
            cmd[i] = cmd[i].replace("$OUTPUT", tmpFile.getAbsolutePath());
        }
        pb.command(cmd);
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
        }
        return tmpFile;
    }

    @Override
    public void finish() throws Exception {
        if (conn != null) {
            conn.close();
            conn = null;
        }
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isToProcess(evidence)) {
            return;
        }

        TextAndScore prevResult = getTextFromDb(evidence.getHash());
        if (prevResult != null) {
            evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(prevResult.score));
            evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, prevResult.text);
            return;
        }

        File tempWav = getWavFile(evidence);
        if (tempWav == null) {
            return;
        }

        try {
            this.evidence = evidence;
            TextAndScore result = transcribeWav(tempWav);
            if (result != null) {
                evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(result.score));
                evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, result.text);
                storeTextInDb(evidence.getHash(), result.text, result.score);
            }

        } finally {
            if (tempWav != null) {
                tempWav.delete();
            }
        }

    }

    protected abstract TextAndScore transcribeWav(File tmpFile) throws Exception;

}
