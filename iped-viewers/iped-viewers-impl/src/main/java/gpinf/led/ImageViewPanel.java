package gpinf.led;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
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
import java.io.File;
import java.io.IOException;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

import dpf.sp.gpinf.indexer.util.ImageUtil;

/**
 * Painel especializado em exibição de uma imagem. Já inclui rolagem e zoom da imagem.
 *
 * @author Wladimir Leite (GPINF/SP)
 */
public class ImageViewPanel extends JPanel {

  /**
   * Imagem que será apresentada.
   */
  private BufferedImage image = null;

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
   * Construtor.
   */
  public ImageViewPanel() {
    super(new BorderLayout());
    imgPanel = new JPanel() {
      /**
       * Identificador utilizado para serialização.
       */
      private static final long serialVersionUID = -7607117377739877804L;

      /**
       * Método sobrescrito, reponsável por desenhar o conteúdo do painel.
       *
       * @param g contexto gráfico a ser utilizado
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
    scrollPane = new JScrollPane(imgPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    add(BorderLayout.CENTER, scrollPane);

    imgPanel.addMouseWheelListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent evt) {
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
      public void mouseWheelMoved(MouseWheelEvent evt) {
        if (image == null) {
          return;
        }
        changeZoom(1 - evt.getWheelRotation() / 5f, evt.getPoint());
      }
    });
  }

  /**
   * Altera imagem que está sendo exibida.
   *
   * @param img Imagem a ser exibida. Valor nulo é aceito, indicando que nenhuma imagem deve ser
   * exibida.
   * @return Indicador se uma imagem válida foi carregada com sucesso.
   */
  public boolean setImage(BufferedImage img) {
    image = img;
    zoomFactor = 1;
    if (image != null) {
      updateZoomFactor();

      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    } else {
      Insets insets = scrollPane.getInsets();
      int sh = ImageViewPanel.this.getHeight() - insets.top - insets.bottom;
      int sw = ImageViewPanel.this.getWidth() - insets.left - insets.right;
      imgPanel.setPreferredSize(new Dimension(sw, sh));
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
      imgPanel.setCursor(Cursor.getDefaultCursor());
    }
    imgPanel.revalidate();
    return image != null;
  }

  /**
   * Altera o fator de zoom da imagem.
   *
   * @param factor Fator multiplicador de ampliação (se maior que 1) ou redução (se menor que 1) a
   * ser aplicado.
   * @param reference Ponto de refer�ncia que deve permanecer o mais próximo possível da localização
   * que se encontrava antes da aplicação do zoom. Se for <code>null</code> considera o centro da
   * área visível como ponto de referência.
   */
  public void changeZoom(double factor, Point reference) {
    Point pt = (reference == null) ? new Point((int) imgPanel.getVisibleRect().getCenterX(), (int) imgPanel.getVisibleRect().getCenterY()) : reference;

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

    // Aguarda validação.
    for (int i = 0; i < 100; i++) {
      if (scrollPane.isValid()) {
        break;
      }
      scrollPane.validate();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
      }
    }
    sbx += (int) (pt.x * (f - 1));
    sby += (int) (pt.y * (f - 1));
    imgPanel.scrollRectToVisible(new Rectangle(sbx, sby, imgPanel.getVisibleRect().width, imgPanel.getVisibleRect().height));
    imgPanel.repaint();

    scrollPane.getVerticalScrollBar().setUnitIncrement(d.height / 50 + 1);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(d.width / 50 + 1);
  }

  /**
   * Atualiza o fator de zoom.
   */
  private void updateZoomFactor() {
    Container parent = ImageViewPanel.this;
    Insets insets = parent.getInsets();
    int sw = parent.getWidth() - insets.left - insets.right - 10;
    int sh = parent.getHeight() - insets.top - insets.bottom - 10;

    zoomFactor = Math.min(((double) sw) / image.getWidth(), ((double) sh) / image.getHeight());
    if (zoomFactor > 1) {
      zoomFactor = 1;
    }

    Dimension d = new Dimension((int) (image.getWidth() * zoomFactor), (int) (image.getHeight() * zoomFactor));
    imgPanel.setPreferredSize(d);
    scrollPane.getVerticalScrollBar().setUnitIncrement(d.height / 50);
    scrollPane.getHorizontalScrollBar().setUnitIncrement(d.width / 50);
  }
}
