package dpf.ap.gpinf.telegramextractor;


import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import junit.framework.TestCase;

public class TelegramParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testTelegramParser() throws IOException, SAXException, TikaException{

        TelegramParser parser = new TelegramParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(1<<20);
        InputStream stream = getStream("test-files/org.telegram.messenger.web/files/cache4.db");
        ParseContext context = new ParseContext();
        parser.setExtractMessages(false);
        parser.setEnabledForUfdr(false);
        parser.getSupportedTypes(context);
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, "application/x-telegram-db");
        IItemSearcher itemSearcher = new IItemSearcher() {
            
            @Override
            public void close() throws IOException {
                // TODO Auto-generated method stub
                
            }
            
            @Override
            public Iterable<IItemBase> searchIterable(String luceneQuery) {
                // TODO Auto-generated method stub
                return searchIterable(luceneQuery);
            }
            
            @Override
            public List<IItemBase> search(String luceneQuery) {
                // TODO Auto-generated method stub
                return search(luceneQuery);
            }
            
            @Override
            public String escapeQuery(String string) {
                // TODO Auto-generated method stub
                return string;
            }
        };
        context.set(IItemSearcher.class, itemSearcher);
//        parser.parse(stream, handler, metadata, context);
     
    }
}
