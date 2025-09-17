package iped.viewers;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.io.IStreamSource;
import iped.io.SeekableInputStream;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.ExternalImageConverter;
import iped.utils.IOUtil;
import iped.utils.IconUtil;
import iped.utils.ImageUtil;
import iped.viewers.api.AbstractViewer;
import iped.viewers.components.ImageViewPanel;
import iped.viewers.localization.Messages;
import iped.viewers.util.ImageMetadataUtil;

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
    private static final String actionGrayScale = "gray-scale";
    private static final String actionBlur = "blur-image";
    private static final String actionHighlightFaces = "highlight-faces";

    private static final int maxDim = 2400;
    private static final int maxBlurDim = 512;
    private static final double blurIntensity = 0.02f;

    private static final Color rectColorMainFacesInTerms = new Color(255,0,0,200); // red
    private static final Color rectColorMainFacesOthers = new Color(0,255,0,200); // green
    private static final Color rectColorBack = new Color(255,255,255,50);

    volatile protected BufferedImage image;
    volatile protected BufferedImage originalImage;
    volatile protected int rotation;
    volatile protected boolean applyBlurFilter = false;
    volatile protected boolean applyGrayScale = false;
    volatile protected boolean applyHighlightFaces = false;
    volatile protected Set<String> facesLocations;
    volatile protected Dimension originalDimension;

    private JButton blurButton, grayButton, highlightFacesButton;

    private String videoComment;

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
        originalImage = null;
        facesLocations = null;
        originalDimension = null;
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
                loadDimension(content);
                if (content instanceof IItemReader) {
                    IItemReader item = (IItemReader) content;
                    // needed for embedded jbig2
                    String mimeType = MediaTypes.getMimeTypeString(item);
                    image = ImageUtil.getSubSampledImage(item, maxDim, mimeType);

                    Object faceLocationsAttr = item.getExtraAttribute(ExtraProperties.FACE_LOCATIONS);
                    if (faceLocationsAttr instanceof List) {
                        facesLocations = ((List<?>) faceLocationsAttr)
                                .stream()
                                .map(location -> HIGHLIGHT_LOCATION + location)
                                .collect(Collectors.toSet());
                    } else if (faceLocationsAttr instanceof String) {
                        facesLocations =  new HashSet<>(Collections.singleton(HIGHLIGHT_LOCATION + faceLocationsAttr));
                    }
                    if (facesLocations != null && !highlightTerms.isEmpty()) {
                        facesLocations.removeAll(highlightTerms); // to not override the highlightTerms rectangles
                    }
                }
                if (image == null) {
                    in = new BufferedInputStream(content.getSeekableInputStream());
                    image = ImageUtil.getSubSampledImage(in, maxDim);
                }
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
                    if (orientation > 0) {
                        image = ImageUtil.applyOrientation(image, orientation);
                    }

                    drawRectangles(image, highlightTerms, rectColorMainFacesInTerms);
                    originalImage = ImageUtil.cloneImage(image);

                    videoComment = ImageUtil.readJpegMetaDataComment(content.getSeekableInputStream());
                    if (!StringUtils.startsWith(videoComment, "Frames=")) {
                        videoComment = null;
                    }

                    applyHighlightFaces(false);
                }

            } catch (IOException e) {
                e.printStackTrace();

            } finally {
                IOUtil.closeQuietly(in);
            }
        }
        toolBar.setVisible(image != null && isToolbarVisible());
        BufferedImage img = image;
        if (img != null && applyBlurFilter) {
            img = applyBlur(img);
        }
        if (img != null && applyGrayScale) {
            img = applyGrayScale(img);
        }
        updatePanel(img);
    }

    private void loadDimension(IStreamSource content) throws IOException {
        try (InputStream is = content.getSeekableInputStream()) {
            originalDimension = ImageUtil.getImageFileDimension(is);
        }
        if (originalDimension == null) {
            try (InputStream is = content.getSeekableInputStream()) {
                originalDimension = externalImageConverter.getDimension(is);
            }
        }
    }

    private double getZoom(BufferedImage img) {
        if (originalDimension == null) {
            return 0;
        }
        int originalMaxDimension = Math.max(originalDimension.width, originalDimension.height);
        int displayedMaxDimension = Math.max(img.getWidth(), img.getHeight());
        if (originalMaxDimension == 0 || displayedMaxDimension == 0) {
            return 0;
        }
        return displayedMaxDimension / (double) originalMaxDimension;
    }

    private void drawRectangles(BufferedImage img, Set<String> highlights, Color rectColorMain)  {

        if (highlights == null || highlights.isEmpty()) {
            return;
        }

        double zoom = getZoom(img);
        if (zoom <= 0) {
            return;
        }

        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 0.5% of image's smallest dimension
        int strokeWidth = Math.max(2, Math.min(img.getHeight(), img.getWidth()) / 200);

        Stroke mainStroke = new BasicStroke(strokeWidth);
        Stroke backStroke = new BasicStroke(strokeWidth * 2);
        int arc = 4 * strokeWidth;

        for (String str : highlights) {
            if (str.startsWith(HIGHLIGHT_LOCATION + "[") && str.endsWith("]")) {
                String[] vals = str.substring(HIGHLIGHT_LOCATION.length() + 1, str.length() - 1).split(", ");
                double top = Integer.parseInt(vals[0]) * zoom;
                double right = Integer.parseInt(vals[1]) * zoom;
                double bottom = Integer.parseInt(vals[2]) * zoom;
                double left = Integer.parseInt(vals[3]) * zoom;

                RoundRectangle2D rc = new RoundRectangle2D.Double(left, top, right - left, bottom - top, arc, arc);

                g.setStroke(backStroke);
                g.setColor(rectColorBack);
                g.draw(rc);

                g.setStroke(mainStroke);
                g.setColor(rectColorMain);
                g.draw(rc);
            }
        }
        g.dispose();
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
        if (image.getColorModel().hasAlpha()) {
            image = ImageUtil.getOpaqueImage(image);
        }
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
            @Override
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

            @Override
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

        grayButton = createToolBarButton(actionGrayScale, true);
        grayButton.setToolTipText(Messages.getString("ImageViewer.GrayScale"));

        blurButton = createToolBarButton(actionBlur, true);
        blurButton.setToolTipText(Messages.getString("ImageViewer.Blur"));

        highlightFacesButton = createToolBarButton(actionHighlightFaces, true);
        highlightFacesButton.setToolTipText(Messages.getString("ImageViewer.HighlightFaces"));
    }

    protected JButton createToolBarButton(String action) {
        return createToolBarButton(action, false);
    }

    protected JButton createToolBarButton(String action, boolean select) {
        JButton but = new JButton(IconUtil.getIcon(action, resPath, 24));
        but.setActionCommand(action);
        but.setOpaque(false);
        toolBar.add(but);
        but.setFocusPainted(false);
        but.setFocusable(false);
        if (select) {
            but.setBorder(BorderFactory.createSoftBevelBorder(BevelBorder.LOWERED));
            but.setBorderPainted(false);
        }
        but.addActionListener(this);
        return but;
    }

    protected JButton createToolSelection(String action, boolean select) {
        JButton but = new JButton(IconUtil.getIcon(action, resPath, 24));
        but.setActionCommand(action);
        but.setOpaque(false);
        toolBar.add(but);
        but.setFocusPainted(false);
        but.setFocusable(false);
        if (select) {
            but.setBorder(BorderFactory.createSoftBevelBorder(BevelBorder.LOWERED));
            but.setBorderPainted(false);
        }
        but.addActionListener(this);
        return but;
    }

    @Override
    public synchronized void actionPerformed(ActionEvent e) {
        if (image == null) {
            return;
        }
        boolean update = false;
        String cmd = e.getActionCommand();
        if (cmd.equals(actionRotLeft)) {
            if (--rotation < 0) {
                rotation = 3;
            }
            update = true;
        } else if (cmd.equals(actionRotRight)) {
            if (++rotation > 3) {
                rotation = 0;
            }
            update = true;
        } else if (cmd.equals(actionZoomIn)) {
            imagePanel.changeZoom(1.2, null);
        } else if (cmd.equals(actionZoomOut)) {
            imagePanel.changeZoom(1 / 1.2, null);
        } else if (cmd.equals(actionFitWindow)) {
            imagePanel.fitToWindow();
        } else if (cmd.equals(actionFitWidth)) {
            imagePanel.fitToWidth();
        } else if (cmd.equals(actionGrayScale)) {
            setGrayFilter(!applyGrayScale);
        } else if (cmd.equals(actionBlur)) {
            setBlurFilter(!applyBlurFilter);
        } else if (cmd.equals(actionHighlightFaces)) {
            setHightlightFaces(!applyHighlightFaces);
        }
        if (update) {
            update();
        }
    }

    private void update() {
        if (image == null) {
            updatePanel(null);
        } else {
            applyHighlightFaces(true);
            BufferedImage img = image;
            img = rotation != 0 ? ImageUtil.rotate(img, rotation) : img;
            img = applyBlurFilter ? applyBlur(img) : img;
            img = applyGrayScale ? applyGrayScale(img) : img;
            updatePanel(img);
            int factor = sliderBrightness.getValue();
            if (factor != 0) {
                updateBrightness(factor);
            }
        }
    }

    private void setButtonSelected(JButton button, boolean selected) {
        button.setBorderPainted(selected);
    }

    private BufferedImage applyBlur(BufferedImage image) {
        return ImageUtil.blur(image, maxBlurDim, blurIntensity);
    }

    private BufferedImage applyGrayScale(BufferedImage image) {
        return ImageUtil.grayscale(image);
    }

    private void applyHighlightFaces(boolean restoreImageBeforeHighlight) {
        if (restoreImageBeforeHighlight) {
            image = ImageUtil.cloneImage(originalImage);
        }
        if (applyHighlightFaces) {
            drawRectangles(image, facesLocations, rectColorMainFacesOthers);
        }
        if (videoComment != null) {
            image = ImageUtil.getBestFramesFit(image, videoComment, imagePanel.getWidth(), imagePanel.getHeight());
        }
    }

    public void setBlurFilter(boolean enableBlur) {
        applyBlurFilter = enableBlur;
        setButtonSelected(blurButton, applyBlurFilter);
        update();
    }

    public void setGrayFilter(boolean enableGray) {
        applyGrayScale = enableGray;
        setButtonSelected(grayButton, applyGrayScale);
        update();
    }

    public void setHightlightFaces(boolean enableHightlightFaces) {
        applyHighlightFaces = enableHightlightFaces;
        setButtonSelected(highlightFacesButton, applyHighlightFaces);
        update();
    }
}