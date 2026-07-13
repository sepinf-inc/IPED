package iped.viewers.search;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import iped.utils.LocalizedFormat;

public class SearchPanel extends JPanel {
    private static final long serialVersionUID = 2822133907330094018L;
    private final JTextField searchText = new JTextField(30);
    private final JLabel hitsLabel = new JLabel();
    private final List<SearchListener> listeners = new ArrayList<SearchListener>();
    private final Ellipse2D[] but = new Ellipse2D[3];
    private int currBut = -1, totHits = -1;
    private boolean currPress;
    private final NumberFormat nf = LocalizedFormat.getNumberInstance();

    private static final Color colorShadow = new Color(128, 128, 128, 40);
    private static final Color colorEnable = new Color(64, 64, 64);
    private static final Color colorDisable = new Color(196, 196, 196);
    private static final Color colorHover = new Color(235, 235, 235);
    private static final Color colorPress = new Color(210, 210, 210);
    private static final Stroke strokeIcon = new BasicStroke(1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
    private static final Stroke strokeBorder = new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

    public SearchPanel() {
        setBorder(BorderFactory.createEmptyBorder(2, 0, 5, 0));
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(Box.createRigidArea(new Dimension(10, 10)));
        add(searchText);
        add(Box.createRigidArea(new Dimension(6, 10)));
        add(hitsLabel);
        add(Box.createRigidArea(new Dimension(75, 10)));
        updateUI();

        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                (new Thread() {
                    public void run() {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                        }
                        searchText.requestFocusInWindow();
                    };
                }).start();
            }
        });
        searchText.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                fireChange(SearchEvent.termChange);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                fireChange(SearchEvent.termChange);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fireChange(SearchEvent.termChange);
            }
        });

        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int newBut = findBut(e);
                if (newBut != currBut) {
                    currBut = newBut;
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (currPress) {
                    currBut = findBut(e);
                    currPress = false;
                    repaint();
                    if (currBut != -1) {
                        pressed(currBut);
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!currPress) {
                    currBut = findBut(e);
                    currPress = true;
                    repaint();
                }
            }
        });

        searchText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    setVisible(false);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    fireChange(SearchEvent.nextHit);
                }
            }
        });
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setOpaque(false);
        if (searchText != null) {
            searchText.setBackground(Color.white);
            searchText.setForeground(Color.black);
            searchText.setBorder(BorderFactory.createEmptyBorder());
        }
        if (hitsLabel != null) {
            Font fontLabel = new Font(hitsLabel.getFont().getName(), Font.PLAIN, searchText.getFont().getSize() * 9 / 10);
            hitsLabel.setForeground(Color.gray);
            hitsLabel.setFont(fontLabel);
            hitsLabel.setBackground(Color.white);
        }
    }

    private void pressed(int idx) {
        if (idx == 0) {
            setVisible(false);
        } else if (idx == 1) {
            fireChange(SearchEvent.nextHit);
        } else if (idx == 2) {
            fireChange(SearchEvent.prevHit);
        }
    }

    private int findBut(MouseEvent e) {
        for (int i = 0; i < 3; i++) {
            if (but[i] != null && but[i].contains(e.getPoint())) {
                return i;
            }
        }
        return -1;
    }

    public void addSearchListener(SearchListener listener) {
        listeners.add(listener);
    }

    private void fireChange(int type) {
        for (SearchListener l : listeners) {
            l.stateChanged(new SearchEvent(this, type));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        RoundRectangle2D rc = new RoundRectangle2D.Double(1, 1, w - 5, getHeight() - 5, 12, 12);
        g2.setColor(colorShadow);
        g2.translate(-2, 0);
        g2.fill(rc);
        g2.translate(2, 2);
        g2.fill(rc);
        g2.translate(2, 2);
        g2.fill(rc);
        g2.translate(-2, -4);
        g2.setColor(Color.white);
        g2.fill(rc);
        g2.setColor(colorDisable);
        g2.drawLine(w - 70, 5, w - 70, 20);
        for (int i = 0; i < 3; i++) {
            Ellipse2D e = but[i] = new Ellipse2D.Double(w - 20 * i - 25, 3, 19, 19);
            if (i == currBut) {
                g2.setColor(currPress && (i == 0 || totHits > 0) ? colorPress : colorHover);
                g2.fill(e);
            }
        }
        g2.setStroke(strokeIcon);

        g2.setColor(colorEnable);
        g2.drawLine(w - 19, 9, w - 13, 15);
        g2.drawLine(w - 19, 15, w - 13, 9);

        if (totHits <= 0) {
            g2.setColor(colorDisable);
        }
        g2.drawLine(w - 36, 14, w - 32, 10);
        g2.drawLine(w - 40, 10, w - 36, 14);

        g2.drawLine(w - 56, 10, w - 52, 14);
        g2.drawLine(w - 60, 14, w - 56, 10);

        g2.setStroke(strokeBorder);
        g2.setColor(colorDisable);
        g2.draw(rc);
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            searchText.setText("");
        }
        super.setVisible(visible);
    }

    public String getText() {
        return searchText.isVisible() ? searchText.getText() : "";
    }

    public void setHits(int currHit, int totHits) {
        this.totHits = totHits;
        if (totHits == -1) {
            hitsLabel.setText(" ");
        } else {
            hitsLabel.setText(nf.format(currHit) + "/" + nf.format(totHits));
        }
        repaint();
    }
}
