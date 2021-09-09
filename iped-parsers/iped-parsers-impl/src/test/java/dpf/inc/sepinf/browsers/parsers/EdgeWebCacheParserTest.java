package dpf.inc.sepinf.browsers.parsers;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;

public class EdgeWebCacheParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testEdgeBookmarkParser() throws IOException, SAXException, TikaException {

        EdgeWebCacheParser parser = new EdgeWebCacheParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, EdgeWebCacheParser.EDGE_WEB_CACHE.toString());
        ContentHandler handler = new BodyContentHandler(1 << 25);
        try (InputStream stream = getStream("test-files/test_WebCacheV01.dat")) {
            ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, getName(), false);
            edgeContext.set(ItemInfo.class, itemInfo);
            parser.getSupportedTypes(edgeContext);
            parser.setExtractEntries(true);
            parser.parse(stream, handler, metadata, edgeContext);
        }

        assertEquals(0, edgetracker.bookmarktitle.size());
        assertEquals(10200, edgetracker.visitUrl.size());
        assertEquals(10200, edgetracker.visitDate.size());

        assertEquals("https://technet.microsoft.com/favicon.ico", edgetracker.visitUrl.get(0));
        assertEquals(1427134328000L, edgetracker.visitDate.get(0).getTime());

        assertEquals("https://clients6.google.com/static/proxy.html?jsh=m%3B%2F_%2Fscs%2Fapps-static%2F_%2Fjs%2Fk%3Doz.gapi.en.Vh6dPmTLRzs.O%2Fm%3D__features__%2Fam%3DIQ%2Frt%3Dj%2Fd%3D1%2Ft%3Dzcms%2Frs%3DAGLTcCPMH84qo1WVX7OKMktu4bYHvIFfsw", edgetracker.visitUrl.get(10));
        assertEquals(1427142869000L, edgetracker.visitDate.get(10).getTime());

        assertEquals("Visited: informant@file://10.11.11.128/secured_drive/Secret%20Project%20Data/pricing%20decision/(secret_project)_pricing_decision.xlsx", edgetracker.visitUrl.get(100));
        assertEquals(1427142413000L, edgetracker.visitDate.get(100).getTime());

        assertEquals("ietld:tur.br", edgetracker.visitUrl.get(1000));
        assertEquals(1427037861000L, edgetracker.visitDate.get(1000).getTime());

        assertEquals("https://www.microsoft.com/global/onemscomsettings/publishingimages/footerimages/msn_symbol_clr_52x52.png", edgetracker.visitUrl.get(10000));
        assertEquals(1427134361000L, edgetracker.visitDate.get(10000).getTime());

        assertEquals(":2015032520150326: informant@http://www.piriform.com/ccleaner/download",
                edgetracker.visitUrl.get(10199));
        assertEquals(1427294892000L, edgetracker.visitDate.get(10199).getTime());

    }
}
