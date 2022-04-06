package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.ExternalImageConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;
import dpf.sp.gpinf.indexer.util.IconUtil;
import dpf.sp.gpinf.indexer.util.ImageMetadataUtil;
import dpf.sp.gpinf.indexer.util.ImageUtil;
import gpinf.led.ImageViewPanel;
import iped3.IItemBase;
import iped3.io.IStreamSource;
import iped3.io.SeekableInputStream;
import iped3.util.MediaTypes;

public class ImageViewer extends AbstractViewer implements ActionListener {

    private static Logger LOGGER = LoggerFactory.getLogger(ImageViewer.class);

    protected ImageViewPanel imagePanel;
    protected JToolBar toolBar;
    private JSlider sliderBrightness;

    private ExternalImageConverter externalImageConverter;

    public static final String HIGHLIGHT_LOCATION = ImageViewer.class.getName() + "HighlightLocation:";

    private static final String actionRotLeft = "rotate-left";
    private static final String actionRotRight = "rotate-right";
    private static final String actionZoomIn = "zoom-in";
    private static final String actionZoomOut = "zoom-out";
    private static final String actionFitWidth = "fit-width";
    private static final String actionFitWindow = "fit-window";
    private static final String actionCopyImage = "copy-image";

    private static final int maxDim = 2400;
    
    volatile protected BufferedImage image;
    volatile protected int rotation;

    public ImageViewer() {
        this(0);
    }

    public ImageViewer(int initialFitMode) {
        super(new BorderLayout());
        isToolbarVisible = true;
        imagePanel = new ImageViewPanel(initialFitMode);
        createToolBar();
        getPanel().add(imagePanel, BorderLayout.CENTER);
        getPanel().add(toolBar, BorderLayout.NORTH);
    }

    @Override
    public String getName() {
        return "Image"; //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.startsWith("image"); //$NON-NLS-1$
    }

    protected void cleanState(boolean cleanRotation) {
        image = null;
        sliderBrightness.setValue(0);
        if (cleanRotation) {
            rotation = 0;
        }
    }
    
    @Override
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {
        cleanState(true);
        if (content != null) {
            InputStream in = null;
            try {
                in = new BufferedInputStream(content.getSeekableInputStream());
                // needed for embedded jbig2
                String mimeType = content instanceof IItemBase
                        ? MediaTypes.getMimeTypeIfJBIG2((IItemBase) content)
                        : null;
                image = ImageUtil.getSubSampledImage(in, maxDim, maxDim, mimeType);

                if (image == null) {
                    IOUtil.closeQuietly(in);
                    SeekableInputStream sis = content.getSeekableInputStream();
                    in = new BufferedInputStream(sis);
                    image = externalImageConverter.getImage(in, maxDim, true, sis.size());
                }
                if (image == null) {
                    IOUtil.closeQuietly(in);
                    in = new BufferedInputStream(content.getSeekableInputStream());
                    image = ImageMetadataUtil.getThumb(in);
                }
                if (image != null) {
                    IOUtil.closeQuietly(in);
                    in = new BufferedInputStream(content.getSeekableInputStream());
                    int orientation = ImageMetadataUtil.getOrientation(in);
                    boolean isVideo = false;
                    if (orientation > 0) {
                        image = ImageUtil.rotate(image, orientation);
                    } else {
                        String videoComment = ImageUtil.readJpegMetaDataComment(content.getSeekableInputStream());
                        if (videoComment != null && videoComment.startsWith("Frames=")) {
                            isVideo = true;
                            if (!highlightTerms.isEmpty()) {
                                drawRectangles(image, getZoom(content, image), highlightTerms);
                            }
                            image = ImageUtil.getBestFramesFit(image, videoComment, imagePanel.getWidth(),
                                    imagePanel.getHeight());
                        }
                    }
                    if (!isVideo && !highlightTerms.isEmpty()) {
                      drawRectangles(image, getZoom(content, image), highlightTerms);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                IOUtil.closeQuietly(in);
            }
        }
        toolBar.setVisible(image != null && isToolbarVisible());
        updatePanel(image);
    }

    private double getZoom(IStreamSource content, BufferedImage img) throws IOException {
        Dimension d = null;
        try (InputStream is = content.getSeekableInputStream()) {
            d = ImageUtil.getImageFileDimension(is);
        }
        if (d == null) {
            try (InputStream is = content.getSeekableInputStream()) {
                d = externalImageConverter.getDimension(is);
            }
        }
        if (d == null)
            return 0;
        int originalDimension = Math.max(d.width, d.height);
        int displayedDimension = Math.max(img.getWidth(), img.getHeight());
        if (originalDimension == 0 || displayedDimension == 0)
            return 0;
        return displayedDimension / (double) originalDimension;
    }

    private void drawRectangles(BufferedImage img, double zoom, Set<String> highlights) {
        if (zoom <= 0) 
            return;
        Graphics2D graph = img.createGraphics();
        graph.setColor(Color.RED);
        graph.setStroke(new BasicStroke(4));
        for (String str : highlights) {
            if (str.startsWith(HIGHLIGHT_LOCATION + "[") && str.endsWith("]")) {
                String[] vals = str.substring(HIGHLIGHT_LOCATION.length() + 1, str.length() - 1).split(", ");
                int top = (int) Math.round(Integer.parseInt(vals[0]) * zoom);
                int right = (int) Math.round(Integer.parseInt(vals[1]) * zoom);
                int bottom = (int) Math.round(Integer.parseInt(vals[2]) * zoom);
                int left = (int) Math.round(Integer.parseInt(vals[3]) * zoom);
                graph.drawRect(left, top, right - left, bottom - top);
            }
        }
        graph.dispose();

    }

    protected void updatePanel(final BufferedImage img) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                imagePanel.setImage(img);
            }
        });
    }

    protected void updateBrightness(float factor) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                imagePanel.adjustBrightness(factor);
            }
        });
    }

    @Override
    public void init() {
        externalImageConverter = new ExternalImageConverter();
        externalImageConverter.setNumThreads(Math.max(Runtime.getRuntime().availableProcessors() / 2, 1));
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
            externalImageConverter.close();
        } catch (IOException e) {
            LOGGER.warn("Error closing " + externalImageConverter, e);
        }
    }

    @Override
    public void scrollToNextHit(boolean forward) {
    }

    @Override
    public int getHitsSupported() {
        return -1;
    }

    @Override
    public void setToolbarVisible(boolean isVisible) {
        super.setToolbarVisible(isVisible);
        toolBar.setVisible(isVisible);
    }

    @Override
    public int getToolbarSupported() {
        return 1;
    }

    private void createToolBar() {
        toolBar = new JToolBar();
        toolBar.setFloatable(false);
        Icon iconSeparator = IconUtil.getIcon("separator", resPath, 24);

        toolBar.add(new JLabel(iconSeparator));
        createToolBarButton(actionRotLeft);
        createToolBarButton(actionRotRight);
        toolBar.add(new JLabel(iconSeparator));
        createToolBarButton(actionZoomIn);
        createToolBarButton(actionZoomOut);
        createToolBarButton(actionFitWindow);
        createToolBarButton(actionFitWidth);
        toolBar.add(new JLabel(iconSeparator));

        sliderBrightness = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 0);
        sliderBrightness.setPreferredSize(new Dimension(60, 16));
        sliderBrightness.setMinimumSize(new Dimension(20, 16));
        sliderBrightness.setOpaque(false);
        sliderBrightness.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (image != null) {
                    int factor = sliderBrightness.getValue();
                    updateBrightness(factor);
                }
            }
        });
        Icon icon = IconUtil.getIcon("bright", resPath, 12);
        JPanel panelAux = new JPanel() {
            private static final long serialVersionUID = 8147197693022129080L;

            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                icon.paintIcon(this, g, (getWidth() - icon.getIconWidth()) / 2, 0);
            }
        };
        panelAux.setOpaque(false);
        panelAux.add(sliderBrightness);
        panelAux.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
        toolBar.add(panelAux);
        toolBar.add(new JLabel(iconSeparator));

        JButton butCopyImage = createToolBarButton(actionCopyImage);
        butCopyImage.setToolTipText(Messages.getString("ImageViewer.Copy"));

        toolBar.add(new JLabel(iconSeparator));
    }

    protected JButton createToolBarButton(String action) {
        JButton but = new JButton(IconUtil.getIcon(action, resPath, 24));
        but.setActionCommand(action);
        but.setOpaque(false);
        toolBar.add(but);
        but.setFocusPainted(false);
        but.setFocusable(false);
        but.addActionListener(this);
        return but;
    }

    public synchronized void actionPerformed(ActionEvent e) {
        if (image == null) {
            return;
        }
        String cmd = e.getActionCommand();
        if (cmd.equals(actionRotLeft)) {
            if (--rotation < 0)
                rotation = 3;
            updateRotation();
        } else if (cmd.equals(actionRotRight)) {
            if (++rotation > 3)
                rotation = 0;
            updateRotation();
        } else if (cmd.equals(actionZoomIn)) {
            imagePanel.changeZoom(1.2, null);
        } else if (cmd.equals(actionZoomOut)) {
            imagePanel.changeZoom(1 / 1.2, null);
        } else if (cmd.equals(actionFitWindow)) {
            imagePanel.fitToWindow();
        } else if (cmd.equals(actionFitWidth)) {
            imagePanel.fitToWidth();
        } else if (cmd.equals(actionCopyImage)) {
            copyScreen();
        }
    }

    private void updateRotation() {
        BufferedImage img = image;
        updatePanel(ImageUtil.rotatePos(img, rotation));
        int factor = sliderBrightness.getValue();
        if (factor != 0)
            updateBrightness(factor);
    }
}