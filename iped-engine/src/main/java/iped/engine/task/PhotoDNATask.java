package iped.engine.task;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.dpf.sepinf.photodna.api.PhotoDNA;
import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.PhotoDNAConfig;
import iped.engine.preview.PreviewRepositoryManager;
import iped.parsers.util.MetadataUtil;
import iped.utils.IOUtil;
import iped.utils.ImageUtil;

public class PhotoDNATask extends AbstractTask {

    private Logger LOGGER = LoggerFactory.getLogger(PhotoDNATask.class);

    public static final String PDNA_NOT_FOUND_MSG = "Optional photoDNA lib not found in plugins folder. If you are law enforcement, ask iped@pf.gov.br";

    public static final int HASH_SIZE = 144;

    public static final String PHOTO_DNA = "photoDNA";
    public static final String PHOTO_DNA_FRAMES = "photoDNAFrames";
    public static final String PHOTO_DNA_FRAMES_TEMP = "photoDNAFramesTemp";

    private static AtomicBoolean warned = new AtomicBoolean();

    private PhotoDNAConfig pdnaConfig;

    private PhotoDNA photodna;

    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new PhotoDNAConfig());
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

        if (pdnaConfig.isSkipHashDBFiles() && evidence.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) != null)
            return;

        if (evidence.getLength() != null && evidence.getLength() < pdnaConfig.getMinFileSize())
            return;

        boolean isAnimationImage = MetadataUtil.isAnimationImage(evidence);
        if (MetadataUtil.isImageType(evidence.getMediaType()) && !isAnimationImage) {
            processImage(evidence);

        } else if (MetadataUtil.isVideoType(evidence.getMediaType()) || isAnimationImage) {
            processVideo(evidence);
        }
    }

    private void processImage(IItem evidence) throws Exception {
        if (evidence.getExtraAttribute(PHOTO_DNA) != null)
            return;
        
        byte[] thumb = evidence.getThumb();
        if (thumb == null)
            return;

        if (pdnaConfig.isUseThumbnail() && thumb.length == 0)
            return;

        try (InputStream is = pdnaConfig.isUseThumbnail() ? new ByteArrayInputStream(thumb)
                : evidence.getBufferedInputStream()) {

            photodna.reset();
            byte[] hash = photodna.computePhotoDNA(is);
            String hashStr = new String(Hex.encodeHex(hash, false));
            evidence.setExtraAttribute(PHOTO_DNA, hashStr);

        } catch (Throwable e) {
            LOGGER.info("Error computing photoDNA for image " + evidence.getPath(), e);
            evidence.setExtraAttribute("photodna_exception", e.toString());
        }
    }

    private void processVideo(IItem evidence) throws Exception {
        if (evidence.getExtraAttribute(PHOTO_DNA_FRAMES) != null)
            return;

        // For videos, compute the PhotoDNA of each extracted frame image
        // (VideoThumbsTask must be enabled)
        @SuppressWarnings("unchecked")
        List<String> hashes = (List<String>) evidence.getTempAttribute(PHOTO_DNA_FRAMES_TEMP);
        if (hashes != null) {
            // Reuse PhotoDNAs calculated for video frames (extracted as subitems)

            // Create a LinkedHashSet to remove duplicates while preserving order
            Set<String> uniqueHashes = new LinkedHashSet<>(hashes);

            // Clear the original list and add the unique elements back
            hashes.clear();
            hashes.addAll(uniqueHashes);
        } else {
            File viewFile = evidence.getViewFile();
            if (viewFile != null && viewFile.exists()) {
                hashes = processVideo(evidence, viewFile);
            } else if (evidence.hasPreview()) {
                AtomicReference<List<String>> res = new AtomicReference<>();
                PreviewRepositoryManager.get(output).consumePreview(evidence, inputStream -> {
                    res.set(processVideo(evidence, inputStream));
                });
                hashes = res.get();
            }
        }
        if (hashes != null && !hashes.isEmpty()) {
            evidence.setExtraAttribute(PHOTO_DNA_FRAMES, hashes);
        }
    }

    private List<String> processVideo(IItem evidence, Object inputVideo) throws IOException {

        LinkedHashSet<String> hashes = new LinkedHashSet<>();
        List<BufferedImage> frames = ImageUtil.getFrames(inputVideo);
        if (frames != null) {
            for (BufferedImage frame : frames) {
                ByteArrayOutputStream os = null;
                ByteArrayInputStream is = null;
                try {
                    os = new ByteArrayOutputStream();
                    ImageIO.write(frame, "png", os);
                    is = new ByteArrayInputStream(os.toByteArray());
                    photodna.reset();
                    byte[] hash = photodna.computePhotoDNA(is);
                    String hashStr = new String(Hex.encodeHex(hash, false));
                    hashes.add(hashStr);
                } catch (Throwable e) {
                    LOGGER.info("Error computing photoDNA for video frame " + evidence.getPath(), e);
                    evidence.setExtraAttribute("photodna_exception", e.toString());
                } finally {
                    IOUtil.closeQuietly(is);
                    IOUtil.closeQuietly(os);
                }
            }
        }
        return new ArrayList<>(hashes);
    }
}
