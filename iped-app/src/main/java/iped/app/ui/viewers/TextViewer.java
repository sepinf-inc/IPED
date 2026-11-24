package iped.app.ui.viewers;

import java.awt.image.BufferedImage;
import java.util.Set;

import iped.app.ui.TextParser;
import iped.io.IStreamSource;
import iped.viewers.ATextViewer;
import iped.viewers.DocumentViewport;

public class TextViewer extends ATextViewer implements DocumentViewport {

    private IStreamSource displayedSource;

    public TextViewer() {
        super();
    }

    @Override
    public void loadFile(IStreamSource content, String contentType, Set<String> highlightTerms) {

        displayedSource = content;
        if (content == null) {
            loadFile(content, null);
        } else {
            textParser = new TextParser(content, contentType, tmp);
            textParser.execute();
        }
    }

    @Override
    public BufferedImage captureDocument(IStreamSource expectedSource) {
        if (expectedSource == null || displayedSource != expectedSource || textTable.getRowCount() <= 0
                || !(textParser instanceof TextParser) || !((TextParser) textParser).isDone()
                || ((TextParser) textParser).isCancelled()) {
            throw new IllegalStateException("Text preview is not ready");
        }
        return DocumentViewport.snapshot(textTable);
    }
}
