package iped.parsers.tor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
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

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final MediaType TOR_TC_MIME = MediaType.application("x-tor-tc-fragment");
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(TOR_TC_MIME);
    private static final String TORTC_PREFIX = "TORTC:";
    private static final int MAX_BUFFER_SIZE = 1 << 24;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    private void parseMetadata(String itemText, String varName, Metadata metadata) {
        int i = itemText.indexOf(varName);
        String value = null;

        if (i >= 0) {
            int start = i + varName.length();
            int j = itemText.indexOf(" ", start);
            if (j > start) {
                value = itemText.substring(start + 1, j);
            } else if (start < itemText.length()) {
                value = itemText.substring(start + 1);
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
            if (nextSpace != -1) {
                metadata.add(TORTC_PREFIX + "CircuitStatus", itemText.substring(0, nextSpace));
                itemText = itemText.substring(nextSpace + 1);
            }

            nextSpace = itemText.indexOf(" ");
            if (nextSpace != -1) {
                String circuitId = itemText.substring(0, nextSpace);
                if (circuitId.indexOf("BUILD_FLAGS") > 2) {
                    metadata.add(TORTC_PREFIX + "CircuitId", circuitId);
                }
            }

            parseMetadata(itemText, "BUILD_FLAGS", metadata);
            parseMetadata(itemText, "TIME_CREATED", metadata);
            parseMetadata(itemText, "REND_QUERY", metadata);
            parseMetadata(itemText, "SOCKS_USERNAME", metadata);
            parseMetadata(itemText, "SOCKS_PASSWORD", metadata);
            parseMetadata(itemText, "PURPOSE", metadata);
        }

    }

    private static String readInputStream(InputStream is) throws IOException, TikaException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        IOUtils.copyLarge(is, bout, 0, MAX_BUFFER_SIZE);
        AutoDetectReader reader = new AutoDetectReader(new ByteArrayInputStream(bout.toByteArray()));
        return IOUtils.toString(reader);
    }

}
