package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.tika.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.IItem;
import iped3.util.ExtraProperties;

public abstract class AbstractAudioTranscriptTask extends AbstractTask{
    
    private static Logger LOGGER = LoggerFactory.getLogger(AbstractAudioTranscriptTask.class);
    
    protected static final String ENABLE_KEY = "enableAudioTranscription";
    
    protected static final String CONF_FILE = "AudioTranscriptConfig.txt";
    
    private static final String LANG_KEY = "language";
    
    private static final String MIMES_KEY = "mimesToProcess";
    
    private static final String CONVERT_CMD_KEY = "convertCommand";
    
    private static final String TEST_FFMPEG = "ffmpeg -version";
    
    private static boolean ffmpegTested = false;
    
    private static boolean ffmpegDetected = false;
    
    protected List<String> languages = new ArrayList<>();
    
    private List<String> mimesToProcess = new ArrayList<>();
    
    private String convertCmd;
    
    private boolean isEnabled = false;
    
    @Override
    public boolean isEnabled() {
        return this.isEnabled;
    }
    
    protected boolean isToProcess(IItem evidence) {
        
        if (evidence.getLength() == null || evidence.getLength() == 0 || !evidence.isToAddToCase() || 
                evidence.getMetadata().get(ExtraProperties.TRANSCRIPT_ATTR) != null) {
            return false;
        }
        boolean supported = false;
        for(String mime : mimesToProcess) {
            if(evidence.getMediaType().toString().startsWith(mime)) {
                supported = true;
                break;
            }
        }
        return supported;
    }
    
    private void testFfmpeg() {
        if(!ffmpegTested) {
            try {
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(TEST_FFMPEG.split(" "));
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

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        String enabled = confParams.getProperty(ENABLE_KEY);
        if(enabled != null) {
            isEnabled = Boolean.valueOf(enabled.trim());
        }
        
        UTF8Properties props = new UTF8Properties();
        props.load(new File(confDir, CONF_FILE));
        
        String langs = props.getProperty(LANG_KEY);
        for(String lang : langs.split(";")) {
            languages.add(lang.trim());
        }
        
        convertCmd = props.getProperty(CONVERT_CMD_KEY).trim();
        
        String mimes = props.getProperty(MIMES_KEY).trim();
        for(String mime : mimes.split(";")) {
            mimesToProcess.add(mime.trim());
        }
        
    }
    
    protected File getWavFile(IItem evidence) throws IOException, InterruptedException {
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
    public void finish() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected abstract void process(IItem evidence) throws Exception;
    
}
