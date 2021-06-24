package dpf.mg.udi.gpinf.shareazaparser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
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
import iped3.datasource.IDataSource;
import iped3.io.IItemBase;
import iped3.io.SeekableInputStream;
import iped3.search.IItemSearcher;
import iped3.util.ExtraProperties;

public abstract class AbstractPkgTest extends TestCase {
    protected ParseContext shareazalibdatContext;
    protected EmbeddedShareazaLibDatParser shareazalibdattracker;

    protected void setUp() throws Exception {
        super.setUp();

        shareazalibdattracker = new EmbeddedShareazaLibDatParser();
        shareazalibdatContext = new ParseContext();
        shareazalibdatContext.set(Parser.class, shareazalibdattracker);
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedShareazaLibDatParser extends AbstractParser {

        protected List<String> p2pregistrycount = new ArrayList<String>();
        protected List<String> csamhashhits = new ArrayList<String>();
        protected List<String> sharedhashes = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            if (metadata.get(ExtraProperties.P2P_REGISTRY_COUNT) != null)
                p2pregistrycount.add(metadata.get(ExtraProperties.P2P_REGISTRY_COUNT));
            if (metadata.get(ExtraProperties.CSAM_HASH_HITS) != null)
                csamhashhits.add(metadata.get(ExtraProperties.CSAM_HASH_HITS));
            if (metadata.get(ExtraProperties.SHARED_HASHES) != null)
                sharedhashes.add(metadata.get(ExtraProperties.SHARED_HASHES));
        }
    }

}
