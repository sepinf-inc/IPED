package dpf.sp.gpinf.indexer.parsers;



import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

    
public class OutlookPSTParserTest extends AbstractPkgTest{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    @Test
    public void testOutlookPSTParser() throws IOException, SAXException, TikaException{

        OutlookPSTParser parser = new OutlookPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_sample.pst");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        assertEquals("thisisatest", metadata.get(TikaCoreProperties.TITLE));
         
    }
    
    
    @Test
    public void testOutlookPSTParserMetadata() throws IOException, SAXException, TikaException{

        OutlookPSTParser parser = new OutlookPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        InputStream stream = getStream("test-files/test_sample.pst");
        parser.parse(stream, handler, metadata, pstContext);
        
        assertEquals(27, psttracker.foldertitle.size());
        assertEquals(1, psttracker.foldercreated.size());
        assertEquals(0, psttracker.foldermodified.size());
        assertEquals(18, psttracker.foldercomment.size());
        assertEquals(4, psttracker.messagesubject.size());
        assertEquals(4, psttracker.messagebody.size());
        assertEquals(3, psttracker.messagedate.size());
        assertEquals(1, psttracker.attachmentname.size());
        assertEquals(0, psttracker.useraccount.size());
        assertEquals(1, psttracker.username.size());
        assertEquals(1, psttracker.useremail.size());
        assertEquals(1, psttracker.userphone.size());
        assertEquals(1, psttracker.useraddress.size());
        assertEquals(0, psttracker.userbirth.size());
        assertEquals(1, psttracker.userorganization.size());
        assertEquals(1, psttracker.userurls.size());
        assertEquals(1, psttracker.usernotes.size());
        assertEquals(32, psttracker.contentmd5.size());
    }
    @Test
    public void testOutlookPSTParserMetadataSimpleMessage() throws IOException, SAXException, TikaException{

        OutlookPSTParser parser = new OutlookPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.pst");
        parser.parse(stream, handler, metadata, pstContext);

        
        assertEquals("Caixa de Entrada", psttracker.foldertitle.get(9));
        assertEquals("Pasta Caixa de Entrada", psttracker.foldercomment.get(4));
        assertEquals("2021-04-27", psttracker.foldercreated.get(0).substring(0,10));
        assertEquals("this is a test message", psttracker.messagesubject.get(0));
        assertEquals("Hello, this\nis a test message.\n\n\n  ", psttracker.messagebody.get(0));
    }
    @Test
    public void testOutlookPSTParserMetadataRealMessages() throws IOException, SAXException, TikaException{

        OutlookPSTParser parser = new OutlookPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.pst");
        parser.parse(stream, handler, metadata, pstContext);

        assertEquals("Re: [sepinf-inc/IPED] WIP Parsers tests (#481)", psttracker.messagesubject.get(1));
        assertEquals("Solved by using my abstrackpackage instead of "
                + "hardcoding the parser. Sorry, my mistake. Thanks for the tip!! "
                + "Good. I also confu(...)", psttracker.messagebody.get(1));
        assertEquals("2021-04-26", psttracker.messagedate.get(0).substring(0,10));
        
        assertEquals("Re: Solicita documentação para contrato de estágio na DITEC/PF.", psttracker.messagesubject.get(2));
        assertEquals("Bom dia, Guilherme! A UnB assinou o TCE/PA? At.te,"
                + " ELIZÃ‚NGELA RIBEIRO DE ANDRADE Fiscal do Contrato substituta"
                + " NAD/SELO/DITEC/(...)", psttracker.messagebody.get(2));
        assertEquals("2021-03-29", psttracker.messagedate.get(1).substring(0,10));
    }
    @Test
    public void testOutlookPSTParserMetadataMessageAttach() throws IOException, SAXException, TikaException{

        OutlookPSTParser parser = new OutlookPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.pst");
        parser.parse(stream, handler, metadata, pstContext); 
        
        assertEquals("This is a test message with attachment!!! ", psttracker.messagesubject.get(3));
        assertEquals("Hi there, it’s me again. Take a look\n"
                + "in this attachment. It is awesome.", psttracker.messagebody.get(3));
        assertEquals("2021-04-27", psttracker.messagedate.get(2).substring(0,10));
        assertEquals("lionel-animals-to-follow-on-instagram-1568319926.jpg", psttracker.attachmentname.get(0));
    }
    @Test
    public void testOutlookPSTParserMetadataUserInfo() throws IOException, SAXException, TikaException{

        OutlookPSTParser parser = new OutlookPSTParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.pst");
        parser.parse(stream, handler, metadata, pstContext);

        assertEquals("Sr. Guilherme Andreúce Monteiro", psttracker.username.get(0));
        assertEquals("guilhermeandreuce@gmail.com", psttracker.useremail.get(0));
        assertEquals("+55 (61) 986778855", psttracker.userphone.get(0));
        assertEquals("Condomínio da imaginação ruas dos bobos número 0", psttracker.useraddress.get(0));
        assertEquals("Polícia Federal", psttracker.userorganization.get(0));
        assertEquals("github.com/streeg", psttracker.userurls.get(0));
        
    }
    


}
