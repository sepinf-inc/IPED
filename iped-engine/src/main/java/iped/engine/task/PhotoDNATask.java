package iped.engine.task;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.dpf.sepinf.photodna.api.PhotoDNA;
import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.PhotoDNAConfig;

public class PhotoDNATask extends AbstractTask {

    private Logger LOGGER = LoggerFactory.getLogger(PhotoDNATask.class);

    public static final String PDNA_NOT_FOUND_MSG = "Optional photoDNA lib not found in plugins folder. If you are law enforcement, ask iped@pf.gov.br";

    public static final int HASH_SIZE = 144;

    public static final String PHOTO_DNA = "photoDNA";

    private static AtomicBoolean warned = new AtomicBoolean();

    private PhotoDNAConfig pdnaConfig;

    private PhotoDNA photodna;

    public List<Configurable<?>> getConfigurables() {
        PhotoDNAConfig result = ConfigurationManager.get().findObject(PhotoDNAConfig.class);
        if(result == null) {
            result = new PhotoDNAConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        pdnaConfig = configurationManager.findObject(PhotoDNAConfig.class);

        if (!pdnaConfig.isEnabled())
            return;

        try {
            Class<?> c = Class.forName("br.dpf.sepinf.photodna.PhotoDNA");
            photodna = (PhotoDNA) c.getDeclaredConstructor().newInstance();

        } catch (ClassNotFoundException e) {
            pdnaConfig.setEnabled(false);
            if (!warned.getAndSet(true)) {
                LOGGER.error(PDNA_NOT_FOUND_MSG);
            }
        }

    }

    @Override
    public boolean isEnabled() {
        return pdnaConfig.isEnabled();
    }

    @Override
    public void finish() throws Exception {

    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if (!evidence.isToAddToCase())
            return;

        byte[] thumb = evidence.getThumb(); 
        if (thumb == null || !evidence.getMediaType().getType().equals("image"))
            return;

        if (evidence.getLength() != null && evidence.getLength() < pdnaConfig.getMinFileSize())
            return;

        if (pdnaConfig.isUseThumbnail() && thumb.length == 0)
            return;

        if (pdnaConfig.isSkipHashDBFiles() && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null)
            return;

        byte[] hash;
        try (InputStream is = pdnaConfig.isUseThumbnail() ? new ByteArrayInputStream(thumb)
                : evidence.getBufferedInputStream()) {

            photodna.reset();
            hash = photodna.computePhotoDNA(is);
            String hashStr = new String(Hex.encodeHex(hash, false));
            evidence.setExtraAttribute(PHOTO_DNA, hashStr);

        } catch (Throwable e) {
            LOGGER.info("Error computing photoDNA for " + evidence.getPath(), e);
            evidence.setExtraAttribute("photodna_exception", e.toString());
            return;
        }

    }

}
