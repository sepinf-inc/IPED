package dpf.sp.gpinf.indexer.parsers;

import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import dpf.sp.gpinf.indexer.parsers.util.IgnoreContentHandler;
import dpf.sp.gpinf.indexer.parsers.util.Messages;
import iped3.util.ExtraProperties;
import junit.framework.TestCase;

public class MBoxParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    

    
    @Test
    public void testMboxParsing() throws IOException, SAXException, TikaException{

            MboxParser parser = new MboxParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new DefaultHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream("test-files/testMBOX.mbox");
            parser.parse(stream, handler, metadata, context);


    }
    
    @Test
    public void testMboxMetadata() throws IOException, SAXException, TikaException{
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/testMBOX.mbox");
        Logger LOGGER = LoggerFactory.getLogger(MboxParser.class);
        final String MBOX_RECORD_DIVIDER = "From "; //$NON-NLS-1$
        int MAIL_MAX_SIZE = 50000000;
            
            
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();

            String charsetName = "windows-1252"; //$NON-NLS-1$
            InputStreamReader isr = new InputStreamReader(stream, charsetName);
            BufferedReader reader = new BufferedReader(isr, 100000);
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);

            String line;
            int count = 0;
            if (extractor.shouldParseEmbedded(metadata))
                do {
                    line = reader.readLine();

                    if (!line.startsWith(MBOX_RECORD_DIVIDER)) {
                        try {
                            count++;
                            ByteArrayOutputStream message = new ByteArrayOutputStream(100000);
                            do {

                                message.write(line.getBytes(charsetName));
                                message.write(0x0A);
                                line = reader.readLine();

                            } while (line != null && !line.startsWith(MBOX_RECORD_DIVIDER)
                                    && message.size() < MAIL_MAX_SIZE);

                            ByteArrayInputStream messageStream = new ByteArrayInputStream(message.toByteArray());
                            message = null;

                            Metadata mailMetadata = getMailMetadata(messageStream, count);
                            messageStream.reset();

                            if (extractor.shouldParseEmbedded(mailMetadata))
                                extractor.parseEmbedded(messageStream, xhtml, mailMetadata, true);

                        } catch (Throwable t) {
                            if (count == 1)
                                throw new TikaException("MboxParser Exception", t); //$NON-NLS-1$

                            LOGGER.warn("Error extracting email {} from {}\t{}", count, name, t.toString()); //$NON-NLS-1$
                        }

                    }

                } while (line != null && !Thread.currentThread().isInterrupted());

            reader.close();
            xhtml.endDocument();

        }
        

    static Metadata getMailMetadata(InputStream stream, int count) throws Exception {
            
            Metadata mailMetadata = new Metadata();
            mailMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822"); //$NON-NLS-1$

            try {
                RFC822Parser parser = new RFC822Parser();
                parser.parse(stream, new IgnoreContentHandler(), mailMetadata, new ParseContext());

            } catch (Exception e) {
                if (count == 1)
                    throw e;
            }

            String subject = mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT);
            if (subject == null || subject.trim().isEmpty())
                subject = Messages.getString("MboxParser.NoSubject"); //$NON-NLS-1$

            Metadata finalMetadata = new Metadata();
            finalMetadata.set(Metadata.CONTENT_TYPE, "message/rfc822"); //$NON-NLS-1$
            finalMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "message/rfc822"); //$NON-NLS-1$
            finalMetadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);
            finalMetadata.set(TikaCoreProperties.CREATED, mailMetadata.getDate(TikaCoreProperties.CREATED));


            switch (count) {
                case 1:
                assertEquals("denis silva <dsga.dsga@hotmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                assertEquals("guiwee@gmail.com <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                assertEquals("DADOS EXPERIMENTO 6", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                assertEquals("Peso da esfera de aço: 12,84 g Peso da esfera de plástico: 6,55 g OBS:"
                        + " As esferas foram pesadas separadamente dentro de um copo(...)", mailMetadata.get(ExtraProperties.MESSAGE_BODY));
                assertEquals("2014-11-15T01:17:35Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));
                break;
                case 2:
                    assertEquals("Marlos Andreúce <karmaggr@gmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                    assertEquals("Guille x J <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                    assertEquals("Re: Da uma olhada nesse jogo, acho que você vai gostar!", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                    assertEquals("zerei esse, filho. é legal, porém a fase é minúscula. é tipo uma prévia do que "
                            + "sairá esse ano. 2015-01-13 20:55 GMT-02:00 Guill(...)", mailMetadata.get(ExtraProperties.MESSAGE_BODY));
                    assertEquals("2015-01-14T13:49:24Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                    assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));
                break;
                case 3:
                    assertEquals("Guille x J <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                    assertEquals("Marlos Andreúce <karmaggr@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                    assertEquals("Da uma olhada nesse jogo, acho que você vai gostar!", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                    assertEquals("https://store.sonyentertainmentnetwork.com/#!/en-us/games/metal-gear-solid-v-"
                            + "ground-zeroes/cid=UP0101-CUSA00218_00-METALGEARSOL(...)", mailMetadata.get(ExtraProperties.MESSAGE_BODY));
                    assertEquals("2015-01-13T22:55:22Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                    assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));                    
                break;
                case 4:
                    //non ascii
                    assertEquals("Guille ● ω ● Jubs <or-gasm@hotmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                    assertEquals("guiwee@gmail.com <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                    assertEquals("Linf++", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                    assertEquals("", mailMetadata.get(ExtraProperties.MESSAGE_BODY));
                    assertEquals("2014-10-17T18:59:31Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                    assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));                    
                break;
                case 5:
                    assertEquals("Guille ● ω ● Jubs <or-gasm@hotmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                    assertEquals("guiwee@gmail.com <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                    assertEquals("Trabalho CB Incompleto", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                    assertEquals("Trabalho CB Incompleto", mailMetadata.get(ExtraProperties.MESSAGE_BODY));
                    assertEquals("2014-09-23T02:23:33Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                    assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));                    
                break;
                case 6:
                    //attachment Qualéamúsicapronto.zip
                    assertEquals("Guille ● ω ● Jubs <or-gasm@hotmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                    assertEquals("guiwee@gmail.com <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                    assertEquals("Trabalho cb completo", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                    assertEquals("2014-10-01T16:21:30Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                    assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));                    
                break;
                case 7:
                    assertEquals("denis silva <dsga.dsga@hotmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                    assertEquals("guiwee@gmail.com <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                    assertEquals("FÌSICA EXPERIMENTAL", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                    assertEquals("", mailMetadata.get(ExtraProperties.MESSAGE_BODY));
                    assertEquals("2014-09-26T11:04:14Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                    assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));                    
                break;
                case 8:
                    assertEquals("Guille x J <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_FROM));
                    assertEquals("Guilherme andreúce <guiwee@gmail.com>", mailMetadata.get(Metadata.MESSAGE_TO));
                    assertEquals("Trabalho 3 física experimental", mailMetadata.get(ExtraProperties.MESSAGE_SUBJECT));
                    assertEquals("É literalmente, só colocar os valores que a gente tem no papel. As únicas coisas que"
                            + " tem que fazer: os cálculos das médias. o c(...)", mailMetadata.get(ExtraProperties.MESSAGE_BODY));
                    assertEquals("2014-09-26T04:27:34Z", mailMetadata.get(ExtraProperties.MESSAGE_DATE));
                    assertEquals("message/rfc822", mailMetadata.get(Metadata.CONTENT_TYPE));                    
                break;
            }

            return finalMetadata;
            }
        }

 




