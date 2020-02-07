package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class EmptyEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public void parseEmbedded(InputStream arg0, ContentHandler arg1, Metadata arg2, boolean arg3)
            throws SAXException, IOException {
        // ignore
    }

    @Override
    public boolean shouldParseEmbedded(Metadata arg0) {
        return false;
    }

}
