package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import dpf.sp.gpinf.indexer.desktop.TextParser;
import dpf.sp.gpinf.indexer.ui.fileViewer.util.AppSearchParams;
import dpf.sp.gpinf.indexer.util.StreamSource;
import java.util.Set;


public class TextViewer extends ATextViewer {
  
  public TextViewer(AppSearchParams params) {
    super(params);
  }

  @Override
  public void loadFile(StreamSource content, String contentType, Set<String> highlightTerms) {

    if (content == null) {
      loadFile(content, null);
    } else {
      textParser = new TextParser(appSearchParams, content, contentType, tmp);
      textParser.execute();
    }
  }
}
