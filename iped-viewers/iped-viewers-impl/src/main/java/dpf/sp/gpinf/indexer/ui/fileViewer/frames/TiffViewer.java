package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.awt.BorderLayout;
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
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.ui.fileViewer.Messages;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.io.IStreamSource;

public class TiffViewer extends Viewer {

    private static final long serialVersionUID = -7364831780786494299L;

    private final JLabel pageCounter1 = new JLabel(Messages.getString("TiffViewer.Page")); //$NON-NLS-1$
    private JTextField pageCounter2 = new JTextField(3);
    private JLabel pageCounter3 = new JLabel(" / "); //$NON-NLS-1$

    private JPanel imgPanel;
    private JScrollPane scrollPane;

    private ExecutorService executor = Executors.newFixedThreadPool(1);

    volatile private InputStream is = null;
    volatile private ImageInputStream iis = null;
    volatile private ImageReader reader = null;

    volatile private BufferedImage image = null;
    volatile private IStreamSource currentContent;
    volatile private int currentPage = 0;
    volatile private int numPages = 0;
    volatile private double zoomFactor = 1;
    volatile private int rotation = 0;
    volatile private boolean painting = false;

    @Override
    public String getName() {
        return "TIFF"; //$NON-NLS-1$
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("image/tiff"); //$NON-NLS-1$
    }

    public TiffViewer() {
        super(new BorderLayout());

        imgPanel = new JPanel() {
            @Override
            public void paintComponent(Graphics g) {
                painting = true;
                // System.out.println("painting");
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                if (image != null) {
                    int w = (int) (image.getWidth() * zoomFactor);
                    int h = (int) (image.getHeight() * zoomFactor);
                    g2.drawImage(image, 0, 0, w, h, null);
                }
                painting = false;
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
        JPanel topPanel = initControlPanel();
        this.getPanel().add(topPanel, BorderLayout.NORTH);
        this.getPanel().add(scrollPane, BorderLayout.CENTER);

    }

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
    public void loadFile(IStreamSource content, Set<String> highlightTerms) {

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
            if (reader != null)
                reader.dispose();
        } catch (Exception e) {
        }

        IOUtil.closeQuietly(iis);
        IOUtil.closeQuietly(is);

        reader = null;
        iis = null;
        is = null;
    }

    private void openContent(final IStreamSource content) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                disposeResources();
                if (content != currentContent)
                    return;
                try {
                    is = currentContent.getStream();
                    iis = ImageIO.createImageInputStream(is);
                    reader = ImageIO.getImageReaders(iis).next();
                    reader.setInput(iis, false, true);

                    numPages = reader.getNumImages(true);

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

    private void displayPage(final IStreamSource content) {

        executor.submit(new Runnable() {
            @Override
            public void run() {
                if (content != currentContent)
                    return;
                try {
                    int w0 = reader.getWidth(currentPage - 1);
                    int h0 = reader.getHeight(currentPage - 1);

                    ImageReadParam params = reader.getDefaultReadParam();
                    int sampling = w0 > h0 ? w0 / 1000 : h0 / 1000;
                    if (sampling < 1)
                        sampling = 1;
                    int finalW = (int) Math.ceil((float) w0 / sampling);
                    int finalH = (int) Math.ceil((float) h0 / sampling);

                    params.setSourceSubsampling(sampling, sampling, 0, 0);
                    image = reader.getImageTypes(currentPage - 1).next().createBufferedImage(finalW, finalH);
                    params.setDestination(image);

                    reader.read(currentPage - 1, params);

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

    private void refreshGUI() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                fitWidth();
                imgPanel.scrollRectToVisible(new Rectangle());
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
