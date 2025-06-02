package iped.parsers.plist;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.detect.apple.BPListDetector;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.dd.plist.NSObject;

public class PListParser extends AbstractPListParser<Void> {

    private static final long serialVersionUID = 3633471688807424828L;

    private static final Logger logger = LoggerFactory.getLogger(PListParser.class);

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(BPListDetector.BITUNES,
            BPListDetector.BMEMGRAPH, BPListDetector.BPLIST, BPListDetector.BWEBARCHIVE, BPListDetector.PLIST)));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    protected void processAndGenerateHTMLContent(NSObject plistObj, State state) throws SAXException {

        if (plistObj != null) {
            processObject(plistObj, "", state, true); // first elements are open by default
        } else {
            state.xhtml.startElement("p");
            state.xhtml.characters("No PList content found or unexpected structure.");
            state.xhtml.endElement("p");
        }
    }

}
