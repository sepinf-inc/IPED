package dpf.inc.sepinf.browsers.parsers;

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

import iped3.util.ExtraProperties;

public abstract class AbstractPkgTest extends TestCase {
   protected ParseContext firefoxContext;
   
   protected Parser autoDetectParser;
   protected EmbeddedFirefoxParser firefoxtracker;

   protected void setUp() throws Exception {
      super.setUp();
      
      firefoxtracker = new EmbeddedFirefoxParser();
      firefoxContext = new ParseContext();
      firefoxContext.set(Parser.class, firefoxtracker);
      
   }

      
   @SuppressWarnings("serial")
   protected static class EmbeddedFirefoxParser extends AbstractParser {
       
      protected List<String> bookmarktitle = new ArrayList<String>();
      protected List<String> bookmarkurl = new ArrayList<String>();
      protected List<String> bookmarkcreated = new ArrayList<String>();
      protected List<String> bookmarkmodified = new ArrayList<String>();
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
          return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
              Metadata metadata, ParseContext context) throws IOException,
              SAXException, TikaException {
          
              if(metadata.get(TikaCoreProperties.TITLE)!= null)
                  bookmarktitle.add(metadata.get(TikaCoreProperties.TITLE));
              
              if(metadata.get(ExtraProperties.URL)!= null)
                  bookmarkurl.add(metadata.get(ExtraProperties.URL));
              
              if(metadata.get(TikaCoreProperties.CREATED)!= null)
                  bookmarkcreated.add(metadata.get(TikaCoreProperties.CREATED));
              
              if(metadata.get(TikaCoreProperties.MODIFIED)!= null)
                  bookmarkmodified.add(metadata.get(TikaCoreProperties.MODIFIED));
          
      }
   }
}
