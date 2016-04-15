package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import gpinf.led.ImageUtil;
import gpinf.led.ImageViewPanel;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.swing.SwingUtilities;

import dpf.sp.gpinf.indexer.util.StreamSource;
import dpf.sp.gpinf.indexer.util.GraphicsMagicConverter;
import dpf.sp.gpinf.indexer.util.IOUtil;

public class ImageViewer extends Viewer {

  /**
   *
   */
  private static final long serialVersionUID = -6992477374808107778L;

  private ImageViewPanel imagePanel;

  public ImageViewer() {
    super(new GridLayout());
    imagePanel = new ImageViewPanel();
    this.getPanel().add(imagePanel);

  }

  @Override
  public String getName() {
    return "Imagem";
  }

  @Override
  public boolean isSupportedType(String contentType) {
    return /*contentType.equals("image/gif") || 
         contentType.equals("image/jpeg") || 
         contentType.equals("image/x-ms-bmp") ||
         contentType.equals("image/png");
         */ contentType.startsWith("image");
    //contentType.endsWith("msmetafile") || 
    //contentType.endsWith("x-emf"); 
  }

  @Override
  public void loadFile(StreamSource content, Set<String> highlightTerms) {

    BufferedImage image = null;
    InputStream in = null;
    if (content != null) {
      try {
        in = new BufferedInputStream(content.getStream());
        image = ImageUtil.read(in);

      } catch (IOException e) {

        InputStream in2 = null;
        try {
          in2 = new BufferedInputStream(content.getStream());
          image = new GraphicsMagicConverter().getImage(in2, 0);

        } catch (Exception e1) {
          e1.printStackTrace();
        } finally {
          IOUtil.closeQuietly(in2);
        }

      } finally {
        IOUtil.closeQuietly(in);
      }
    }

    final BufferedImage img = image;

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        imagePanel.setImage(img);
      }
    });

  }

  @Override
  public void init() {
    // TODO Auto-generated method stub

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
    // TODO Auto-generated method stub

  }

  @Override
  public void scrollToNextHit(boolean forward) {
    // TODO Auto-generated method stub

  }

}
