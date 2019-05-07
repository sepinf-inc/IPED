package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.DefaultResourceCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.IOUtil;

import iped3.io.StreamSource;

public class PDFBoxViewer extends Viewer {

    private static final long serialVersionUID = -7364831780786494299L;

    private final JLabel pageCounter1 = new JLabel(Messages.getString("TiffViewer.Page")); //$NON-NLS-1$
    private JTextField pageCounter2 = new JTextField(3);
    private JLabel pageCounter3 = new JLabel(" / "); //$NON-NLS-1$

    private JPanel imgPanel;
    private JScrollPane scrollPane;

    private ExecutorService executor = Executors.newCachedThreadPool();

    volatile private InputStream is = null;

    private PDDocument document = null;
    private PDFRenderer pdfRenderer;

    volatile private BufferedImage image = null;
    volatile private StreamSource currentContent;
    volatile private int currentPage = 0;
    volatile private int numPages = 0;
    volatile private double zoomFactor = 1;
    volatile private int rotation = 0;
    volatile private boolean painting = false;

    static {
        String javaVer = System.getProperty("java.version");
        if (javaVer.startsWith("1.8.0")) {
            int idx = javaVer.indexOf("_");
            if (idx > 0 && Integer.valueOf(javaVer.substring(idx + 1)) < 191)
                System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        // Faster rendering in some cases with a lot of images per page
        System.setProperty("org.apache.pdfbox.rendering.UsePureJavaCMYKConversion", "true"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private class NoResourceCache extends DefaultResourceCache {
        @Override
        public void put(COSObject indirect, PDXObject xobject) throws IOException {
            // do not cache images to prevent OutOfMemory
        }
    }

    @Override
    public String getName() {
        return "PDFBOX"; //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("application/pdf"); //$NON-NLS-1$
    }

    public PDFBoxViewer() {
        super(new BorderLayout());

        imgPanel = new JPanel() {
            @Override
            public void paintComponent(Graphics g) {
                // System.out.println("painting");
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                if (image != null) {
                    int w = (int) (image.getWidth() * zoomFactor);
                    int h = (int) (image.getHeight() * zoomFactor);
                    g2.drawImage(image, 0, 0, w, h, null);
                }
            }
        };

        this.getPanel().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (image != null) {
                    fitWidth();
                    imgPanel.repaint();
                }
            }
        });

        scrollPane = new JScrollPane(imgPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                long t = e.getWhen();
                JScrollBar bar = scrollPane.getVerticalScrollBar();
                if (lastScrollTime != 0 && t - lastScrollTime > 100 && lastScrollPos == bar.getValue()) {
                    if (e.getWheelRotation() > 0 && currentPage < numPages
                            && bar.getValue() == bar.getMaximum() - bar.getVisibleAmount()) {
                        currentPage++;
                        displayPage();
                        imgPanel.scrollRectToVisible(new Rectangle());
                    } else if (e.getWheelRotation() < 0 && currentPage > 1 && bar.getValue() == bar.getMinimum()) {
                        currentPage--;
                        displayPage();
                        imgPanel.scrollRectToVisible(new Rectangle(0, imgPanel.getHeight() - 1, 1, 1));
                    }
                }
                lastScrollTime = t;
                lastScrollPos = bar.getValue();
            }
        });
        JPanel topPanel = initControlPanel();
        this.getPanel().add(topPanel, BorderLayout.NORTH);
        this.getPanel().add(scrollPane, BorderLayout.CENTER);

    }

    private long lastScrollTime = 0;
    private int lastScrollPos = 0;

    @Override
    public void copyScreen(Component comp) {
        super.copyScreen(scrollPane);
    }

    private void fitWidth() {
        if (image != null) {
            zoomFactor = (imgPanel.getVisibleRect().getWidth()) / image.getWidth();
            Dimension d = new Dimension((int) imgPanel.getVisibleRect().getWidth(),
                    (int) (image.getHeight() * zoomFactor));
            imgPanel.setPreferredSize(d);
        }
    }

    @Override
    public void loadFile(StreamSource content, Set<String> highlightTerms) {

        currentPage = 1;
        numPages = 0;
        rotation = 0;
        image = null;

        if (content != currentContent && currentContent != null)
            refreshGUI();

        currentContent = content;
        if (currentContent != null) {
            openContent(content);
        }

    }

    private void disposeResources() {
        try {
            if (document != null)
                document.close();
        } catch (Exception e) {
        }

        IOUtil.closeQuietly(is);

        document = null;
        is = null;
    }

    private void openContent(final StreamSource content) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                disposeResources();
                if (content != currentContent)
                    return;
                try {
                    is = content.getStream();
                    document = PDDocument.load(is, MemoryUsageSetting.setupMixed(100000000));
                    // document.setResourceCache(new NoResourceCache());
                    pdfRenderer = new PDFRenderer(document);
                    pdfRenderer.setSubsamplingAllowed(true);
                    numPages = document.getNumberOfPages();

                } catch (Exception e) {
                    e.printStackTrace();
                }
                displayPage(content);
            }
        });

    }

    private void displayPage() {
        displayPage(currentContent);
    }

    private void displayPage(final StreamSource content) {

        imgPanel.scrollRectToVisible(new Rectangle());
        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (content != currentContent)
                    return;
                try {
                    int page = currentPage - 1;
                    int pageW = (int) document.getPage(page).getCropBox().getWidth();
                    int pageH = (int) document.getPage(page).getCropBox().getHeight();

                    int rotationAngle = document.getPage(page).getRotation();
                    if (rotationAngle == 90 || rotationAngle == 270)
                        image = new BufferedImage(pageH, pageW, BufferedImage.TYPE_INT_RGB);
                    else
                        image = new BufferedImage(pageW, pageH, BufferedImage.TYPE_INT_RGB);

                    Graphics2D g2 = (Graphics2D) image.getGraphics();
                    g2.setBackground(Color.WHITE);
                    g2.setColor(Color.WHITE);
                    g2.fillRect(0, 0, pageW, pageH);

                    painting = true;
                    refreshWhilePainting();
                    pdfRenderer.renderPageToGraphics(page, g2);
                    painting = false;
                    g2.dispose();

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    if (image != null)
                        image = getCompatibleImage(image);
                }

                refreshGUI();
            }
        });

    }

    private void refreshWhilePainting() {
        executor.submit(new Runnable() {
            public void run() {
                try {
                    while (painting) {
                        refreshGUI();
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                }
            }
        });
    }

    private void refreshGUI() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                fitWidth();
                imgPanel.revalidate();
                imgPanel.repaint();
                pageCounter2.setText(String.valueOf(currentPage));
                pageCounter3.setText(" / " + numPages); //$NON-NLS-1$
            }
        });
    }

    private BufferedImage getCompatibleImage(BufferedImage image) {
        // System.out.println("getCompat" + System.currentTimeMillis()/1000);

        // obtain the current system graphical settings
        GraphicsConfiguration gfx_config = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();

        /*
         * if image is already compatible and optimized for current system settings,
         * simply return it
         */
        if (image.getColorModel().equals(gfx_config.getColorModel())) {
            return image;
        }

        // image is not optimized, so create a new image that is
        BufferedImage new_image = gfx_config.createCompatibleImage(image.getWidth(), image.getHeight(),
                image.getTransparency());

        // get the graphics context of the new image to draw the old image on
        Graphics2D g2d = (Graphics2D) new_image.getGraphics();

        // actually draw the image and dispose of context no longer needed
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        // System.out.println("CompatGot" + System.currentTimeMillis()/1000);

        // return the new optimized image
        return new_image;
    }

    private JPanel initControlPanel() {

        JPanel topBar = new JPanel();
        topBar.setLayout(new GridLayout());// new
        // FlowLayout(FlowLayout.CENTER,0,0));
        topBar.setPreferredSize(new Dimension(30, 27));

        /**
         * back to page 1
         */
        JButton start = new JButton();
        start.setBorderPainted(false);
        URL startImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/start.gif"); //$NON-NLS-1$
        start.setIcon(new ImageIcon(startImage));
        topBar.add(start);

        start.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentContent != null && currentPage != 1) {
                    currentPage = 1;
                    displayPage();
                }
            }
        });

        /**
         * back icon
         */
        JButton back = new JButton();
        back.setBorderPainted(false);
        URL backImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/back.gif"); //$NON-NLS-1$
        back.setIcon(new ImageIcon(backImage));
        topBar.add(back);
        back.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentContent != null && currentPage > 1) {
                    currentPage -= 1;
                    displayPage();
                }
            }
        });

        pageCounter2.setEditable(true);
        pageCounter2.setToolTipText(Messages.getString("TiffViewer.Page")); //$NON-NLS-1$
        pageCounter2.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent a) {

                String value = pageCounter2.getText().trim();
                int newPage;

                // allow for bum values
                try {
                    newPage = Integer.parseInt(value);

                    if ((newPage > numPages) || (newPage < 1)) {
                        return;
                    }

                    currentPage = newPage;
                    displayPage();

                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null,
                            "<" + value + "> " + Messages.getString("TiffViewer.InvalidPage") + numPages); //$NON-NLS-1$
                }

            }

        });

        topBar.add(new JPanel());
        topBar.add(pageCounter2);
        topBar.add(pageCounter3);
        // topBar.add(new JPanel());

        /**
         * forward icon
         */
        JButton forward = new JButton();
        forward.setBorderPainted(false);
        URL fowardImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/forward.gif"); //$NON-NLS-1$
        forward.setIcon(new ImageIcon(fowardImage));
        topBar.add(forward);
        forward.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentContent != null && currentPage < numPages) {
                    currentPage += 1;
                    displayPage();
                }
            }
        });

        /**
         * goto last page
         */
        JButton end = new JButton();
        end.setBorderPainted(false);
        URL endImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/end.gif"); //$NON-NLS-1$
        end.setIcon(new ImageIcon(endImage));
        topBar.add(end);
        end.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentContent != null && currentPage < numPages) {
                    currentPage = numPages;
                    displayPage();
                }
            }
        });

        return topBar;
    }

    @Override
    public void init() {
        // TODO Auto-generated method stub

    }

    @Override
    public void dispose() {
        // TODO Auto-generated method stub

    }

    @Override
    public void scrollToNextHit(boolean forward) {
        // TODO Auto-generated method stub

    }

}
