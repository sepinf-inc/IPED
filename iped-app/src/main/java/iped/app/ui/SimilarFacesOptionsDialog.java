package iped.app.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import org.apache.commons.io.IOUtils;
import org.apache.tika.mime.MediaType;

import iped.data.IItem;
import iped.engine.preview.PreviewRepository;
import iped.engine.preview.PreviewRepositoryManager;
import iped.engine.search.SimilarFacesSearch;
import iped.io.SeekableInputStream;
import iped.utils.ExternalImageConverter;
import iped.utils.ImageUtil;

public class SimilarFacesOptionsDialog extends JDialog {
    private static final long serialVersionUID = 1;
    private List<Rectangle2D> rects = new ArrayList<Rectangle2D>();
    private final List<RoundRectangle2D> screenRects = new ArrayList<RoundRectangle2D>();
    private final Set<Integer> selectedIdxs = Collections.synchronizedSet(new HashSet<Integer>());
    private BufferedImage img;
    private boolean resOk;
    private int resMinScore;
    private int resMode;
    private ExternalImageConverter externalImageConverter = new ExternalImageConverter();

    public SimilarFacesOptionsDialog(Window parent, IItem item, int minScore, int mode) {
        super(parent, ModalityType.APPLICATION_MODAL);
        setTitle(Messages.getString("MenuClass.FindSimilarFaces"));
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setContentPane(content);
        setMinimumSize(new Dimension(320, 240));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                IOUtils.closeQuietly(externalImageConverter);
            }
        });

        JRadioButton rb0 = new JRadioButton(Messages.getString("FaceSimilarity.ModeOR"));
        JRadioButton rb1 = new JRadioButton(Messages.getString("FaceSimilarity.ModeAND"));
        ButtonGroup bg = new ButtonGroup();
        bg.add(rb0);
        bg.add(rb1);
        (mode == 0 ? rb0 : rb1).setSelected(true);

        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

        JLabel labelTop = new JLabel(Messages.getString("FaceSimilarity.MinScore") + ":  ");
        input.add(labelTop);

        SpinnerModel sm = new SpinnerNumberModel(minScore, 1, 100, 5);
        JSpinner spMinScore = new JSpinner(sm);
        input.add(spMinScore);

        JPanel okCancel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));

        Dimension dim = new Dimension(80, 26);
        JButton butOk = new JButton();
        getRootPane().setDefaultButton(butOk);
        butOk.setText(Messages.getString("Ok"));
        butOk.setPreferredSize(dim);
        okCancel.add(butOk);

        JButton butCancel = new JButton();
        butCancel.setPreferredSize(dim);
        butCancel.setText(Messages.getString("Cancel"));
        okCancel.add(butCancel);

        butOk.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                setVisible(false);
                resOk = true;
                resMode = rb0.isSelected() ? 0 : 1;
                resMinScore = minScore;
                try {
                    resMinScore = ((Number) spMinScore.getValue()).intValue();
                } catch (Exception e) {
                }
                resMinScore = Math.max(1, Math.min(100, resMinScore));
            }
        });

        butCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });

        JPanel top = new JPanel();
        JPanel bottom = new JPanel();

        MediaType mime = item.getMediaType();
        String mimeStr = (mime != null) ? mime.toString() : null;
        if (img == null) {
            File view = item.getViewFile();
            if (view != null && view.exists()) {
                img = ImageUtil.getSubSampledImage(view, 1024, mimeStr);
            } else if (item.hasPreview()) {
                try {
                    PreviewRepository previewRepo = PreviewRepositoryManager.get(item.getPreviewBaseFolder());
                    AtomicReference<BufferedImage> reference = new AtomicReference<>();
                    previewRepo.consumePreview(item, inputStream -> {
                        reference.set(ImageUtil.getSubSampledImage(inputStream, 1024, mimeStr));
                    });
                    img = reference.get();
                } catch (SQLException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (img == null) {
            img = ImageUtil.getSubSampledImage(item, 1024, mimeStr);
            String str = item.getMetadata().get("image:tiff:Orientation");
            if (str != null) {
                try {
                    int rot = Integer.parseInt(str);
                    if (rot > 1) {
                        img = ImageUtil.applyOrientation(img, rot);
                    }
                } catch (Exception e) {
                }
            }
        }
        if (img == null) {
            try (SeekableInputStream sis = item.getSeekableInputStream()) {
                BufferedInputStream in = new BufferedInputStream(sis);
                img = externalImageConverter.getImage(in, 1024, true, sis.size());
            } catch (Exception e) {
            }
        }
        if (img != null) {
            try {
                Dimension d = getDimension(item);
                if (d != null) {
                    Object locations = item.getExtraAttribute(SimilarFacesSearch.FACE_LOCATIONS);
                    if (locations != null) {
                        double zoom = getZoom(d, img);
                        if (zoom != 0) {
                            rects = toRectList(locations, zoom, d.width, d.height);
                            for (int i = 0; i < rects.size(); i++) {
                                selectedIdxs.add(i);
                            }
                        }
                    }
                }
            } catch (IOException e) {
            }
            Stroke mainStroke = new BasicStroke(2);
            Stroke backStroke = new BasicStroke(4);
            Color rectColorMain = new Color(255, 0, 0, 200);
            Color rectColorDisabled = new Color(128, 128, 128, 200);
            Color rectColorBack = new Color(255, 255, 255, 120);
            Color areaColor = new Color(255, 255, 255, 80);

            JPanel center = new JPanel() {
                private static final long serialVersionUID = 1;

                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    super.paintComponent(g);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    double sw = getWidth();
                    double sh = getHeight() - 16;
                    double zw = sw / img.getWidth();
                    double zh = sh / img.getHeight();
                    double zoom = Math.min(2, Math.min(zw, zh));
                    int w = (int) (img.getWidth() * zoom);
                    int h = (int) (img.getHeight() * zoom);
                    int x = (getWidth() - w) / 2;
                    int y = (getHeight() - h) / 2;
                    g2.drawImage(img, x, y, w, h, null);

                    g2.setColor(Color.red);
                    synchronized (screenRects) {
                        screenRects.clear();
                        for (int i = 0; i < rects.size(); i++) {
                            Rectangle2D rc = rects.get(i);
                            RoundRectangle2D rr = new RoundRectangle2D.Double(x + rc.getX() * zoom,
                                    y + rc.getY() * zoom, rc.getWidth() * zoom, rc.getHeight() * zoom, 10, 10);
                            screenRects.add(rr);
                        }
                    }
                    Area a = new Area(new Rectangle2D.Double(x, y, w, h));
                    for (int i = 0; i < screenRects.size(); i++) {
                        if (selectedIdxs.contains(i)) {
                            RoundRectangle2D rr = screenRects.get(i);
                            a.subtract(new Area(rr));
                        }
                    }
                    g2.setColor(areaColor);
                    g2.fill(a);
                    for (int i = 0; i < screenRects.size(); i++) {
                        RoundRectangle2D rr = screenRects.get(i);

                        g2.setStroke(backStroke);
                        g2.setColor(rectColorBack);
                        g2.draw(rr);

                        g2.setStroke(mainStroke);
                        g2.setColor(selectedIdxs.contains(i) ? rectColorMain : rectColorDisabled);
                        g2.draw(rr);
                    }
                }
            };

            int w = Math.max(96, Math.min(480, img.getWidth()));
            int h = Math.max(96, Math.min(480, w * img.getHeight() / img.getWidth()));
            center.setPreferredSize(new Dimension(w, h));

            JLabel labelSel = new JLabel();
            if (rects.size() > 1) {
                // More than one face
                top.setLayout(new GridLayout(4, 1, 0, 0));
                labelSel.setText(getSelText());
                top.add(input);

                top.add(rb0);
                top.add(rb1);

                JPanel multi = new JPanel(new GridLayout(1, 2, 0, 0));
                multi.add(labelSel);
                JLabel labelClick = new JLabel("(" + Messages.getString("FaceSimilarity.ClickToSelect") + ")");
                labelClick.setHorizontalAlignment(SwingConstants.RIGHT);
                multi.add(labelClick);
                top.add(multi);

                JPanel allNone = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

                JButton butAll = new JButton();
                butAll.setText(Messages.getString("FaceSimilarity.All"));
                butAll.setPreferredSize(dim);
                allNone.add(butAll);

                JButton butNone = new JButton();
                butNone.setText(Messages.getString("FaceSimilarity.None"));
                butNone.setPreferredSize(dim);
                allNone.add(butNone);

                butAll.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        for (int i = 0; i < rects.size(); i++) {
                            selectedIdxs.add(i);
                        }
                        update(labelSel, butOk, rb0, rb1);
                    }
                });

                butNone.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        selectedIdxs.clear();
                        update(labelSel, butOk, rb0, rb1);
                    }
                });

                bottom.setLayout(new GridLayout(1, 2));
                bottom.add(allNone);
                bottom.add(okCancel);
            } else {
                top.setLayout(new GridLayout(1, 1, 0, 0));
                top.add(input);
                bottom.setLayout(new GridLayout(1, 1));
                bottom.add(okCancel);
            }

            add(center, BorderLayout.CENTER);
            center.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && rects.size() > 1) {
                        boolean changed = false;
                        synchronized (screenRects) {
                            for (int i = 0; i < screenRects.size(); i++) {
                                RoundRectangle2D r = screenRects.get(i);
                                if (r.contains(e.getPoint())) {
                                    if (selectedIdxs.contains(i)) {
                                        selectedIdxs.remove(i);
                                    } else {
                                        selectedIdxs.add(i);
                                    }
                                    changed = true;
                                    break;
                                }
                            }
                        }
                        if (changed) {
                            update(labelSel, butOk, rb0, rb1);
                        }
                    }
                }
            });
        } else {
            top.setLayout(new GridLayout(1, 1));
            top.add(input);
            bottom.setLayout(new GridLayout(1, 1));
            bottom.add(okCancel);
        }
        add(top, BorderLayout.NORTH);
        add(bottom, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }

    private void update(JLabel labelSel, JButton butOk, JRadioButton rb0, JRadioButton rb1) {
        repaint();
        labelSel.setText(getSelText());
        butOk.setEnabled(!selectedIdxs.isEmpty());
        rb0.setEnabled(selectedIdxs.size() > 1);
        rb1.setEnabled(selectedIdxs.size() > 1);
    }

    public boolean isOk() {
        return resOk;
    }

    public int getMinScore() {
        return resMinScore;
    }

    public int getMode() {
        return resMode;
    }

    public Set<Integer> getSelectedIdxs() {
        return selectedIdxs;
    }

    private String getSelText() {
        return Messages.getString("FaceSimilarity.SelectedFaces") + ": " + selectedIdxs.size() + " / " + rects.size();
    }

    private static double getZoom(Dimension d, BufferedImage img) throws IOException {
        int original = Math.max(d.width, d.height);
        int displayed = Math.max(img.getWidth(), img.getHeight());
        if (original != 0 && displayed != 0) {
            return displayed / (double) original;
        }
        return 0;
    }

    private Dimension getDimension(IItem item) throws IOException {
        Dimension d = null;
        File view = item.getViewFile();
        if (view != null && view.exists()) {
            d = ImageUtil.getImageFileDimension(view);
        } else if (item.hasPreview()) {
            try {
                PreviewRepository previewRepo = PreviewRepositoryManager.get(item.getPreviewBaseFolder());
                AtomicReference<Dimension> reference = new AtomicReference<>();
                previewRepo.consumePreview(item, inputStream -> {
                    reference.set(ImageUtil.getImageFileDimension(inputStream));
                });
                d = reference.get();
            } catch (SQLException | IOException e) {
                e.printStackTrace();
            }
        }
        if (d == null) {
            try (InputStream is = item.getSeekableInputStream()) {
                d = ImageUtil.getImageFileDimension(is);
            }
        }
        if (d == null) {
            try (InputStream is = item.getSeekableInputStream()) {
                d = externalImageConverter.getDimension(is);
            }
        }
        return d;
    }

    private static final List<Rectangle2D> toRectList(Object faceLocations, double zoom, int w, int h) {
        List<Rectangle2D> l = new ArrayList<Rectangle2D>();
        if (faceLocations instanceof List) {
            List<?> ol = (List<?>) faceLocations;
            for (Object obj : ol) {
                if (obj instanceof String) {
                    String s = (String) obj;
                    l.add(toRect(s, zoom, w, h));
                } else if (obj instanceof List) {
                    List<?> sl = (List<?>) obj;
                    l.add(toRect(sl, zoom, w, h));
                }
            }
        } else if (faceLocations instanceof String) {
            l.add(toRect((String) faceLocations, zoom, w, h));
        }
        return l;
    }

    private static final Rectangle2D toRect(String s, double zoom, int w, int h) {
        String[] vals = s.substring(1, s.length() - 1).split(", ");
        double top = Math.max(0, Integer.parseInt(vals[0])) * zoom;
        double right = Math.min(w, Integer.parseInt(vals[1])) * zoom;
        double bottom = Math.min(h, Integer.parseInt(vals[2])) * zoom;
        double left = Math.max(0, Integer.parseInt(vals[3])) * zoom;
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }

    private static final Rectangle2D toRect(List<?> l, double zoom, int w, int h) {
        double top = Math.max(0, ((Number) l.get(0)).intValue()) * zoom;
        double right = Math.min(w, ((Number) l.get(1)).intValue()) * zoom;
        double bottom = Math.min(h, ((Number) l.get(2)).intValue()) * zoom;
        double left = Math.max(0, ((Number) l.get(3)).intValue()) * zoom;
        return new Rectangle2D.Double(left, top, right - left, bottom - top);
    }
}
