package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public class WhatsAppParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testWhatsAppParser() throws IOException, SAXException, TikaException{

        WhatsAppParser parser = new WhatsAppParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-db");
        metadata.add(Metadata.RESOURCE_NAME_KEY, "msgstore-d4");
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/whatsapp/msgstore.db");
        parser.setExtractMessages(true);
        parser.setMergeDbs(false);
        parser.getSupportedTypes(whatsappContext);
        parser.parse(stream, handler, metadata, whatsappContext);
        assertEquals(57, whatsapptracker.title.size());
    }

    @Test
  public void testWhatsAppParserWADB() throws IOException, SAXException, TikaException{

      WhatsAppParser parser = new WhatsAppParser();
      Metadata metadata = new Metadata();
      metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-whatsapp-wadb");
      ContentHandler handler = new BodyContentHandler(1<<20);
      InputStream stream = getStream("test-files/whatsapp/wa.db");
      parser.setExtractMessages(true);
      parser.setMergeDbs(false);
      parser.getSupportedTypes(whatsappContext);
      parser.parse(stream, handler, metadata, whatsappContext);
  }

    
}
