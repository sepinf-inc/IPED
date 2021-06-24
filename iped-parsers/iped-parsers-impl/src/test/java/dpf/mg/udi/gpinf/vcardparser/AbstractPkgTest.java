package dpf.mg.udi.gpinf.vcardparser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped3.util.ExtraProperties;

public abstract class AbstractPkgTest extends TestCase {
    protected ParseContext vcardContext;
    protected EmbeddedVCardParser vcardtracker;

    protected void setUp() throws Exception {
        super.setUp();

        vcardtracker = new EmbeddedVCardParser();
        vcardContext = new ParseContext();
        vcardContext.set(Parser.class, vcardtracker);

    }

    @SuppressWarnings("serial")
    protected static class EmbeddedVCardParser extends AbstractParser {

        protected List<String> username = new ArrayList<String>();
        protected List<String> userbirth = new ArrayList<String>();
        protected List<String> userphone = new ArrayList<String>();
        protected List<String> useremail = new ArrayList<String>();
        protected List<String> useraddress = new ArrayList<String>();
        protected List<String> userorganization = new ArrayList<String>();
        protected List<String> usernotes = new ArrayList<String>();
        protected List<String> userurls = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            username.add(metadata.get(ExtraProperties.USER_NAME));
            userbirth.add(metadata.get(ExtraProperties.USER_BIRTH));
            userphone.add(metadata.get(ExtraProperties.USER_PHONE));
            useremail.add(metadata.get(ExtraProperties.USER_EMAIL));
            useraddress.add(metadata.get(ExtraProperties.USER_ADDRESS));
            userorganization.add(metadata.get(ExtraProperties.USER_ORGANIZATION));
            usernotes.add(metadata.get(ExtraProperties.USER_NOTES));
            userurls.add(metadata.get(ExtraProperties.USER_URLS));
        }
    }
}
