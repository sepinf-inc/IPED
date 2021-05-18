package dpf.sp.gpinf.indexer.parsers;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.MD5;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
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
   protected ParseContext trackingContext;
   protected ParseContext recursingContext;
   protected ParseContext mboxContext;
   protected ParseContext pstContext;
   protected ParseContext oleContext;
   
   protected Parser autoDetectParser;
   protected EmbeddedTrackingParser tracker;
   protected EmbeddedMboxParser mboxtracker;
   protected EmbeddedPSTParser psttracker;
   protected EmbeddedOLEParser oletracker;

   protected void setUp() throws Exception {
      super.setUp();
      
      mboxtracker = new EmbeddedMboxParser();
      mboxContext = new ParseContext();
      mboxContext.set(Parser.class, mboxtracker);
      
      oletracker = new EmbeddedOLEParser();
      oleContext = new ParseContext();
      oleContext.set(Parser.class, oletracker);
      
      psttracker = new EmbeddedPSTParser();
      pstContext = new ParseContext();
      pstContext.set(Parser.class, psttracker);
      
      
      tracker = new EmbeddedTrackingParser();
      trackingContext = new ParseContext();
      trackingContext.set(Parser.class, tracker);
      
      autoDetectParser = new AutoDetectParser();
      recursingContext = new ParseContext();
      recursingContext.set(Parser.class, autoDetectParser);
   }


   @SuppressWarnings("serial")
   protected static class EmbeddedTrackingParser extends AbstractParser {
      protected List<String> filenames = new ArrayList<String>();
      protected List<String> modifieddate = new ArrayList<String>();
      protected List<String> itensmd5 = new ArrayList<String>();
      protected List<String> isfolder = new ArrayList<String>();
      

      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
         return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

         String hdigest = new DigestUtils(MD5).digestAsHex(stream);
         if(metadata.get(Metadata.RESOURCE_NAME_KEY)!= null)
             filenames.add(metadata.get(Metadata.RESOURCE_NAME_KEY));
         if(metadata.get(TikaCoreProperties.MODIFIED)!= null)
             modifieddate.add(metadata.get(TikaCoreProperties.MODIFIED));
         itensmd5.add(hdigest.toUpperCase());
         if(metadata.get(ExtraProperties.EMBEDDED_FOLDER)!= null)
                 isfolder.add(metadata.get(ExtraProperties.EMBEDDED_FOLDER));
         isfolder.add("false");

      }

   }
   @SuppressWarnings("serial")
   protected static class EmbeddedMboxParser extends AbstractParser {
      protected List<String> messagesubject = new ArrayList<String>();
      protected List<String> contenttype = new ArrayList<String>();
      protected List<String> contentmd5 = new ArrayList<String>();
      

      public Set<MediaType> getSupportedTypes(ParseContext context) {
         return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

         String hdigest = new DigestUtils(MD5).digestAsHex(stream);
         if(metadata.get(ExtraProperties.MESSAGE_SUBJECT)!= null)
             messagesubject.add(metadata.get(ExtraProperties.MESSAGE_SUBJECT));
         if(metadata.get(HttpHeaders.CONTENT_TYPE)!= null)
             contenttype.add(metadata.get(HttpHeaders.CONTENT_TYPE));
         contentmd5.add(hdigest.toUpperCase());

      }

   }
   
   @SuppressWarnings("serial")
   protected static class EmbeddedPSTParser extends AbstractParser {
       
      protected List<String> foldertitle = new ArrayList<String>();
      protected List<String> foldercreated = new ArrayList<String>();
      protected List<String> foldermodified = new ArrayList<String>();
      protected List<String> foldercomment = new ArrayList<String>();
      protected List<String> messagesubject = new ArrayList<String>();
      protected List<String> messagebody = new ArrayList<String>();
      protected List<String> messagedate = new ArrayList<String>();
      protected List<String> attachmentname = new ArrayList<String>();
      protected List<String> useraccount = new ArrayList<String>();
      protected List<String> username = new ArrayList<String>();
      protected List<String> useremail = new ArrayList<String>();
      protected List<String> userphone = new ArrayList<String>();
      protected List<String> useraddress = new ArrayList<String>();
      protected List<String> userbirth = new ArrayList<String>();
      protected List<String> userorganization = new ArrayList<String>();
      protected List<String> userurls = new ArrayList<String>();
      protected List<String> usernotes = new ArrayList<String>();
      protected List<String> contentmd5 = new ArrayList<String>();
      

      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
          return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
              Metadata metadata, ParseContext context) throws IOException,
              SAXException, TikaException {
          
          //avoiding filling the string vector with nullified info may cause the information to be
          //in the wrong position! Have to be cautious whenever trying to associate the information
          //for testing
          //md5
          String hdigest = new DigestUtils(MD5).digestAsHex(stream);
          //folder
          if(metadata.get(TikaCoreProperties.TITLE)!= null)
              foldertitle.add(metadata.get(TikaCoreProperties.TITLE));
          if(metadata.get(TikaCoreProperties.CREATED)!= null)
              foldercreated.add(metadata.get(TikaCoreProperties.CREATED));
          if(metadata.get(TikaCoreProperties.MODIFIED)!= null)
              foldermodified.add(metadata.get(TikaCoreProperties.MODIFIED));
          if(metadata.get(Metadata.COMMENT)!= null)
              foldercomment.add(metadata.get(Metadata.COMMENT));
          //messages
          if(metadata.get(ExtraProperties.MESSAGE_SUBJECT)!= null)
              messagesubject.add(metadata.get(ExtraProperties.MESSAGE_SUBJECT));
          if(metadata.get(ExtraProperties.MESSAGE_BODY)!= null)
              messagebody.add(metadata.get(ExtraProperties.MESSAGE_BODY));
          if(metadata.get(ExtraProperties.MESSAGE_DATE)!= null)
              messagedate.add(metadata.get(ExtraProperties.MESSAGE_DATE));
          //attachment
          if(metadata.get(Metadata.RESOURCE_NAME_KEY)!= null)
              attachmentname.add(metadata.get(Metadata.RESOURCE_NAME_KEY));
          //contact
          if(metadata.get(ExtraProperties.USER_ACCOUNT)!= null)
              useraccount.add(metadata.get(ExtraProperties.USER_ACCOUNT));
          if(metadata.get(ExtraProperties.USER_NAME)!= null)
              username.add(metadata.get(ExtraProperties.USER_NAME));
          if(metadata.get(ExtraProperties.USER_EMAIL)!= null)
              useremail.add(metadata.get(ExtraProperties.USER_EMAIL));
          if(metadata.get(ExtraProperties.USER_PHONE)!= null)
              userphone.add(metadata.get(ExtraProperties.USER_PHONE));
          if(metadata.get(ExtraProperties.USER_ADDRESS)!= null)
              useraddress.add(metadata.get(ExtraProperties.USER_ADDRESS));
          if(metadata.get(ExtraProperties.USER_BIRTH)!= null)
              userbirth.add(metadata.get(ExtraProperties.USER_BIRTH));
          if(metadata.get(ExtraProperties.USER_ORGANIZATION)!= null)
              userorganization.add(metadata.get(ExtraProperties.USER_ORGANIZATION));
          if(metadata.get(ExtraProperties.USER_URLS)!= null)
              userurls.add(metadata.get(ExtraProperties.USER_URLS));
          if(metadata.get(ExtraProperties.USER_NOTES)!= null)
              usernotes.add(metadata.get(ExtraProperties.USER_NOTES));
         
          contentmd5.add(hdigest.toUpperCase());

      }

   }
   
   @SuppressWarnings("serial")
   protected static class EmbeddedOLEParser extends AbstractParser {
       
      protected List<String> documentfolder = new ArrayList<String>();
      
      public Set<MediaType> getSupportedTypes(ParseContext context) {
          return (new AutoDetectParser()).getSupportedTypes(context);
      }

      public void parse(InputStream stream, ContentHandler handler,
              Metadata metadata, ParseContext context) throws IOException,
              SAXException, TikaException {
          
         
          if(metadata.get(TikaCoreProperties.TITLE)!= null)
              documentfolder.add(metadata.get(TikaCoreProperties.TITLE));
      }

   }
}
