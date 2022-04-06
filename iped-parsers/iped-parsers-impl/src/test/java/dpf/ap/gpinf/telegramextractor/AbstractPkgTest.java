package dpf.ap.gpinf.telegramextractor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;
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
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.IItemBase;
import iped3.datasource.IDataSource;
import iped3.io.ISeekableInputStreamFactory;
import iped3.io.SeekableInputStream;
import iped3.search.IItemSearcher;
import iped3.util.ExtraProperties;

public abstract class AbstractPkgTest extends TestCase {

    protected ParseContext telegramContext;
    protected EmbeddedTelegramParser telegramtracker;
    protected ParseContext telegramUserContext;
    protected EmbeddedTelegramUserParser telegramusertracker;

    protected void setUp() throws Exception {
        super.setUp();

        IItemSearcher itemSearcher = new IItemSearcher() {

            @Override
            public void close() throws IOException {
                // no op
            }

            @Override
            public Iterable<IItemBase> searchIterable(String luceneQuery) {
                return Collections.emptyList();
            }

            @Override
            public List<IItemBase> search(String luceneQuery) {
                return Collections.emptyList();
            }

            @Override
            public String escapeQuery(String string) {
                return string;
            }
        };
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, getName(), false);

        telegramtracker = new EmbeddedTelegramParser();
        telegramContext = new ParseContext();
        telegramContext.set(Parser.class, telegramtracker);
        telegramContext.set(ItemInfo.class, itemInfo);
        telegramContext.set(IItemSearcher.class, itemSearcher);

        telegramusertracker = new EmbeddedTelegramUserParser();
        telegramUserContext = new ParseContext();
        telegramUserContext.set(Parser.class, telegramusertracker);
        telegramUserContext.set(ItemInfo.class, itemInfo);
        telegramUserContext.set(IItemSearcher.class, itemSearcher);

    }

    @SuppressWarnings("serial")
    protected static class EmbeddedTelegramParser extends AbstractParser {

        protected List<String> title = new ArrayList<String>();
        protected List<String> username = new ArrayList<String>();
        protected List<String> userphone = new ArrayList<String>();
        protected List<String> useraccount = new ArrayList<String>();
        protected List<String> usernotes = new ArrayList<String>();
        protected List<String> participants = new ArrayList<String>();
        protected List<String> messagefrom = new ArrayList<String>();
        protected List<String> messagebody = new ArrayList<String>();
        protected List<String> messageto = new ArrayList<String>();
        protected List<String> messagedate = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            if (metadata.get(TikaCoreProperties.TITLE) != null)
                title.add(metadata.get(TikaCoreProperties.TITLE));
            if (metadata.get(ExtraProperties.USER_NAME) != null && metadata.get(ExtraProperties.USER_NAME) != "")
                username.add(metadata.get(ExtraProperties.USER_NAME));
            if (metadata.get(ExtraProperties.USER_PHONE) != null)
                userphone.add(metadata.get(ExtraProperties.USER_PHONE));
            if (metadata.get(ExtraProperties.USER_ACCOUNT) != null)
                useraccount.add(metadata.get(ExtraProperties.USER_ACCOUNT));
            if (metadata.get(ExtraProperties.USER_NOTES) != null)
                usernotes.add(metadata.get(ExtraProperties.USER_NOTES));
            if (metadata.get(ExtraProperties.PARTICIPANTS) != null)
                participants.add(metadata.get(ExtraProperties.PARTICIPANTS));
            if (metadata.get(org.apache.tika.metadata.Message.MESSAGE_FROM) != null)
                messagefrom.add(metadata.get(org.apache.tika.metadata.Message.MESSAGE_FROM));
            if (metadata.get(ExtraProperties.MESSAGE_BODY) != null)
                messagebody.add(metadata.get(ExtraProperties.MESSAGE_BODY));
            if (metadata.get(org.apache.tika.metadata.Message.MESSAGE_TO) != null)
                messageto.add(metadata.get(org.apache.tika.metadata.Message.MESSAGE_TO));
            if (metadata.get(ExtraProperties.MESSAGE_DATE) != null)
                messagedate.add(metadata.get(ExtraProperties.MESSAGE_DATE));
        }
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedTelegramUserParser extends AbstractParser {

        protected List<String> title = new ArrayList<String>();
        protected List<String> username = new ArrayList<String>();
        protected List<String> userphone = new ArrayList<String>();
        protected List<String> useraccount = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            if (metadata.get(TikaCoreProperties.TITLE) != null)
                title.add(metadata.get(TikaCoreProperties.TITLE));
            if (metadata.get(ExtraProperties.USER_NAME) != null && metadata.get(ExtraProperties.USER_NAME) != "")
                username.add(metadata.get(ExtraProperties.USER_NAME));
            if (metadata.get(ExtraProperties.USER_PHONE) != null)
                userphone.add(metadata.get(ExtraProperties.USER_PHONE));
            if (metadata.get(ExtraProperties.USER_ACCOUNT) != null)
                useraccount.add(metadata.get(ExtraProperties.USER_ACCOUNT));
        }
    }
}
