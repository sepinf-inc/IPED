package iped.parsers.browsers.edge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
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
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.ItemInfo;
import iped.properties.ExtraProperties;

public class EdgeWebCacheParserTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testEdgeBookmarkParser() throws IOException, SAXException, TikaException {

        EmbeddedEdgeParser edgetracker = new EmbeddedEdgeParser();
        ParseContext edgeContext = new ParseContext();
        edgeContext.set(Parser.class, edgetracker);

        EdgeWebCacheParser parser = new EdgeWebCacheParser();
        assumeFalse(parser.getSupportedTypes(edgeContext).isEmpty());

        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, EdgeWebCacheParser.EDGE_WEB_CACHE.toString());
        ContentHandler handler = new BodyContentHandler(1 << 25);
        String fileName = "test_WebCacheV01.dat";
        ItemInfo itemInfo = new ItemInfo(0, "", null, null, fileName, false);
        edgeContext.set(ItemInfo.class, itemInfo);
        parser.setExtractEntries(true);

        try (InputStream stream = getStream("test-files/" + fileName)) {

            parser.parse(stream, handler, metadata, edgeContext);

            assertEquals(0, edgetracker.bookmarktitle.size());
            assertEquals(10200, edgetracker.visitUrl.size());
            assertEquals(10200, edgetracker.visitDate.size());

            assertEquals("https://technet.microsoft.com/favicon.ico", edgetracker.visitUrl.get(0));
            assertEquals(1427134328000L, edgetracker.visitDate.get(0).getTime());

            assertEquals(
                    "https://clients6.google.com/static/proxy.html?jsh=m%3B%2F_%2Fscs%2Fapps-static%2F_%2Fjs%2Fk%3Doz.gapi.en.Vh6dPmTLRzs.O%2Fm%3D__features__%2Fam%3DIQ%2Frt%3Dj%2Fd%3D1%2Ft%3Dzcms%2Frs%3DAGLTcCPMH84qo1WVX7OKMktu4bYHvIFfsw",
                    edgetracker.visitUrl.get(10));
            assertEquals(1427142869000L, edgetracker.visitDate.get(10).getTime());

            assertEquals(
                    "Visited: informant@file://10.11.11.128/secured_drive/Secret%20Project%20Data/pricing%20decision/(secret_project)_pricing_decision.xlsx",
                    edgetracker.visitUrl.get(100));
            assertEquals(1427142413000L, edgetracker.visitDate.get(100).getTime());

            assertEquals("ietld:tur.br", edgetracker.visitUrl.get(1000));
            assertEquals(1427037861000L, edgetracker.visitDate.get(1000).getTime());

            assertEquals(
                    "https://www.microsoft.com/global/onemscomsettings/publishingimages/footerimages/msn_symbol_clr_52x52.png",
                    edgetracker.visitUrl.get(10000));
            assertEquals(1427134361000L, edgetracker.visitDate.get(10000).getTime());

            assertEquals(":2015032520150326: informant@http://www.piriform.com/ccleaner/download",
                    edgetracker.visitUrl.get(10199));
            assertEquals(1427294892000L, edgetracker.visitDate.get(10199).getTime());

        }

    }

    @SuppressWarnings("serial")
    protected static class EmbeddedEdgeParser extends AbstractParser {

        protected List<String> bookmarktitle = new ArrayList<String>();
        protected List<String> bookmarkmodified = new ArrayList<String>();
        protected List<String> visitUrl = new ArrayList<>();
        protected List<Date> visitDate = new ArrayList<>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(TikaCoreProperties.TITLE) != null)
                bookmarktitle.add(metadata.get(TikaCoreProperties.TITLE));

            if (metadata.get(TikaCoreProperties.MODIFIED) != null)
                bookmarkmodified.add(metadata.get(TikaCoreProperties.MODIFIED));

            if (metadata.get(ExtraProperties.URL) != null)
                visitUrl.add(metadata.get(ExtraProperties.URL));

            if (metadata.get(ExtraProperties.VISIT_DATE) != null)
                visitDate.add(metadata.getDate(ExtraProperties.VISIT_DATE));

        }
    }
}
