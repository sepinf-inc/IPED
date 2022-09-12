package iped.parsers.mail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.properties.ExtraProperties;

public class MSGParser extends OfficeParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(POIFSDocumentType.OUTLOOK.getType());
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        DelegatingExtractor delegate = null;
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class);
        if (extractor != null) {
            delegate = new DelegatingExtractor(extractor);
            context.set(EmbeddedDocumentExtractor.class, delegate);
        }

        try {
            super.parse(stream, handler, metadata, context);

        } finally {
            if (delegate != null) {
                metadata.set(ExtraProperties.MESSAGE_ATTACHMENT_COUNT, delegate.attachCount);
                context.set(EmbeddedDocumentExtractor.class, extractor);
            }
        }

    }

    private class DelegatingExtractor implements EmbeddedDocumentExtractor {

        private EmbeddedDocumentExtractor delegate;
        private int attachCount = 0;

        private DelegatingExtractor(EmbeddedDocumentExtractor delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return delegate.shouldParseEmbedded(metadata);
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                throws SAXException, IOException {

            metadata.set(ExtraProperties.MESSAGE_IS_ATTACHMENT, Boolean.TRUE.toString());
            delegate.parseEmbedded(stream, handler, metadata, outputHtml);
            attachCount++;
        }

    }

}