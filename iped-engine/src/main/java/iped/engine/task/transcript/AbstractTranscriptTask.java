package iped.engine.task.transcript;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.Externalizable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.NoRouteToHostException;
import java.nio.file.Files;
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
import org.ehcache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.data.IItem;
import iped.engine.cache.CacheProvider;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.CacheConfig;
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

    protected static final int TIMEOUT_PER_MB = 100;

    protected static int MIN_TIMEOUT = 180;

    protected static final int WAV_BYTES_PER_SEC = 16000 * 2; // 16khz sample rate and 16bits per sample

    private static final int MAX_WAV_TIME = 59;
    private static final int MAX_WAV_SIZE = 16000 * 2 * MAX_WAV_TIME;

    protected AudioTranscriptConfig transcriptConfig;

    // Cache
    private static final String CACHE_ALIAS = "AudioTranscriptionCache";
    private Cache<String, TextAndScore> cache;


    // Variables to store some statistics
    private static final AtomicLong wavTime = new AtomicLong();
    private static final AtomicLong transcriptionTime = new AtomicLong();
    private static final AtomicInteger wavSuccess = new AtomicInteger();
    private static final AtomicInteger wavFail = new AtomicInteger();
    private static final AtomicInteger transcriptionSuccess = new AtomicInteger();
    private static final AtomicInteger transcriptionFail = new AtomicInteger();
    private static final AtomicLong transcriptionChars = new AtomicLong();

    protected IItem evidence;


    @Override
    public boolean isEnabled() {
        return transcriptConfig.isEnabled();
    }

    protected boolean isToProcess(IItem evidence) {

        if (evidence.getLength() == null || evidence.getLength() == 0 || !evidence.isToAddToCase() || evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null) {
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

    public static class TextAndScore implements Externalizable {
        String text;
        double score;

        public TextAndScore() {
        }

        public TextAndScore(String text, double score) {
            this.text = text;
            this.score = score;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeUTF(text);
            out.writeDouble(score);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            text = in.readUTF();
            score = in.readDouble();
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

        // clear default config service address in output
        this.transcriptConfig.clearTranscriptionServiceAddress(output);
        // clear profile config service address in output
        this.transcriptConfig.clearTranscriptionServiceAddress(new File(output, "profile"));

        CacheConfig cacheConfig = configurationManager.findObject(CacheConfig.class);
        CacheProvider cacheProvider = CacheProvider.getInstance(cacheConfig);

        this.cache = cacheProvider.getOrCreateCache(CACHE_ALIAS, String.class, TextAndScore.class);
    }

    public static TextAndScore transcribeWavBreaking(File tmpFile, String itemPath, Function<File, TextAndScore> transcribeWavPart) throws Exception {
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
        return getAudioSplits(inFile, itemPath, MAX_WAV_TIME);
    }

    protected static Collection<File> getAudioSplits(File inFile, String itemPath, int max_wave_time) {
        List<File> splitFiles = new ArrayList<File>();
        AudioInputStream aIn = null;
        AudioInputStream aOut = null;
        try {
            File outFile = File.createTempFile("iped", "");
            outFile.delete();
            aIn = AudioSystem.getAudioInputStream(inFile);
            int bytesPerFrame = aIn.getFormat().getFrameSize();
            int framesPerPart = Math.round(aIn.getFormat().getFrameRate() * max_wave_time);
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

    protected File getTempFileToTranscript(IItem evidence, TemporaryResources tmp) throws IOException, InterruptedException {
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

        if (evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null && evidence.getMetadata().get(ExtraProperties.CONFIDENCE_ATTR) != null)
            return;

        TextAndScore prevResult = cache.get(evidence.getHash());
        if (prevResult != null) {
            evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(prevResult.score));
            evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, prevResult.text);
            return;
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

        try {
            this.evidence = evidence;
            long t = System.currentTimeMillis();
            TextAndScore result = transcribeAudio(tmpFile);
            transcriptionTime.addAndGet(System.currentTimeMillis() - t);
            if (result != null) {
                evidence.getMetadata().set(ExtraProperties.CONFIDENCE_ATTR, Double.toString(result.score));
                evidence.getMetadata().set(ExtraProperties.TRANSCRIPT_ATTR, result.text);
                cache.put(evidence.getHash(), result);
                transcriptionSuccess.incrementAndGet();
                if (result.text != null) {
                    transcriptionChars.addAndGet(result.text.length());
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
            tmp.close();
        }

    }

    protected abstract TextAndScore transcribeAudio(File tmpFile) throws Exception;

}
