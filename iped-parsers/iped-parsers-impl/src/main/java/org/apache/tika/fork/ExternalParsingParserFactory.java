package org.apache.tika.fork;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserFactory;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public class ExternalParsingParserFactory extends ParserFactory {

    public ExternalParsingParserFactory(Map<String, String> args) {
        super(args);
    }

    @Override
    public Parser build() throws IOException, SAXException, TikaException {

        TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
        final IndexerDefaultParser recursiveParser = new IndexerDefaultParser();

        CompositeParser c = new CompositeParser(tikaConfig.getMediaTypeRegistry(),
                ((CompositeParser) tikaConfig.getParser()).getAllComponentParsers()) {
            private static final long serialVersionUID = 1L;

            @Override
            public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                    throws IOException, SAXException, TikaException {

                context.set(Parser.class, recursiveParser);
                EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class);
                if (extractor instanceof EmbeddedDocumentParser) {
                    ((EmbeddedDocumentParser) extractor).setContext(context);
                }
                TikaInputStream tis = null;
                if (stream instanceof InputStreamProxy2) {
                    // do not create more temp files
                    tis = ((InputStreamProxy2) stream).getTikaInputStream();
                    if (tis != null)
                        stream = tis;
                }

                MetadataFilterContentHandler metadataHandler = new MetadataFilterContentHandler(handler, metadata);
                try {
                    super.parse(stream, metadataHandler, metadata, context);

                } finally {
                    // emit metadata at end to write ALL metadata to handler with ForkParser
                    // including metadata populated at end of parsing
                    metadataHandler.emitMetadata();

                    IOUtils.closeQuietly(tis);
                }
            }
        };
        c.setFallback(recursiveParser.getFallback());
        return c;
    }

    private static class MetadataFilterContentHandler extends ContentHandlerDecorator {

        private static final String XHTML = "http://www.w3.org/1999/xhtml";
        private final Metadata metadata;
        private boolean filtered = false;

        public MetadataFilterContentHandler(ContentHandler handler, Metadata metadata) {
            super(handler);
            this.metadata = metadata;
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes atts) throws SAXException {
            filtered = false;
            if (!name.equals("meta")) {
                super.startElement(uri, localName, name, atts);
            }
        }

        @Override
        public void endElement(String uri, String localName, String name) throws SAXException {
            filtered = false;
            if (!name.equals("meta")) {
                super.endElement(uri, localName, name);
            } else
                filtered = true;
        }

        public void emitMetadata() throws SAXException {

            for (String name : metadata.names()) {
                for (String value : metadata.getValues(name)) {
                    if (value != null) {
                        AttributesImpl attributes = new AttributesImpl();
                        attributes.addAttribute("", "name", "name", "CDATA", name);
                        attributes.addAttribute("", "content", "content", "CDATA", value);
                        super.startElement(XHTML, "meta", "meta", attributes);
                        super.endElement(XHTML, "meta", "meta");
                        super.ignorableWhitespace("\n".toCharArray(), 0, 1);
                    }
                }
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {

            if (!filtered || length != 1 || ch[start] != '\n') {
                super.ignorableWhitespace(ch, start, length);
            }
            filtered = false;
        }

    }

}
