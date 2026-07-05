package iped.parsers.signal;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
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

import iped.data.IItemReader;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ItemInfo;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import junit.framework.TestCase;

public abstract class AbstractPkgTest extends TestCase {

    protected ParseContext signalContext;
    protected EmbeddedSignalParser signalTracker;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        IItemSearcher noopSearcher = new IItemSearcher() {
            @Override
            public void close() throws IOException {}

            @Override
            public Iterable<IItemReader> searchIterable(String luceneQuery) {
                return Collections.emptyList();
            }

            @Override
            public List<IItemReader> search(String luceneQuery) {
                return Collections.emptyList();
            }

            @Override
            public String escapeQuery(String string) {
                return string;
            }
        };

        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, getName(), false);

        signalTracker = new EmbeddedSignalParser();
        signalContext = new ParseContext();
        signalContext.set(Parser.class, signalTracker);
        signalContext.set(ItemInfo.class, itemInfo);
        signalContext.set(IItemSearcher.class, noopSearcher);
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedSignalParser extends AbstractParser {

        protected List<String> contentTypes     = new ArrayList<>();
        protected List<String> titles           = new ArrayList<>();
        protected List<String> messageBodies    = new ArrayList<>();
        protected List<String> messageFroms     = new ArrayList<>();
        protected List<String> messageTos       = new ArrayList<>();
        protected List<String> participants     = new ArrayList<>();
        protected List<String> messageDates     = new ArrayList<>();
        protected List<String> userAccountTypes = new ArrayList<>();
        protected List<String> groupIds         = new ArrayList<>();

        @Override
        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return new AutoDetectParser().getSupportedTypes(context);
        }

        @Override
        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            String ct = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);
            if (ct != null)
                contentTypes.add(ct);

            String title = metadata.get(TikaCoreProperties.TITLE);
            if (title != null)
                titles.add(title);

            String body = metadata.get(ExtraProperties.MESSAGE_BODY);
            if (body != null)
                messageBodies.add(body);

            String from = metadata.get(org.apache.tika.metadata.Message.MESSAGE_FROM);
            if (from != null)
                messageFroms.add(from);

            String to = metadata.get(org.apache.tika.metadata.Message.MESSAGE_TO);
            if (to != null)
                messageTos.add(to);

            String p = metadata.get(ExtraProperties.PARTICIPANTS);
            if (p != null)
                participants.add(p);

            String date = metadata.get(ExtraProperties.MESSAGE_DATE);
            if (date != null)
                messageDates.add(date);

            String uat = metadata.get(ExtraProperties.USER_ACCOUNT_TYPE);
            if (uat != null)
                userAccountTypes.add(uat);

            String gid = metadata.get(ExtraProperties.GROUP_ID);
            if (gid != null)
                groupIds.add(gid);
        }
    }
}
