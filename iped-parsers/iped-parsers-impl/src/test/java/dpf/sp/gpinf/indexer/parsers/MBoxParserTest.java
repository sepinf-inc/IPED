package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
public class MBoxParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
     
    @Test
    public void testMboxParsing() throws IOException, SAXException, TikaException{

            MboxParser parser = new MboxParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream("test-files/test_mbox.mbox");
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);
            

    }
    
    
    @Test
    public void testMboxMetadata() throws IOException, SAXException, TikaException{

        MboxParser parser = new MboxParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mbox.mbox");
        parser.parse(stream, handler, metadata, mboxContext);
        
        assertEquals(8, mboxtracker.messagesubject.size());
        assertEquals(8, mboxtracker.contenttype.size());
        assertEquals(8, mboxtracker.contentmd5.size());
        
        assertEquals("DADOS EXPERIMENTO 6", mboxtracker.messagesubject.get(0));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(0));
        assertEquals("C445685C851ED833DE708F74B5C7E9B0", mboxtracker.contentmd5.get(0));
        
        assertEquals("Re: Da uma olhada nesse jogo, acho que você vai gostar!", mboxtracker.messagesubject.get(1));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(1));
        assertEquals("C5C26209B5CF4F369B4CDD754270A82B", mboxtracker.contentmd5.get(1));
        
        assertEquals("Da uma olhada nesse jogo, acho que você vai gostar!", mboxtracker.messagesubject.get(2));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(2));
        assertEquals("BD869B271E3037E88739CE5D8131650C", mboxtracker.contentmd5.get(2));   
        
        assertEquals("Linf++", mboxtracker.messagesubject.get(3));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(3));
        assertEquals("104907B3FDC01AD771263CA0FC79728A", mboxtracker.contentmd5.get(3));           
        
        assertEquals("Trabalho CB Incompleto", mboxtracker.messagesubject.get(4));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(4));
        assertEquals("CFFC46163BB36FC3B5E730F2A7E27AB0", mboxtracker.contentmd5.get(4));  
        
                    //attachment Qualéamúsicapronto.zip
        assertEquals("Trabalho cb completo", mboxtracker.messagesubject.get(5));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(5));
        assertEquals("59DD6F57C182E2F67F4C6857073ECFB4", mboxtracker.contentmd5.get(5)); 
                
        assertEquals("FÌSICA EXPERIMENTAL", mboxtracker.messagesubject.get(6));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(6));
        assertEquals("AE06FBC3694648397C563ACEC269515E", mboxtracker.contentmd5.get(6));
        
        assertEquals("Trabalho 3 física experimental", mboxtracker.messagesubject.get(7));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(7));
        assertEquals("D14BA8E896A274896CD835092FECB956", mboxtracker.contentmd5.get(7));
            }
        }

 




