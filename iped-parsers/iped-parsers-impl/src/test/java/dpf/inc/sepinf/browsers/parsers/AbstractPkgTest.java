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

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.util.ExtraProperties;

public abstract class AbstractPkgTest extends TestCase {
   protected ParseContext firefoxContext;
   protected ParseContext chromeContext;
   protected ParseContext edgeContext;
   protected ParseContext safariContext;
   protected ParseContext safariContexthst;
   protected ParseContext safariContextbkm;
   protected ParseContext safariContextdwl;
   
   protected Parser autoDetectParser;
   protected EmbeddedFirefoxParser firefoxtracker;
   protected EmbeddedChromeParser chrometracker;
   protected EmbeddedEdgeParser edgetracker;
   protected EmbeddedSafariParser safaritracker;
   protected EmbeddedSafariParserHst safaritrackerhst;
   protected EmbeddedSafariParserBkm safaritrackerbkm;
   protected EmbeddedSafariParserDwl safaritrackerdwl;
   protected ItemInfo itemInfo;
   protected ItemInfo itemInfohst;
   protected ItemInfo itemInfobkm;
   protected ItemInfo itemInfodwl;
   
   protected void setUp() throws Exception {
      super.setUp();
      
      firefoxtracker = new EmbeddedFirefoxParser();
      firefoxContext = new ParseContext();
      firefoxContext.set(Parser.class, firefoxtracker);

      chrometracker = new EmbeddedChromeParser();
      chromeContext = new ParseContext();
      chromeContext.set(Parser.class, chrometracker);
      
      edgetracker = new EmbeddedEdgeParser();
      edgeContext = new ParseContext();
      edgeContext.set(Parser.class, edgetracker);

      safaritracker = new EmbeddedSafariParser();
      safaritrackerhst = new EmbeddedSafariParserHst();
      safaritrackerbkm = new EmbeddedSafariParserBkm();
      safaritrackerdwl = new EmbeddedSafariParserDwl();
      safariContext = new ParseContext();
      safariContexthst = new ParseContext();
      safariContextbkm = new ParseContext();
      safariContextdwl = new ParseContext();
      ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "test-files/test_sample.plist", false);
      ItemInfo itemInfohst = new ItemInfo(0, getName(), null, null, "History", false);
      ItemInfo itemInfobkm = new ItemInfo(0, getName(), null, null, "Bookmarks", false);
      ItemInfo itemInfodwl = new ItemInfo(0, getName(), null, null, "Downloads", false);
      safariContext.set(ItemInfo.class, itemInfo);
      safariContext.set(Parser.class, safaritracker);
      safariContexthst.set(ItemInfo.class, itemInfohst);
      safariContexthst.set(Parser.class, safaritrackerhst);
      safariContextbkm.set(ItemInfo.class, itemInfobkm);
      safariContextbkm.set(Parser.class, safaritrackerbkm);
      safariContextdwl.set(ItemInfo.class, itemInfodwl);
      safariContextdwl.set(Parser.class, safaritrackerdwl);
      
   }

   @SuppressWarnings("serial")
   protected static class EmbeddedChromeParser extends AbstractParser {
       
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

   @SuppressWarnings("serial")
   protected static class EmbeddedSafariParserDwl extends AbstractParser {
       
      protected List<String> downloadurl = new ArrayList<String>();
      protected List<String> downloadlocalpath = new ArrayList<String>();
      protected List<String> downloadtotalbytes = new ArrayList<String>();
      protected List<String> downloadreceivedbytes = new ArrayList<String>();
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
          return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
              Metadata metadata, ParseContext context) throws IOException,
              SAXException, TikaException {
          
              
              if(metadata.get(ExtraProperties.URL)!= null)
                  downloadurl.add(metadata.get(ExtraProperties.URL));

              if(metadata.get(ExtraProperties.LOCAL_PATH)!= null)
                  downloadlocalpath.add(metadata.get(ExtraProperties.LOCAL_PATH));

              if(metadata.get(ExtraProperties.DOWNLOAD_TOTAL_BYTES)!= null)
                  downloadtotalbytes.add(metadata.get(ExtraProperties.DOWNLOAD_TOTAL_BYTES));

              if(metadata.get(ExtraProperties.DOWNLOAD_RECEIVED_BYTES)!= null)
                  downloadreceivedbytes.add(metadata.get(ExtraProperties.DOWNLOAD_RECEIVED_BYTES));
          
      }
   }
   
   @SuppressWarnings("serial")
   protected static class EmbeddedSafariParserBkm extends AbstractParser {
       
      protected List<String> bookmarktitle = new ArrayList<String>();
      protected List<String> bookmarkurl = new ArrayList<String>();
      
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
          
      }
   }
   
   @SuppressWarnings("serial")
   protected static class EmbeddedSafariParserHst extends AbstractParser {
       
      protected List<String> historytitle = new ArrayList<String>();
      protected List<String> historyaccessed = new ArrayList<String>();
      protected List<String> historyvisitdate = new ArrayList<String>();
      protected List<String> historyurl = new ArrayList<String>();
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
          return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
              Metadata metadata, ParseContext context) throws IOException,
              SAXException, TikaException {
          
              if(metadata.get(TikaCoreProperties.TITLE)!= null)
                  historytitle.add(metadata.get(TikaCoreProperties.TITLE));
              
              if(metadata.get(ExtraProperties.ACCESSED)!= null)
                  historyaccessed.add(metadata.get(ExtraProperties.ACCESSED));
              
              if(metadata.get(ExtraProperties.VISIT_DATE)!= null)
                  historyvisitdate.add(metadata.get(ExtraProperties.VISIT_DATE));
              
              if(metadata.get(ExtraProperties.URL)!= null)
                  historyurl.add(metadata.get(ExtraProperties.URL));
          
      }
   }
   
   @SuppressWarnings("serial")
   protected static class EmbeddedSafariParser extends AbstractParser {
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
          return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
              Metadata metadata, ParseContext context) throws IOException,
              SAXException, TikaException {

      }
   }



   @SuppressWarnings("serial")
   protected static class EmbeddedEdgeParser extends AbstractParser {
       
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
