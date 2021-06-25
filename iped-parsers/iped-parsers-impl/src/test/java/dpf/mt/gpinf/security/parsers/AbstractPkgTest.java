package dpf.mt.gpinf.security.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
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

public abstract class AbstractPkgTest extends TestCase {
    protected ParseContext certificateContext;
    protected EmbeddedCertificateParser certificatetracker;

    protected void setUp() throws Exception {
        super.setUp();

        certificatetracker = new EmbeddedCertificateParser();
        certificateContext = new ParseContext();
        certificateContext.set(Parser.class, certificatetracker);
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedCertificateParser extends AbstractParser {

        protected List<String> notbefore = new ArrayList<String>();
        protected List<String> notafter = new ArrayList<String>();
        protected List<String> issuer = new ArrayList<String>();
        protected List<String> subject = new ArrayList<String>();
        protected List<String> issubjectauthority = new ArrayList<String>();
        protected List<String> contenttype = new ArrayList<String>();
        protected List<String> title = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {
            if (metadata.get(CertificateParser.NOTBEFORE) != null)
                notbefore.add(metadata.get(CertificateParser.NOTBEFORE));
            if (metadata.get(CertificateParser.NOTAFTER) != null)
                notafter.add(metadata.get(CertificateParser.NOTAFTER));
            if (metadata.get(CertificateParser.ISSUER) != null)
                issuer.add(metadata.get(CertificateParser.ISSUER));
            if (metadata.get(CertificateParser.SUBJECT) != null)
                subject.add(metadata.get(CertificateParser.SUBJECT));
            if (metadata.get(CertificateParser.ISSUBJECTAUTHORITY) != null)
                issubjectauthority.add(metadata.get(CertificateParser.ISSUBJECTAUTHORITY));
            if (metadata.get(HttpHeaders.CONTENT_TYPE) != null)
                contenttype.add(metadata.get(HttpHeaders.CONTENT_TYPE));
            if (metadata.get(TikaCoreProperties.TITLE) != null)
                title.add(metadata.get(TikaCoreProperties.TITLE));
        }
    }

}
