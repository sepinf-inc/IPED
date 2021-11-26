package dpf.sp.gpinf.indexer.process.task;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.EnableTaskProperty;
import iped3.IItem;
import macee.core.Configurable;

public class QRCodeTask extends AbstractTask {

    private static Logger logger = LoggerFactory.getLogger(QRCodeTask.class);
    /**
     * Static object to control (synchronize) initialization process (it should run
     * only once for all threads).
     */
    private static final AtomicBoolean init = new AtomicBoolean(false);

    private static final String ENABLE_PARAM = "enableQRCode"; //$NON-NLS-1$

    private static final String QRCODE_TEXT = "QRCodeText";

    private static final String QRCODE_HEX = "QRCodeHex";
    private static final String QRCODE_TYPE = "QRCodeType";

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
        // TODO Auto-generated method stub
        return Arrays.asList(new EnableTaskProperty(ENABLE_PARAM));
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        /**
         * initialization process (it should run only once for all threads).
         */
        synchronized (init) {
            if (!init.get()) {
                init.set(true);
                taskEnabled = configurationManager.getEnableTaskProperty(ENABLE_PARAM);
                if (!taskEnabled) {
                    logger.info("Task disabled."); //$NON-NLS-1$
                    init.set(true);
                    return;
                }
            }

        }

    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub

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
                || !DIETask.isImageType(evidence.getMediaType())) {
            return;
        }
        BufferedImage img = null;
        try {
            img = ImageIO.read(evidence.getBufferedStream());
        } catch (IOException e) {
            img = null;
        }

        if (img == null) {
            logger.warn("cannot read file " + evidence.getName());
            return;
        }

        try {
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));

            MultiFormatReader reader = new MultiFormatReader();
            Result rs = reader.decode(binaryBitmap);
            if (rs.getText().length() > 0) {
                evidence.setExtraAttribute(QRCODE_TEXT, rs.getText());
            }
            if (rs.getRawBytes().length > 0) {
                evidence.setExtraAttribute(QRCODE_HEX, getHex(rs.getRawBytes()));
            }
            if (rs.getBarcodeFormat() != null) {
                evidence.setExtraAttribute(QRCODE_TYPE, rs.getBarcodeFormat().toString());

            }
        } catch (NotFoundException e) {

        }

    }

}
