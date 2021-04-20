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
            InputStream stream = getStream("test-files/testMBOX.mbox");
            parser.parse(stream, handler, metadata, context);
            

    }
    
    
    @Test
    public void testMboxMetadata() throws IOException, SAXException, TikaException{

        MboxParser parser = new MboxParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/testMBOX.mbox");
        parser.parse(stream, handler, metadata, mboxContext);
        
        assertEquals(8, mboxtracker.messageto.size());
        assertEquals(8, mboxtracker.messagefrom.size());
        assertEquals(8, mboxtracker.messagesubject.size());
        assertEquals(8, mboxtracker.messagebody.size());
        assertEquals(8, mboxtracker.messagedate.size());
        assertEquals(8, mboxtracker.contenttype.size());
        
        
        assertEquals("guiwee@gmail.com <guiwee@gmail.com>", mboxtracker.messageto.get(0));
        assertEquals("denis silva <dsga.dsga@hotmail.com>", mboxtracker.messagefrom.get(0));
        assertEquals("DADOS EXPERIMENTO 6", mboxtracker.messagesubject.get(0));
        assertEquals("Peso da esfera de aço: 12,84 g Peso da esfera de plástico: 6,55 g OBS:"
                        + " As esferas foram pesadas separadamente dentro de um copo(...)", mboxtracker.messagebody.get(0));
        assertEquals("2014-11-15T01:17:35Z", mboxtracker.messagedate.get(0));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(0));

        
        assertEquals("Guille x J <guiwee@gmail.com>", mboxtracker.messageto.get(1));
        assertEquals("Marlos Andreúce <karmaggr@gmail.com>", mboxtracker.messagefrom.get(1));
        assertEquals("Re: Da uma olhada nesse jogo, acho que você vai gostar!", mboxtracker.messagesubject.get(1));
        assertEquals("zerei esse, filho. é legal, porém a fase é minúscula. é tipo uma prévia do que "
                            + "sairá esse ano. 2015-01-13 20:55 GMT-02:00 Guill(...)", mboxtracker.messagebody.get(1));
        assertEquals("2015-01-14T13:49:24Z", mboxtracker.messagedate.get(1));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(1));
        
        
        assertEquals("Marlos Andreúce <karmaggr@gmail.com>", mboxtracker.messageto.get(2));
        assertEquals("Guille x J <guiwee@gmail.com>", mboxtracker.messagefrom.get(2));
        assertEquals("Da uma olhada nesse jogo, acho que você vai gostar!", mboxtracker.messagesubject.get(2));
        assertEquals("https://store.sonyentertainmentnetwork.com/#!/en-us/games/metal-gear-solid-v-"
                            + "ground-zeroes/cid=UP0101-CUSA00218_00-METALGEARSOL(...)", mboxtracker.messagebody.get(2));
        assertEquals("2015-01-13T22:55:22Z", mboxtracker.messagedate.get(2));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(2));   
        
        
                    //non ascii
        assertEquals("guiwee@gmail.com <guiwee@gmail.com>",  mboxtracker.messageto.get(3));
        assertEquals("Guille ● ω ● Jubs <or-gasm@hotmail.com>", mboxtracker.messagefrom.get(3));
        assertEquals("Linf++", mboxtracker.messagesubject.get(3));
        assertEquals("", mboxtracker.messagebody.get(3));
        assertEquals("2014-10-17T18:59:31Z", mboxtracker.messagedate.get(3));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(3));           
        
        
        assertEquals("guiwee@gmail.com <guiwee@gmail.com>",  mboxtracker.messageto.get(4));
        assertEquals("Guille ● ω ● Jubs <or-gasm@hotmail.com>", mboxtracker.messagefrom.get(4));
        assertEquals("Trabalho CB Incompleto", mboxtracker.messagesubject.get(4));
        assertEquals("Trabalho CB Incompleto", mboxtracker.messagebody.get(4));
        assertEquals("2014-09-23T02:23:33Z", mboxtracker.messagedate.get(4));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(4));  
        
        
                    //attachment Qualéamúsicapronto.zip
        assertEquals("guiwee@gmail.com <guiwee@gmail.com>",  mboxtracker.messageto.get(5));
        assertEquals("Guille ● ω ● Jubs <or-gasm@hotmail.com>", mboxtracker.messagefrom.get(5));
        assertEquals("Trabalho cb completo", mboxtracker.messagesubject.get(5));
        assertEquals("2014-10-01T16:21:30Z", mboxtracker.messagedate.get(5));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(5)); 
        
        
        assertEquals("guiwee@gmail.com <guiwee@gmail.com>",  mboxtracker.messageto.get(6));
        assertEquals("denis silva <dsga.dsga@hotmail.com>", mboxtracker.messagefrom.get(6));
        assertEquals("FÌSICA EXPERIMENTAL", mboxtracker.messagesubject.get(6));
        assertEquals("", mboxtracker.messagebody.get(6));
        assertEquals("2014-09-26T11:04:14Z", mboxtracker.messagedate.get(6));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(6)); 
        
        
        assertEquals("Guilherme andreúce <guiwee@gmail.com>",  mboxtracker.messageto.get(7));
        assertEquals("Guille x J <guiwee@gmail.com>", mboxtracker.messagefrom.get(7));
        assertEquals("Trabalho 3 física experimental", mboxtracker.messagesubject.get(7));
        assertEquals("É literalmente, só colocar os valores que a gente tem no papel. As únicas coisas que"
                            + " tem que fazer: os cálculos das médias. o c(...)", mboxtracker.messagebody.get(7));
        assertEquals("2014-09-26T04:27:34Z", mboxtracker.messagedate.get(7));
        assertEquals("message/rfc822", mboxtracker.contenttype.get(7));                    
            }
        }

 




