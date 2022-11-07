package iped.engine.task;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.engine.task.carver.BaseCarveTask;
import iped.parsers.standard.RawStringParser;
import iped.utils.RandomFilterInputStream;

public class EntropyTask extends AbstractTask {

    public static final String COMPRESS_RATIO = RawStringParser.COMPRESS_RATIO;

    public static final String ENABLE_PARAM = "entropyTest"; //$NON-NLS-1$

    private byte[] buf = new byte[64 * 1024];

    private boolean enableOption;

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        enableOption = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        EnableTaskProperty result = ConfigurationManager.get().findObject(EnableTaskProperty.class);
        if(result == null) {
            result = new EnableTaskProperty(ENABLE_PARAM);
        }
        return Arrays.asList(result);
    }

    @Override
    public boolean isEnabled() {
        return enableOption;
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isEnabled() || !evidence.isToAddToCase())
            return;

        if (evidence.getMediaType().equals(BaseCarveTask.UNALLOCATED_MIMETYPE)
                || Boolean.TRUE.equals(evidence.getExtraAttribute(ThumbTask.HAS_THUMB)))
            return;

        try (RandomFilterInputStream rfis = new RandomFilterInputStream(evidence.getBufferedInputStream())) {

            while (rfis.read(buf) != -1)
                ;
            Double compression = rfis.getCompressRatio();
            if (compression != null)
                evidence.setExtraAttribute(COMPRESS_RATIO, compression);

        } catch (IOException e) {
            // ignore
        }

        /*
         * Deflater compressor = new Deflater(Deflater.BEST_SPEED); byte[] buf = new
         * byte[64 * 1024]; byte[] out = new byte[64 * 1024]; compressor.reset();
         * try(InputStream is = evidence.getBufferedInputStream()){ int len = 0;
         * while((len = is.read(buf))!= -1){ compressor.setInput(buf, 0, len); do{
         * compressor.deflate(out); }while(!compressor.needsInput()); }
         * compressor.finish(); compressor.deflate(out);
         * 
         * float ratio = (float)compressor.getBytesWritten()/compressor.getBytesRead();
         * 
         * evidence.setExtraAttribute("compressRatioTask", ratio);
         * 
         * }catch(Exception e){ //ignore }
         */
    }

}
