package iped.parsers.usnjrnl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import iped.parsers.util.BaseItemSearchContext;

public abstract class AbstractPkgTest extends BaseItemSearchContext {

    protected ParseContext usnContext;
    protected EmbeddedUsnParser usntracker;

    protected ParseContext getContext(String file) throws IOException {
        usnContext = super.getContext(file);
        usntracker = new EmbeddedUsnParser();
        usnContext.set(Parser.class, usntracker);
        return usnContext;
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedUsnParser extends AbstractParser {

        protected List<String> contenttype = new ArrayList<String>();
        protected List<String> title = new ArrayList<String>();
        protected List<String> created = new ArrayList<String>();
        protected List<Metadata> metadata = new ArrayList<Metadata>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (contenttype.size() == 0)
                contenttype.add(metadata.get(StandardParser.INDEXER_CONTENT_TYPE));
            if (!contenttype.contains(UsnJrnlParser.USNJRNL_REGISTRY.toString()))
                contenttype.add(metadata.get(StandardParser.INDEXER_CONTENT_TYPE));

            title.add(metadata.get(TikaCoreProperties.TITLE));

            if (metadata.get(TikaCoreProperties.CREATED) != null)
                created.add(metadata.get(TikaCoreProperties.CREATED));

            this.metadata.add(metadata);
        }

    }
}
