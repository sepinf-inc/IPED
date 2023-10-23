package iped.app.ui.viewers;

import java.util.Set;

import iped.app.ui.TextParser;
import iped.io.IStreamSource;
import iped.viewers.ATextViewer;

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
