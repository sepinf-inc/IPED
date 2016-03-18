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
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import javax.media.jai.PlanarImage;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageDecoder;
import com.sun.media.jai.codec.TIFFDecodeParam;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class TiffViewer extends Viewer {

    private static final long serialVersionUID = -7364831780786494299L;

    private final JLabel pageCounter1 = new JLabel(" Página ");
    private JTextField pageCounter2 = new JTextField(3);
    private JLabel pageCounter3 = new JLabel(" de ");

    private JPanel imgPanel;
    private JScrollPane scrollPane;
    private BufferedImage image = null;
    private StreamSource currentContent;
    private int currentPage = 0;
    private int numPages = 0;
    private double zoomFactor = 1;
    private int rotation = 0;

    private volatile boolean painting = false;

    @Override
    public String getName() {
        return "TIFF";
    }

    @Override
    public boolean isSupportedType(String contentType) {
        return contentType.equals("image/tiff");
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
            Dimension d = new Dimension((int) imgPanel.getVisibleRect().getWidth(), (int) (image.getHeight() * zoomFactor));
            imgPanel.setPreferredSize(d);
        }
    }

    @Override
    public void loadFile(StreamSource content, Set<String> highlightTerms) {

        currentPage = 1;
        rotation = 0;
        image = null;
        refreshGUI();

        currentContent = content;
        if (currentContent != null) {
            displayPage();
        }

    }

    private void displayPage() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TIFFDecodeParam param = null;
                    ImageDecoder dec = ImageCodec.createImageDecoder("tiff", currentContent.getStream(), param);
                    numPages = dec.getNumPages();
                    RenderedImage ri = dec.decodeAsRenderedImage(currentPage - 1);
                    image = getCompatibleImage(PlanarImage.wrapRenderedImage(ri).getAsBufferedImage());
					// image =
                    // PlanarImage.wrapRenderedImage(ri).getAsBufferedImage();

                    dec.getInputStream().close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

                refreshGUI();
            }
        }).start();

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
                pageCounter3.setText(" de " + numPages);
            }
        });
    }

    private BufferedImage getCompatibleImage(BufferedImage image) {
        // obtain the current system graphical settings
        GraphicsConfiguration gfx_config = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

        /*
         * if image is already compatible and optimized for current system
         * settings, simply return it
         */
        if (image.getColorModel().equals(gfx_config.getColorModel())) {
            return image;
        }

        // image is not optimized, so create a new image that is
        BufferedImage new_image = gfx_config.createCompatibleImage(image.getWidth(), image.getHeight(), image.getTransparency());

        // get the graphics context of the new image to draw the old image on
        Graphics2D g2d = (Graphics2D) new_image.getGraphics();

        // actually draw the image and dispose of context no longer needed
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

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
        URL startImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/start.gif");
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
        URL backImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/back.gif");
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
        pageCounter2.setToolTipText("Página");
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
                    JOptionPane.showMessageDialog(null, '>' + value + "< is Not a valid Value.\nPlease enter a number between 1 and " + numPages);
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
        URL fowardImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/forward.gif");
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
        URL endImage = getClass().getResource("/dpf/sp/gpinf/indexer/search/viewer/res/end.gif");
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
