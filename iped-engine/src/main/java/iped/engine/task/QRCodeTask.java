package iped.engine.task;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.EnableTaskProperty;
import iped.parsers.util.MetadataUtil;

public class QRCodeTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(QRCodeTask.class);
    /**
     * Static object to control (synchronize) initialization process (it should run
     * only once for all threads).
     */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    /**
     * Static object to control (synchronize) the termination of the task.
     */
    private static final AtomicBoolean finished = new AtomicBoolean(false);

    private static final String ENABLE_PARAM = "enableQRCode"; //$NON-NLS-1$

    private static final String QRCODE_TEXT = "QRCodeText";
    private static final String QRCODE_HEX = "QRCodeHex";
    // private static final String QRCODE_TYPE = "QRCodeType";
    private static final String QRCODE_POINTS = "QRCodePoints";

    private static final AtomicLong totalImagesProcessed = new AtomicLong();
    private static final AtomicLong totalImagesFailed = new AtomicLong();
    private static final AtomicLong totalQRCodesFound = new AtomicLong();
    private static final AtomicLong totalTime = new AtomicLong();

    private static final Map<DecodeHintType, Object> hints = new HashMap<>();

    private static boolean taskEnabled = false;

    @Override
    public boolean isEnabled() {
        return taskEnabled;
    }

    public static void setEnabled(boolean enabled) {
        taskEnabled = enabled;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new EnableTaskProperty(ENABLE_PARAM));
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        /**
         * initialization process (it should run only once for all threads).
         */
        synchronized (init) {
            if (!init.getAndSet(true)) {
                taskEnabled = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
                hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
                if (!taskEnabled) {
                    logger.info("Task disabled."); //$NON-NLS-1$
                } else {
                    logger.info("Task enabled."); //$NON-NLS-1$
                }
            }

        }

    }

    @Override
    public void finish() throws Exception {
        synchronized (finished) {
            if (!finished.getAndSet(true)) {
                long totalImages = totalImagesProcessed.longValue() + totalImagesFailed.longValue();
                if (totalImages != 0) {
                    logger.info("Total images processed: " + totalImagesProcessed); //$NON-NLS-1$
                    logger.info("Total images not processed: " + totalImagesFailed); //$NON-NLS-1$
                    logger.info("QRCodes Found: " + totalQRCodesFound.longValue()); //$NON-NLS-1$
                    logger.info("Average image processing time (ms/image): " + (totalTime.longValue() / totalImages)); //$NON-NLS-1$
                }
            }

        }

    }

    public static String getHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%1$02X", b)); //$NON-NLS-1$
        }
        return result.toString();
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        if (!taskEnabled || !evidence.isToAddToCase() || evidence.getHash() == null
                || !MetadataUtil.isImageType(evidence.getMediaType())) {
            return;
        }
        BufferedImage img = null;
        long t = System.currentTimeMillis();
        try (BufferedInputStream in = evidence.getBufferedInputStream()) {
            img = ImageIO.read(in);
        } catch (Throwable e) {
            logger.debug("Cannot read image file {} ({} bytes): {}", evidence.getPath(), evidence.getLength(),
                    e.toString());
            totalImagesFailed.incrementAndGet();
        }

        if (img != null && img.getWidth() > 80 && img.getHeight() > 80) {
            try {

                BinaryBitmap binaryBitmap = new BinaryBitmap(
                        new HybridBinarizer(new BufferedImageLuminanceSource(img)));
                QRCodeReader reader = new QRCodeReader();
                Result[] results = { reader.decode(binaryBitmap) };
                List<String> texts = new ArrayList<>(), types = new ArrayList<>(), rawBytes = new ArrayList<>(),
                        points = new ArrayList<>();
                for (Result rs : results) {

                    if (rs.getText() != null && rs.getText().length() > 0) {
                        String text = rs.getText();
                        texts.add(text);
                    }

                    if (rs.getRawBytes() != null && rs.getRawBytes().length > 0) {
                        String bytes = getHex(rs.getRawBytes());
                        rawBytes.add(bytes);
                    }

                    if (rs.getBarcodeFormat() != null) {
                        String type = rs.getBarcodeFormat().toString();
                        types.add(type);
                    }

                    List<String> coords = new ArrayList<>();
                    for (ResultPoint p : rs.getResultPoints()) {
                        coords.add("(" + p.getX() + "," + p.getY() + ")");
                    }
                    if (!coords.isEmpty()) {
                        points.add(coords.toString());
                    }
                }

                if (results.length > 0) {
                    evidence.setExtraAttribute(QRCODE_TEXT, texts);
                    evidence.setExtraAttribute(QRCODE_HEX, rawBytes);
                    // for now we are just detecting qrcodes because barcode
                    // detection doesn't work with multiple qrcodes detection
                    // evidence.setExtraAttribute(QRCODE_TYPE, types);
                    evidence.setExtraAttribute(QRCODE_POINTS, points);
                    
                    logger.info("Found {} qrcode(s) in file {} ({} bytes)", results.length, evidence.getPath(),
                            evidence.getLength());

                    totalQRCodesFound.addAndGet(results.length);
                }

            } catch (Throwable e) {
                logger.debug("Error searching for qrcodes in file {} ({} bytes): {}", evidence.getPath(),
                        evidence.getLength(), e.toString());
            }
            totalImagesProcessed.incrementAndGet();
        }
        t = System.currentTimeMillis() - t;
        totalTime.addAndGet(t);

    }

}
