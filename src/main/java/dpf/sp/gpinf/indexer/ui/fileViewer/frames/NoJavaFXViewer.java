package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.util.Set;

import javax.swing.JLabel;

import dpf.sp.gpinf.indexer.util.StreamSource;

public class NoJavaFXViewer extends Viewer {

  final static String NO_JAVAFX_MSG = "<html>Visualização não suportada. Atualize o Java para a versão 7u06 ou superior.</html>";

  public NoJavaFXViewer() {
    this.getPanel().add(new JLabel(NO_JAVAFX_MSG));
  }

  @Override
  public String getName() {
    return "NoJavaFX";
  }

  @Override
  public boolean isSupportedType(String contentType) {
    return contentType.equals("text/html") || contentType.equals("application/xhtml+xml") || contentType.equals("text/asp") || contentType.equals("text/aspdotnet")
        || contentType.equals("message/rfc822") || contentType.equals("message/x-emlx") || contentType.equals("message/outlook-pst") || contentType.equals("application/messenger-plus");
  }

  @Override
  public void init() {

  }

  @Override
  public void dispose() {
  }

  @Override
  public void loadFile(StreamSource content, Set<String> highlightTerms) {
  }

  @Override
  public void scrollToNextHit(boolean forward) {
    // TODO Auto-generated method stub

  }

}
