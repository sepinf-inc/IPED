package dpf.mt.gpinf.tor.tc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parses TC BUILT and EXTENDED Circuit Status Changes responses fragments on
 * pagefile.sys that are carved by TOR_CIRCUIT_STATUS carver. (Section 4.1.1 of
 * https://torproject.gitlab.io/torspec/control-spec.html#circuit-status-changed)
 */

public class TorTcParser extends AbstractParser {

    public static final MediaType SKYPE_MIME = MediaType.application("x-tor-tc-fragment"); //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = new HashSet<MediaType>();

    static final String TORTC_PREFIX = "TORTC:";

    static {
        SUPPORTED_TYPES.add(SKYPE_MIME);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private void parseMetadata(String itemText, String varName, Metadata metadata) {
        int i = itemText.indexOf(varName);
        String value = null;

        if (i >= 0) {
            int j = itemText.indexOf(" ", i + varName.length());
            if (j > 0) {
                value = itemText.substring(i + varName.length() + 1, j);
            } else {
                value = itemText.substring(i + varName.length() + 1);
            }
        }

        if (value != null) {
            metadata.add(TORTC_PREFIX + varName, value);
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        String itemText = readInputStream(stream);

        if (itemText != null) {
            if (itemText.indexOf("\r\n") > 2) {
                itemText = itemText.substring(0, itemText.indexOf("\r\n"));
            }

            if (!(itemText.contains("BUILD_FLAGS") || itemText.contains("SOCKS") || itemText.contains("HS_STATE"))) {
                throw new TikaException("Invalid or incomplete TOR TC carved fragment");
            }

            int nextSpace = itemText.indexOf(" ");
            metadata.add(TORTC_PREFIX + "CircuitStatus", itemText.substring(0, nextSpace));
            itemText = itemText.substring(nextSpace + 1);

            nextSpace = itemText.indexOf(" ");
            String circuitId = itemText.substring(0, nextSpace);
            if (circuitId.indexOf("BUILD_FLAGS") > 2) {
                metadata.add(TORTC_PREFIX + "CircuitId", circuitId);
            }

            parseMetadata(itemText, "BUILD_FLAGS", metadata);
            parseMetadata(itemText, "TIME_CREATED", metadata);
            parseMetadata(itemText, "REND_QUERY", metadata);
            parseMetadata(itemText, "SOCKS_USERNAME", metadata);
            parseMetadata(itemText, "SOCKS_PASSWORD", metadata);
            parseMetadata(itemText, "PURPOSE", metadata);
        }

    }

    private static final int MAX_BUFFER_SIZE = 1 << 24;

    private static String readInputStream(InputStream is) throws IOException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            IOUtils.copyLarge(is, bout, 0, MAX_BUFFER_SIZE);
            AutoDetectReader reader = new AutoDetectReader(new ByteArrayInputStream(bout.toByteArray()));
            return IOUtils.toString(reader);

        } finally {
            tmp.dispose();
        }
    }

}
