package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import dpf.sp.gpinf.indexer.config.AdvancedIPEDConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.util.RandomFilterInputStream;
import iped3.IItem;

public class EntropyTask extends AbstractTask {

    private static final String COMPRESS_RATIO = RawStringParser.COMPRESS_RATIO;

    byte[] buf = new byte[64 * 1024];

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isEnabled() {
        AdvancedIPEDConfig advancedConfig = (AdvancedIPEDConfig) ConfigurationManager.getInstance()
                .findObjects(AdvancedIPEDConfig.class).iterator().next();
        return advancedConfig.isEntropyTest();
    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!isEnabled())
            return;

        String ratio = evidence.getMetadata().get(COMPRESS_RATIO);
        if (ratio != null) {
            evidence.getMetadata().remove(COMPRESS_RATIO);
            evidence.setExtraAttribute(COMPRESS_RATIO, Double.valueOf(ratio));
            return;
        }

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
         * try(InputStream is = evidence.getBufferedStream()){ int len = 0; while((len =
         * is.read(buf))!= -1){ compressor.setInput(buf, 0, len); do{
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
