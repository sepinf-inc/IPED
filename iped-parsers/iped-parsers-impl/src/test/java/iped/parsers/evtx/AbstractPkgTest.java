package iped.parsers.evtx;

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

    protected ParseContext evtxContext;
    protected EmbeddedEvtxParser evtxTracker;

    protected ParseContext getContext(String file) throws IOException {
        evtxContext = super.getContext(file);
        evtxTracker = new EmbeddedEvtxParser();
        evtxContext.set(Parser.class, evtxTracker);
        return evtxContext;
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedEvtxParser extends AbstractParser {

        protected List<String> contentTypes = new ArrayList<>();
        protected List<String> titles = new ArrayList<>();
        protected List<Metadata> metadataList = new ArrayList<>();

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return new AutoDetectParser().getSupportedTypes(context);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            contentTypes.add(metadata.get(StandardParser.INDEXER_CONTENT_TYPE));
            titles.add(metadata.get(TikaCoreProperties.TITLE));
            metadataList.add(metadata);
        }
    }
}
