package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import gpinf.led.ImageViewPanel;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped3.io.IStreamSource;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;

public class ImageViewer extends Viewer {

    private static Logger LOGGER = LoggerFactory.getLogger(ImageViewer.class);

    private ImageViewPanel imagePanel;

    private GraphicsMagicConverter graphicsMagicConverter;

    public ImageViewer() {
        super(new GridLayout());
        imagePanel = new ImageViewPanel();
        this.getPanel().add(imagePanel);

    }

    @Override
    public String getName() {
        return "Imagem"; //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.startsWith("image"); //$NON-NLS-1$
    }

    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {

        BufferedImage image = null;
        if (content != null) {
            InputStream in = null;
            try {
                in = new BufferedInputStream(content.getStream());
                image = ImageUtil.getSubSampledImage(in, 2000, 2000);

                if (image == null) {
                    IOUtil.closeQuietly(in);
                    in = new BufferedInputStream(content.getStream());
                    image = ImageUtil.getThumb(in);
                }
                if (image == null) {
                    IOUtil.closeQuietly(in);
                    in = new BufferedInputStream(content.getStream());
                    image = graphicsMagicConverter.getImage(in, 1000);
                }

                if (image != null) {
                    IOUtil.closeQuietly(in);
                    in = new BufferedInputStream(content.getStream());
                    int orientation = ImageUtil.getOrientation(in);
                    if (orientation > 0) {
                        image = ImageUtil.rotate(image, orientation);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                IOUtil.closeQuietly(in);
            }
        }

        final BufferedImage img = image;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                imagePanel.setImage(img);
            }
        });

    }

    @Override
    public void init() {
        graphicsMagicConverter = new GraphicsMagicConverter();
    }

    @Override
    public void copyScreen(Component comp) {
        BufferedImage image = imagePanel.getImage();

        TransferableImage trans = new TransferableImage(image);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(trans, trans);
    }

    @Override
    public void dispose() {
        try {
            graphicsMagicConverter.close();
        } catch (IOException e) {
            LOGGER.warn("Error closing " + graphicsMagicConverter, e);
        }

    }

    @Override
    public void scrollToNextHit(boolean forward) {
        // TODO Auto-generated method stub

    }

}
