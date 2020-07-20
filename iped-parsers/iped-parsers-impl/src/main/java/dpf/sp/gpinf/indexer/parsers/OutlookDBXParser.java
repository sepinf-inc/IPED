package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class OutlookDBXParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(OutlookDBXParser.class);

    private static final AtomicBoolean logged = new AtomicBoolean();

    public static final String DBX_PARSER_IMPL_SYS_PROP = "dbxParserImpl"; //$NON-NLS-1$

    private static final String DEFAULT_DBX_PARSER_IMPL = "dpf.sp.gpinf.indexer.parsers.OutlookDBXParserImpl"; //$NON-NLS-1$

    private Parser parserImpl;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {

        try {
            String implName = System.getProperty(DBX_PARSER_IMPL_SYS_PROP, DEFAULT_DBX_PARSER_IMPL);
            parserImpl = (Parser) Class.forName(implName).newInstance();
            return parserImpl.getSupportedTypes(context);

        } catch (Throwable e) {
            if (!logged.getAndSet(true)) {
                String msg = "DBX parser not found, DBX mailboxes will NOT be expanded."; //$NON-NLS-1$
                // are we in analysis app?
                if (System.getProperty("iped.javaVersionChecked") != null)
                    LOGGER.warn(msg);
                else
                    LOGGER.error(msg);
            }
            return Collections.emptySet();
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        EmbeddedDocumentExtractorDecorator eded = new EmbeddedDocumentExtractorDecorator(extractor);

        context.set(EmbeddedDocumentExtractor.class, eded);
        parserImpl.parse(stream, handler, metadata, context);
        context.set(EmbeddedDocumentExtractor.class, extractor);

    }

    private class EmbeddedDocumentExtractorDecorator implements EmbeddedDocumentExtractor {

        private EmbeddedDocumentExtractor extractor;

        public EmbeddedDocumentExtractorDecorator(EmbeddedDocumentExtractor extractor) {
            this.extractor = extractor;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return extractor.shouldParseEmbedded(metadata);
        }

        @Override
        public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
                throws SAXException, IOException {

            if (metadata.get(Metadata.CONTENT_TYPE).equals("message/rfc822")) //$NON-NLS-1$
                metadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "message/rfc822"); //$NON-NLS-1$

            extractor.parseEmbedded(stream, handler, metadata, outputHtml);

        }

    }

}
