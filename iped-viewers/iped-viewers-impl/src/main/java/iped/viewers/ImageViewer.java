package iped.viewers;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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

    private static final String ACTION_COMMAND_SELECT_AGE_PREFIX = "selectAge:";

    private static final String AGE_OPTION_CHILD = "Child";
    private static final String AGE_OPTION_TEENAGER = "Teenager";
    private static final String AGE_OPTION_ADULT = "Adult";
    private static final String AGE_OPTION_MIDDLE_AGE = "MiddleAge";
    private static final String AGE_OPTION_AGED = "Aged";

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

    protected static final Color rectColorMainFacesInTerms = new Color(255,0,0,200); // red
    private static final Color rectColorMainFacesOthers = new Color(0,255,0,200); // green
    private static final Color rectColorBack = new Color(255,255,255,50);

    private static final String[] ageOptions = { AGE_OPTION_CHILD, AGE_OPTION_TEENAGER, AGE_OPTION_ADULT, AGE_OPTION_MIDDLE_AGE, AGE_OPTION_AGED };
    private static final Set<String> selectedAges = new HashSet<>();
    private static final Map<String, Color> ageRectangleColors = new HashMap<>();
    static {
        ageRectangleColors.put(AGE_OPTION_CHILD, new Color(255, 153, 204)); // Rose Pink
        ageRectangleColors.put(AGE_OPTION_TEENAGER, new Color(255, 165, 0, 200)); // Orange
        ageRectangleColors.put(AGE_OPTION_ADULT, new Color(0, 0, 255, 200)); // Blue
        ageRectangleColors.put(AGE_OPTION_MIDDLE_AGE, new Color(0, 255, 255, 200)); // Cyan
        ageRectangleColors.put(AGE_OPTION_AGED, new Color(255, 255, 0, 200)); // Yellow
    }

    volatile protected BufferedImage image;
    volatile protected BufferedImage originalImage;
    volatile protected int rotation;
    volatile protected boolean applyBlurFilter = false;
    volatile protected boolean applyGrayScale = false;
    volatile protected boolean applyHighlightFaces = false;
    volatile protected List<String> facesLocations;
    volatile protected List<String> faceAgeLabels;
    volatile protected Dimension originalDimension;

    private JButton blurButton, grayButton, highlightFacesButton, ageSelectionButton;

    private String videoComment;

    private boolean enableAgeEstimationCombo;


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
        faceAgeLabels = null;
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

                    loadFacesAttributes(item);
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

                    drawRectangles(image, highlightTerms, rectColorMainFacesInTerms, true);
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

    @SuppressWarnings("unchecked")
    protected void loadFacesAttributes(IItemReader item) {
        Object faceLocationsAttr = item.getExtraAttribute(ExtraProperties.FACE_LOCATIONS);
        if (faceLocationsAttr instanceof List) {
            facesLocations = ((List<?>) faceLocationsAttr)
                    .stream()
                    .map(location -> HIGHLIGHT_LOCATION + location)
                    .collect(Collectors.toList());
        } else if (faceLocationsAttr instanceof String) {
            facesLocations =  Collections.singletonList(HIGHLIGHT_LOCATION + faceLocationsAttr);
        }

        Object faceAgeLabelsAttr = item.getExtraAttribute(ExtraProperties.FACE_AGE_LABELS);
        if (faceAgeLabelsAttr instanceof List) {
            faceAgeLabels = (List<String>) faceAgeLabelsAttr;
        } else if (faceAgeLabelsAttr instanceof String) {
            faceAgeLabels =  Collections.singletonList((String) faceAgeLabelsAttr);
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

    protected void drawRectangles(BufferedImage img, Collection<String> highlights, Color rectColorMain, boolean expanded)  {

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
        final int strokeWidth = Math.max(2, Math.min(img.getHeight(), img.getWidth()) / 200);

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

                if (expanded) {
                    top -= strokeWidth;
                    left -= strokeWidth;
                    right += strokeWidth;
                    bottom += strokeWidth;
                    if (top < 0) {
                        top = 0;
                    }
                    if (left < 0) {
                        left = 0;
                    }
                }

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
        highlightFacesButton.setVisible(false);

        ageSelectionButton = createAgeSelectionToolBarButton();
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

    protected JButton createAgeSelectionToolBarButton() {

        JPanel popupPanel = new JPanel();
        popupPanel.setLayout(new BoxLayout(popupPanel, BoxLayout.Y_AXIS));

        for (String ageOption : ageOptions) {
            JCheckBox checkBox = new JCheckBox(Messages.getString("ImageViewer.AgeSelectionOption." + ageOption));
            checkBox.setActionCommand(ACTION_COMMAND_SELECT_AGE_PREFIX + ageOption);
            checkBox.addActionListener(this);

            // pre-select all age options
            selectedAges.add(ageOption);
            checkBox.setSelected(true);

            // Create the color swatch
            JPanel colorIndicator = new JPanel();
            colorIndicator.setBackground(ageRectangleColors.get(ageOption));
            colorIndicator.setPreferredSize(new Dimension(15, 15)); // 15x15 pixel square
            colorIndicator.setBorder(BorderFactory.createLineBorder(Color.BLACK));

            // Create the main row panel that holds the swatch and checkbox
            JPanel rowPanel = new JPanel(new BorderLayout(5, 0)); // 5px horizontal gap
            rowPanel.setOpaque(false);
            rowPanel.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 10)); // Add a little padding
            rowPanel.add(colorIndicator, BorderLayout.LINE_START);
            rowPanel.add(checkBox, BorderLayout.CENTER);

            popupPanel.add(rowPanel);
        }

        JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.add(popupPanel);

        JButton ageButton = new JButton(Messages.getString("ImageViewer.AgeSelectionOptionTitle") + " ▾");
        ageButton.setVisible(applyHighlightFaces);
        ageButton.setMargin(new Insets(3, 10, 3, 10));
        ageButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!ageButton.isEnabled()) {
                    return;
                }
                if (popupMenu.isVisible()) {
                    popupMenu.setVisible(false);
                } else {
                    popupMenu.show(ageButton, 0, ageButton.getHeight());
                }
            }
        });
        toolBar.add(ageButton);
        return ageButton;
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
        } else if (cmd.startsWith(ACTION_COMMAND_SELECT_AGE_PREFIX)) {
            String ageOption = StringUtils.substringAfter(cmd, ACTION_COMMAND_SELECT_AGE_PREFIX);
            if (((JCheckBox) e.getSource()).isSelected()) {
                selectedAges.add(ageOption);
            } else {
                selectedAges.remove(ageOption);
            }
            update = true;
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

    protected void applyHighlightFaces(boolean restoreImageBeforeHighlight) {
        if (restoreImageBeforeHighlight) {
            image = ImageUtil.cloneImage(originalImage);
        }

        if (applyHighlightFaces) {
            if (faceAgeLabels != null && facesLocations != null && faceAgeLabels.size() == facesLocations.size()) {
                for (String ageOption : ageOptions) {
                    if (selectedAges.contains(ageOption)) {
                        List<String> ageLocations = new ArrayList<>();
                        for (int i = 0; i < faceAgeLabels.size(); i++) {
                            if (ageOption.equals(faceAgeLabels.get(i))) {
                                ageLocations.add(facesLocations.get(i));
                            }
                        }
                        drawRectangles(image, ageLocations, ageRectangleColors.get(ageOption), false);
                    }
                }
            } else {
                drawRectangles(image, facesLocations, rectColorMainFacesOthers, false);
            }
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
        ageSelectionButton.setVisible(applyHighlightFaces && enableAgeEstimationCombo);
        setButtonSelected(highlightFacesButton, applyHighlightFaces);
        update();
    }

    public void setEnableAgeEstimationCombo(boolean enableAgeEstimationCombo) {
        this.enableAgeEstimationCombo = enableAgeEstimationCombo;
    }

    public void setEnableHighlightFacesButton(boolean enableHighlightFacesButton) {
        highlightFacesButton.setVisible(enableHighlightFacesButton);
    }
}