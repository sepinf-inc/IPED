package dpf.sp.gpinf.indexer.parsers.ufed;


import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class UFEDChatParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testUFEDChatParsing() throws IOException, SAXException, TikaException {

        UFEDChatParser parser = new UFEDChatParser();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/whatsapp/msgstore.db");
        parser.getSupportedTypes(ufedContext);
        try {
            parser.parse(stream, handler, metadata, ufedContext);
            String hts = handler.toString();
            String mts = metadata.toString();
            System.out.println(hts + mts);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

}
