package gpinf.led;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;

/**
 * Painel especializado em exibição de uma imagem. Já inclui rolagem e zoom da
 * imagem.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class ImageViewPanel extends JPanel {

    /**
     * Imagem que será apresentada.
     */
    private BufferedImage image;

    private BufferedImage orgImage;

    public BufferedImage getImage() {
        return image;
    }

    /**
     * Identificador utilizado para serialização da classe.
     */
    private static final long serialVersionUID = 5489808946126559253L;

    /**
     * Componente de rolagem da imagem.
     */
    private JScrollPane scrollPane;

    /**
     * Fator de zoom.
     */
    private double zoomFactor = 1;

    /**
     * Painel para exibição da imagem (inserido dentro do componente de rolagem).
     */
    private JPanel imgPanel;

    /**
     * 0 - Fit to window, 1 - Fit to width.
     */
    private int initialFitMode;

    /**
     * Construtor.
     */
    public ImageViewPanel(int initialFitMode) {
        super(new BorderLayout());
        this.initialFitMode = initialFitMode;
        imgPanel = new JPanel() {
            /**
             * Identificador utilizado para serialização.
             */
            private static final long serialVersionUID = -7607117377739877804L;

            /**
             * Método sobrescrito, reponsável por desenhar o conteúdo do painel.
             *
             * @param g
             *            contexto gráfico a ser utilizado
             */
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (image != null) {
                    int w = (int) Math.ceil(image.getWidth() * zoomFactor);
                    int h = (int) Math.ceil(image.getHeight() * zoomFactor);
                    g2.drawImage(image, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                }
            }
        };
        scrollPane = new JScrollPane(imgPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(BorderLayout.CENTER, scrollPane);

        MouseAdapter mouseListener = new MouseAdapter() {
            private Point refPos;

            @Override
            public void mouseClicked(MouseEvent evt) {
                if (image == null) {
                    return;
                }
                if (evt.getButton() == MouseEvent.BUTTON1) {
                    changeZoom(1.25, evt.getPoint());
                } else if (evt.getButton() == MouseEvent.BUTTON3) {
                    changeZoom(1 / 1.25, evt.getPoint());
                }
            }

            @Override
            public void mousePressed(MouseEvent evt) {
                if (image == null) {
                    return;
                }
                if (evt.getButton() == MouseEvent.BUTTON1) {
                    refPos = new Point(evt.getPoint());
                } else {
                    refPos = null;
                }
            }

            @Override
            public void mouseDragged(final MouseEvent evt) {
                if (refPos != null) {
                    JViewport viewport = scrollPane.getViewport();
                    Rectangle view = viewport.getViewRect();
                    view.x += refPos.x - evt.getX();
                    view.y += refPos.y - evt.getY();
                    imgPanel.scrollRectToVisible(view);
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent evt) {
                if (image == null) {
                    return;
                }
                changeZoom(1 - evt.getWheelRotation() / 5f, evt.getPoint());
            }
        };
        imgPanel.addMouseWheelListener(mouseListener);
        imgPanel.addMouseMotionListener(mouseListener);
        imgPanel.addMouseListener(mouseListener);
    }

    /**
     * Altera imagem que está sendo exibida.
     *
     * @param img
     *            Imagem a ser exibida. Valor nulo é aceito, indicando que nenhuma
     *            imagem deve ser exibida.
     * @return Indicador se uma imagem válida foi carregada com sucesso.
     */
    public boolean setImage(BufferedImage img) {
        orgImage = image = img;
        zoomFactor = 1;
        if (image != null) {
            updateZoomFactor(1.5, initialFitMode == 0, true);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        } else {
            imgPanel.setPreferredSize(new Dimension(1, 1));
            imgPanel.repaint();
        }
        imgPanel.revalidate();
        return image != null;
    }

    /**
     * Altera o fator de zoom da imagem.
     *
     * @param factor
     *            Fator multiplicador de ampliação (se maior que 1) ou redução (se
     *            menor que 1) a ser aplicado.
     * @param reference
     *            Ponto de referência que deve permanecer o mais próximo possível da
     *            localização que se encontrava antes da aplicação do zoom. Se for
     *            <code>null</code> considera o centro da área visível como ponto de
     *            referência.
     */
    public void changeZoom(double factor, Point reference) {
        Point pt = (reference == null)
                ? new Point((int) imgPanel.getVisibleRect().getCenterX(), (int) imgPanel.getVisibleRect().getCenterY())
                : reference;

        int sbx = scrollPane.getHorizontalScrollBar().getValue();
        int sby = scrollPane.getVerticalScrollBar().getValue();

        int iw = (int) (image.getWidth() * zoomFactor);
        int ih = (int) (image.getHeight() * zoomFactor);
        pt.x -= (imgPanel.getWidth() - iw) / 2;
        pt.y -= (imgPanel.getHeight() - ih) / 2;

        if (pt.x < 0) {
            pt.x = 0;
        }
        if (pt.y < 0) {
            pt.y = 0;
        }
        if (pt.x > image.getWidth() * zoomFactor) {
            pt.x = (int) (image.getWidth() * zoomFactor);
        }
        if (pt.y > image.getHeight() * zoomFactor) {
            pt.y = (int) (image.getHeight() * zoomFactor);
        }

        double currFactor = zoomFactor;
        zoomFactor *= factor;
        if (zoomFactor > 10) {
            zoomFactor = 10;
        }
        if (zoomFactor < 0.1) {
            zoomFactor = 0.1;
        }
        double f = zoomFactor / currFactor;
        int w = (int) (image.getWidth() * zoomFactor);
        int h = (int) (image.getHeight() * zoomFactor);
        Dimension d = new Dimension(w, h);
        imgPanel.setPreferredSize(d);
        imgPanel.revalidate();
        waitValid();

        sbx += (int) (pt.x * (f - 1));
        sby += (int) (pt.y * (f - 1));
        imgPanel.scrollRectToVisible(
                new Rectangle(sbx, sby, imgPanel.getVisibleRect().width, imgPanel.getVisibleRect().height));
        imgPanel.repaint();

        scrollPane.getVerticalScrollBar().setUnitIncrement(d.height / 50 + 1);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(d.width / 50 + 1);
    }

    private void waitValid() {
        for (int i = 0; i < 100; i++) {
            if (scrollPane.isValid()) {
                break;
            }
            scrollPane.validate();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Atualiza o fator de zoom.
     */
    private void updateZoomFactor(double limit, boolean vert, boolean horiz) {
        if (image == null) {
            return;
        }
        Container parent = ImageViewPanel.this;
        Insets insets = parent.getInsets();
        double sw = parent.getWidth() - insets.left - insets.right - 10;
        double sh = parent.getHeight() - insets.top - insets.bottom - 10;
        double zw = sw / image.getWidth();
        double zh = sh / image.getHeight();
        if (vert && horiz) {
            zoomFactor = Math.min(zw, zh);
        } else if (horiz) {
            if (zw > zh) {
                zw = (sw - scrollPane.getVerticalScrollBar().getWidth()) / image.getWidth();
            }
            zoomFactor = zw;
        } else if (vert) {
            if (zh > zw) {
                zh = (sh - scrollPane.getHorizontalScrollBar().getHeight()) / image.getHeight();
            }
            zoomFactor = zh;
        } else {
            zoomFactor = 1;
        }
        if (limit > 0 && zoomFactor > limit) {
            zoomFactor = limit;
        }
        Dimension d = new Dimension((int) (image.getWidth() * zoomFactor), (int) (image.getHeight() * zoomFactor));
        imgPanel.setPreferredSize(d);
        scrollPane.getVerticalScrollBar().setUnitIncrement(d.height / 50);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(d.width / 50);
    }

    public void adjustBrightness(float factor) {
        if (image != null) {
            if (factor > 0 && factor <= 100) {
                RescaleOp op = new RescaleOp(1 + factor * factor / 2000f, factor / 1.5f, null);
                image = op.filter(orgImage, null);
                imgPanel.repaint();
            } else {
                if (!image.equals(orgImage)) {
                    image = orgImage;
                    imgPanel.repaint();
                }
            }
        }
    }

    public void fitToWindow() {
        updateZoomFactor(0, true, true);
        imgPanel.revalidate();
        waitValid();
        imgPanel.repaint();
    }

    public void fitToWidth() {
        updateZoomFactor(0, false, true);
        imgPanel.revalidate();
        waitValid();
        imgPanel.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
        imgPanel.repaint();
    }
}
