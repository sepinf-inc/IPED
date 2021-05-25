package dpf.inc.sepinf.UsnJrnl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public abstract class AbstractPkgTest extends TestCase {
   protected ParseContext usnContext;
   protected EmbeddedUsnParser usntracker;

   protected void setUp() throws Exception {
      super.setUp();
      
      usntracker = new EmbeddedUsnParser();
      usnContext = new ParseContext();
      usnContext.set(Parser.class, usntracker);
   }
   
   @SuppressWarnings("serial")
   protected static class EmbeddedUsnParser extends AbstractParser {
       
      protected List<String> contenttype = new ArrayList<String>();
      protected List<String> title = new ArrayList<String>();
      protected List<String> created = new ArrayList<String>();
      
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
          return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
              Metadata metadata, ParseContext context) throws IOException,
              SAXException, TikaException {
              
              if(contenttype.size() == 0)
                  contenttype.add(metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE));
              if(!contenttype.contains("application/x-usnjournal-registry"))
                  contenttype.add(metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE));
              
              title.add(metadata.get(TikaCoreProperties.TITLE));
              
              if(metadata.get(TikaCoreProperties.CREATED) != null)
                  created.add(metadata.get(TikaCoreProperties.CREATED));
      }
      
   }
}
