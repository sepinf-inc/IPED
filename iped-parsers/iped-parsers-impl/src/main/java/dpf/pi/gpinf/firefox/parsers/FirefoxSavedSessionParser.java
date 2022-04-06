package dpf.pi.gpinf.firefox.parsers;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4SafeDecompressor;
import net.jpountz.lz4.LZ4Exception;
import org.apache.tika.sax.XHTMLContentHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dpf.sp.gpinf.indexer.parsers.util.Messages;

public class FirefoxSavedSessionParser extends AbstractParser {
    private static final long serialVersionUID = 1L;
    private static final MediaType X_FIREFOX_SAVEDSESSION_MIME_TYPE = MediaType.application("x-firefox-savedsession");
    private static final int MAX_MEM_BYTES = 1 << 27;
    private static final String MAX_MEM_WARNING = "Byte data exceeds max size allowed to load on memory.";
    private static LZ4Factory factory = null;
    private static Logger LOGGER = LoggerFactory.getLogger(FirefoxSavedSessionParser.class);

    private final int BLOCK_SIZE = 4096;
    private final short HEADER_OFFSET = 12;
    private final String HEADER_TABS_TABLE = Messages.getString("FirefoxSessions.TabsHeader");
    private final String HEADER_TABS_TABLE_URL = Messages.getString("FirefoxSessions.TabsURL");
    private final String HEADER_TABS_TABLE_TITLE = Messages.getString("FirefoxSessions.TabsTitle");
    private final String HEADER_COOKIES_TABLE = Messages.getString("FirefoxSessions.CookiesHeader");
    private final String HEADER_COOKIES_TABLE_HOST = Messages.getString("FirefoxSessions.CookiesHost");
    private final String HEADER_COOKIES_TABLE_PATH = Messages.getString("FirefoxSessions.CookiesPath");
    private final String HEADER_COOKIES_TABLE_NAME = Messages.getString("FirefoxSessions.CookiesName");
    private final String HEADER_COOKIES_TABLE_COOKIE = Messages.getString("FirefoxSessions.CookiesCookie");
    private static ObjectMapper mapper;

    static {
        factory = LZ4Factory.safeInstance();
        // ObjectMapper is thread-safe when no configuration is done
        mapper = new ObjectMapper();
    }

    public FirefoxSavedSessionParser() {
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set(X_FIREFOX_SAVEDSESSION_MIME_TYPE);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // TODO Auto-generated method stub
        byte[] data;
        String json;
        JsonNode rootNode;
        XHTMLContentHandler xHandler;

        try {
            // LOGGER.info("Found a Mozilla JSON LZ4 session file. Trying to parse it...");
            data = decompressLZ4Data(stream, metadata);
            json = new String(data, StandardCharsets.UTF_8);
            rootNode = parseMozillaJSON(json);
            xHandler = new XHTMLContentHandler(handler, metadata);
            populateTextTabContent(xHandler, rootNode);

        } catch (TikaException | IOException | SAXException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaException("Could not decode Mozilla JSON LZ4 session file", e);
        }
    }

    private void populateTextTabContent(XHTMLContentHandler xHandler, JsonNode rootNode) throws SAXException {
        JsonNode tabs, cookies;
        try {
            xHandler.startDocument();

            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$

            /* TABS */
            xHandler.startElement("h3 align=center"); //$NON-NLS-1$
            xHandler.characters(HEADER_TABS_TABLE); // $NON-NLS-1$
            xHandler.endElement("h3"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("table"); //$NON-NLS-1$

            xHandler.startElement("tr"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(HEADER_TABS_TABLE_URL); // $NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(HEADER_TABS_TABLE_TITLE); // $NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            xHandler.endElement("tr"); //$NON-NLS-1$

            boolean empty = true;
            tabs = rootNode.findPath("tabs");
            if (tabs != null && tabs.isArray()) {
                for (JsonNode tab : tabs) {
                    JsonNode entries = tab.get("entries");
                    if (entries != null && entries.isArray()) {
                        for (JsonNode entry : entries) {
                            xHandler.startElement("tr"); //$NON-NLS-1$

                            xHandler.startElement("td"); //$NON-NLS-1$
                            xHandler.characters(entry.get("url") != null ? entry.get("url").toString() : "-"); // $NON-NLS-1$
                            xHandler.endElement("td"); //$NON-NLS-1$

                            xHandler.startElement("td"); //$NON-NLS-1$
                            xHandler.characters(entry.get("title") != null ? entry.get("title").toString() : "-"); // $NON-NLS-1$
                            xHandler.endElement("td"); //$NON-NLS-1$
                            xHandler.endElement("tr"); //$NON-NLS-1$
                            empty = false;
                        }
                    }
                }
            }
            if(empty) {
                printEmptyLine(xHandler, 2);
            }
            xHandler.endElement("table"); // End of Tabs list
            
            /* -- COOKIES */
            xHandler.startElement("hr"); //$NON-NLS-1$
            xHandler.startElement("h3 align=center"); //$NON-NLS-1$
            xHandler.characters(HEADER_COOKIES_TABLE); // $NON-NLS-1$
            xHandler.endElement("h3"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$

            xHandler.startElement("table"); //$NON-NLS-1$

            xHandler.startElement("tr"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(HEADER_COOKIES_TABLE_HOST); // $NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(HEADER_COOKIES_TABLE_PATH); // $NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(HEADER_COOKIES_TABLE_NAME); // $NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(HEADER_COOKIES_TABLE_COOKIE); // $NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$

            empty = true;
            cookies = rootNode.path("cookies");
            if (cookies != null && cookies.isArray()) {
                for (JsonNode tmp : cookies) {
                    xHandler.startElement("tr"); //$NON-NLS-1$
                    xHandler.startElement("td"); //$NON-NLS-1$
                    xHandler.characters(tmp.get("host") != null ? tmp.get("host").toString() : "-"); //$NON-NLS-1$
                    xHandler.endElement("td"); //$NON-NLS-1$
                    xHandler.startElement("td"); //$NON-NLS-1$
                    xHandler.characters(tmp.get("path") != null ? tmp.get("path").toString() : "-"); //$NON-NLS-1$
                    xHandler.endElement("td"); //$NON-NLS-1$
                    xHandler.startElement("td"); //$NON-NLS-1$
                    xHandler.characters(tmp.get("name") != null ? tmp.get("name").toString() : "-"); //$NON-NLS-1$
                    xHandler.endElement("td"); //$NON-NLS-1$
                    xHandler.startElement("td"); //$NON-NLS-1$
                    xHandler.characters(tmp.get("value") != null ? tmp.get("value").toString() : "-"); //$NON-NLS-1$
                    xHandler.endElement("td"); //$NON-NLS-1$
                    xHandler.endElement("tr"); //$NON-NLS-1$
                    empty = false;
                }
            }
            if(empty) {
                printEmptyLine(xHandler, 4);
            }
            
            xHandler.endElement("table"); //$NON-NLS-1$
            xHandler.endDocument();
        } catch (SAXException e) {
            throw e;
        }
    }
    
    private void printEmptyLine(XHTMLContentHandler xHandler, int cols) throws SAXException {
        xHandler.startElement("tr"); //$NON-NLS-1$
        for(int i = 0; i < cols; i++) {
            xHandler.startElement("td"); //$NON-NLS-1$
            xHandler.characters("-"); //$NON-NLS-1$
            xHandler.endElement("td"); //$NON-NLS-1$
        }
        xHandler.endElement("tr"); //$NON-NLS-1$
    }

    private JsonNode parseMozillaJSON(String json) throws IOException {
        JsonNode node = null;
        node = mapper.readTree(json);
        return node;
    }

    private byte[] decompressLZ4Data(InputStream stream, Metadata metadata) throws IOException, TikaException {
        byte[] buffer = null;
        byte[] compressedFile;
        byte[] streamBuffer = new byte[BLOCK_SIZE];
        int srcLen, compressedLength = 0, uncompressedLength;
        ByteArrayOutputStream fileContent = new ByteArrayOutputStream();
        LZ4SafeDecompressor myDecompressor = factory.safeDecompressor();
        try {
            // -- Thales - Input Stream can block and provide just a part of the content
            // for a single read() call, so let's iterate until we get all the content.
            // Can the file be too large to be kept in the memory ? Maybe so...
            // Is it Thread Safe?
            while ((srcLen = stream.read(streamBuffer)) != -1) {
                fileContent.write(streamBuffer, 0, srcLen);
                compressedLength += srcLen;
                if (compressedLength > MAX_MEM_BYTES) {
                    throw new TikaException(MAX_MEM_WARNING);
                }
            }
            compressedFile = fileContent.toByteArray();
            fileContent = null;

            /*
             * -- Thales - Work around for false positive. False positive can cause
             * OutOfMemory problems among others... This should be properly handled with
             * Tika type detection mechanism or Maybe using Detector()...
             */
            if (compressedFile[0] != 0x6D || compressedFile[1] != 0x6F || compressedFile[2] != 0x7A) {
                throw getTikaException(metadata, null);
            }

            /*
             * -- Thales - After a while researching the MozLZ4 files, discovered that they
             * keep the uncompressed length on its header. This is calculated as follow:
             * 
             * Uncompressed Length= byte[8] + byte[9]*256 + byte[10]*(256^2) +
             * byte[11]*(256^3)
             * 
             */
            uncompressedLength = ((int) compressedFile[8] & 0xff) + (((int) compressedFile[9] & 0xff) * (1 << 8))
                    + (((int) compressedFile[10] & 0xff) * (1 << 16)) + (((int) compressedFile[11] & 0xff) * (1 << 24));

            if (uncompressedLength > MAX_MEM_BYTES) {
                throw new TikaException(MAX_MEM_WARNING);
            }
            buffer = new byte[uncompressedLength];

            /*
             * -- Thales - As said before, bytes 8 to 12 keep the original uncompressed
             * length of the file. Bytes 0 to 7 seems to be the the signature of mozlz4
             * files. I.E their magic number... Ignore it for uncompressing purposes...
             * Maybe it can be important for setting mime-type or carving purposes, but for
             * now just ignore it...
             */
            myDecompressor.decompress(compressedFile, HEADER_OFFSET, compressedLength - HEADER_OFFSET, buffer, 0);

            return buffer;

        } catch (LZ4Exception | IOException e) {
            if (e instanceof LZ4Exception) {
                e.printStackTrace();
                throw getTikaException(metadata, e);
            }
            throw e;
        }

    }

    private TikaException getTikaException(Metadata metadata, Exception cause) {
        TikaException e = new TikaException(
                "Possible false positive LZ4 Mozilla Firefox file: " + metadata.get(Metadata.RESOURCE_NAME_KEY));
        if (cause != null) {
            e.initCause(cause);
        }
        return e;
    }

}