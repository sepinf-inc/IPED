package dpf.inc.sepinf.browsers.parsers;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class SafariPlistParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testSafariPlistParser() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-plist").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.plist");
        parser.getSupportedTypes(safariContext);
        parser.setExtractEntries(true);
        parser.parse(stream, handler, metadata, safariContext);;
        
    }

    
    @Test
    public void testSafariPlistParserHistory() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-history").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_history.plist");
        parser.getSupportedTypes(safariContexthst);
        parser.setExtractEntries(true);
        parser.parse(stream, handler, metadata, safariContexthst);
        assertEquals(19, safaritrackerhst.historytitle.size());
        assertEquals(19, safaritrackerhst.historyaccessed.size());
        assertEquals(19, safaritrackerhst.historyvisitdate.size());
        assertEquals(19, safaritrackerhst.historyurl.size());
        
        assertEquals("/c/ - Anime/Cute - 4chan", safaritrackerhst.historytitle.get(0));
        assertEquals("4chan", safaritrackerhst.historytitle.get(1));
        assertEquals("CNN International - Breaking News, US News, World News and Video", safaritrackerhst.historytitle.get(2));
        assertEquals("Google", safaritrackerhst.historytitle.get(17));
        assertEquals("Falhou ao abrir a página", safaritrackerhst.historytitle.get(18));
        
        assertEquals("2021-07-22T18:13:38Z", safaritrackerhst.historyaccessed.get(0));
        assertEquals("2021-07-22T18:13:28Z", safaritrackerhst.historyaccessed.get(1));
        assertEquals("2021-07-22T18:13:21Z", safaritrackerhst.historyaccessed.get(2));
        assertEquals("2021-07-22T18:11:50Z", safaritrackerhst.historyaccessed.get(17));
        assertEquals("2021-07-22T18:11:39Z", safaritrackerhst.historyaccessed.get(18));
        
        assertEquals("2021-07-22T18:13:38Z", safaritrackerhst.historyvisitdate.get(0));
        assertEquals("2021-07-22T18:13:28Z", safaritrackerhst.historyvisitdate.get(1));
        assertEquals("2021-07-22T18:13:21Z", safaritrackerhst.historyvisitdate.get(2));
        assertEquals("2021-07-22T18:11:50Z", safaritrackerhst.historyvisitdate.get(17));
        assertEquals("2021-07-22T18:11:39Z", safaritrackerhst.historyvisitdate.get(18));
        
        assertEquals("https://boards.4channel.org/c/", safaritrackerhst.historyurl.get(0));   
        assertEquals("https://4chan.org/", safaritrackerhst.historyurl.get(1));   
        assertEquals("http://edition.cnn.com/", safaritrackerhst.historyurl.get(2));   
        assertEquals("http://www.google.com.br/", safaritrackerhst.historyurl.get(17));   
        assertEquals("https://www.apple.com/br/", safaritrackerhst.historyurl.get(18));
    }

    @Test
    public void testSafariPlistParserBookmarks() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-plist").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_bookmarks.plist");
        parser.setExtractEntries(true);
        parser.parse(stream, handler, metadata, safariContextbkm);
        assertEquals(30, safaritrackerbkm.bookmarktitle.size());
        assertEquals(30, safaritrackerbkm.bookmarkurl.size());
        
        assertEquals("Apple", safaritrackerbkm.bookmarktitle.get(0));
        assertEquals("Yahoo!", safaritrackerbkm.bookmarktitle.get(1));
        assertEquals("Google Maps", safaritrackerbkm.bookmarktitle.get(2));
        assertEquals("Windows Media", safaritrackerbkm.bookmarktitle.get(28));
        assertEquals("MSN.com", safaritrackerbkm.bookmarktitle.get(29));
        
        assertEquals("http://www.apple.com/br/", safaritrackerbkm.bookmarkurl.get(0));   
        assertEquals("http://br.yahoo.com/", safaritrackerbkm.bookmarkurl.get(1));   
        assertEquals("http://maps.google.com.br/", safaritrackerbkm.bookmarkurl.get(2));   
        assertEquals("http://www.microsoft.com/isapi/redir.dll?prd=ie&ar=windowsmedia", safaritrackerbkm.bookmarkurl.get(28));   
        assertEquals("http://www.microsoft.com/isapi/redir.dll?prd=ie&pver=6&ar=IStart", safaritrackerbkm.bookmarkurl.get(29));

    }
    
    @Test
    public void testSafariPlistParserDownloads() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-downloads").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_downloads.plist");
        parser.setExtractEntries(true);
        parser.parse(stream, handler, metadata, safariContextdwl);
        assertEquals(3, safaritrackerdwl.downloadurl.size());
        assertEquals(3, safaritrackerdwl.downloadlocalpath.size());
        assertEquals(3, safaritrackerdwl.downloadtotalbytes.size());
        assertEquals(3, safaritrackerdwl.downloadreceivedbytes.size());
        
        assertEquals("https://dl.google.com/tag/s/appguid%3D%7B8A69D345-D564-463C-AFF1-A69D9E530F96%7D%26iid%3D%7B11C004F6-5199-AABB-BDF6-596927E6C22A%7D%26lang%3Dpt-BR%26browser%3D0%26usagestats%3D0%26appname%3DGoogle%2520Chrome%26needsadmin%3Dprefers%26ap%3Dstable-arch_x86%26installdataindex%3Ddefaultbrowser/update2/installers/win_xp/ChromeSetup.exe", safaritrackerdwl.downloadurl.get(0));
        assertEquals("https://get.geo.opera.com/pub/opera-winxpvista/36.0.2130.80/win/Opera_winxpvista_36.0.2130.80_Setup.exe", safaritrackerdwl.downloadurl.get(1));
        assertEquals("https://download-installer.cdn.mozilla.net/pub/firefox/releases/52.9.0esr/win32-sha1/pt-BR/Firefox%20Setup%2052.9.0esr.exe", safaritrackerdwl.downloadurl.get(2));;
        
        assertEquals("C:\\Documents and Settings\\guileb\\Meus documentos\\ChromeSetup.exe", safaritrackerdwl.downloadlocalpath.get(0));
        assertEquals("C:\\Documents and Settings\\guileb\\Meus documentos\\Opera_winxpvista_36.0.2130.80_Setup.exe", safaritrackerdwl.downloadlocalpath.get(1));
        assertEquals("C:\\Documents and Settings\\guileb\\Meus documentos\\Firefox Setup 52.9.0esr.exe", safaritrackerdwl.downloadlocalpath.get(2));
        
        assertEquals("1321688", safaritrackerdwl.downloadtotalbytes.get(0));
        assertEquals("37689480", safaritrackerdwl.downloadtotalbytes.get(1));
        assertEquals("45531008", safaritrackerdwl.downloadtotalbytes.get(2));
        
        assertEquals("1321688", safaritrackerdwl.downloadreceivedbytes.get(0));   
        assertEquals("37689480", safaritrackerdwl.downloadreceivedbytes.get(1));   
        assertEquals("45531008", safaritrackerdwl.downloadreceivedbytes.get(2));   
    }
}
