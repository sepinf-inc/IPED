package dpf.sp.gpinf.indexer.ui.fileViewer.frames;

import java.util.Set;

import dpf.sp.gpinf.indexer.desktop.TextParser;
import iped.io.IStreamSource;

public class TextViewer extends ATextViewer {

    public TextViewer() {
        super();
    }

    @Override
    public void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms) {

        if (content == null) {
            loadFile(content, null);
        } else {
            textParser = new TextParser(content, contentType, tmp);
            textParser.execute();
        }
    }
}
