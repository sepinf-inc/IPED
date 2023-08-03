package iped.parsers.tor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

import iped.parsers.util.MetadataUtil;

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
    public static final String TORTC_PREFIX = "TORTC:";
    public static final String TORTC_BUILD_FLAGS = TORTC_PREFIX + "BUILD_FLAGS";
	public static final String TORTC_PURPOSE = TORTC_PREFIX + "PURPOSE";
	public static final String TORTC_REND_QUERY = TORTC_PREFIX + "REND_QUERY";
	public static final String TORTC_TIME_CREATED = TORTC_PREFIX + "TIME_CREATED";
	public static final String TORTC_SOCKS_USERNAME = TORTC_PREFIX + "SOCKS_USERNAME";
	public static final String TORTC_SOCKS_PASSWORD = TORTC_PREFIX + "SOCKS_PASSWORD";
	public static final String TORTC_CIRCUIT_STATUS = TORTC_PREFIX + "CircuitStatus";
	public static final String TORTC_CIRCUIT_ID = TORTC_PREFIX + "CircuitId";
    private static final int MAX_BUFFER_SIZE = 1 << 24;
    private static final List<String> dateMetadatas = Arrays.asList("TIME_CREATED");

    static {
        for (String metadataName : dateMetadatas) {
            MetadataUtil.setMetadataType(TORTC_PREFIX + metadataName, java.util.Date.class);
        }
    }

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
            if (dateMetadatas.contains(varName)) {
                value += "Z"; // timestamps are represented in UTC in despite of timezone info.
            }
            metadata.add(TORTC_PREFIX + varName, value);
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        String itemText = readInputStream(stream);

        if (itemText != null) {
            int idx = itemText.indexOf("\n");
            if (idx > 2) {
                itemText = itemText.substring(0, idx);
            }
            itemText = itemText.stripTrailing();

            if (!(itemText.contains("BUILD_FLAGS") || itemText.contains("SOCKS") || itemText.contains("HS_STATE"))) {
                throw new TikaException("Invalid or incomplete TOR TC carved fragment");
            }

            int nextSpace = itemText.indexOf(" ");
            if (nextSpace != -1) {
                metadata.add(TORTC_CIRCUIT_STATUS, itemText.substring(0, nextSpace));
                itemText = itemText.substring(nextSpace + 1);
            }

            nextSpace = itemText.indexOf(" ");
            if (nextSpace != -1) {
                String circuitId = itemText.substring(0, nextSpace);
                if (circuitId.indexOf("BUILD_FLAGS") > 2) {
                    metadata.add(TORTC_CIRCUIT_ID, circuitId);
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
