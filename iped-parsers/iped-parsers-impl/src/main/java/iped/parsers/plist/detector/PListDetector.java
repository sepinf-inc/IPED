package iped.parsers.plist.detector;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.SAXException;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;

public class PListDetector implements Detector {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    // binary versions
    public static MediaType BMEMGRAPH = MediaType.application("x-bplist-memgraph");
    public static MediaType BWEBARCHIVE = MediaType.application("x-bplist-webarchive");
    public static MediaType BPLIST = MediaType.application("x-bplist");
    public static MediaType BITUNES = MediaType.application("x-bplist-itunes");
    public static MediaType WA_USER_PLIST = MediaType.application("x-whatsapp-user-plist");
    public static MediaType THREEMA_USER_PLIST = MediaType.application("x-threema-user-plist");

    public static MediaType detectOnKeys(Set<String> keySet) {
        if (keySet.contains("nodes") && keySet.contains("edges") && keySet.contains("graphEncodingVersion")) {
            return BMEMGRAPH;
        } else if (keySet.contains("WebMainResource")) { // && keySet.contains ("WebSubresources") should we require
            // this?
            return BWEBARCHIVE;
        } else if (keySet.contains("Playlists") && keySet.contains("Tracks") && keySet.contains("Music Folder")) {
            return BITUNES;
        } else if ((keySet.contains("OwnJabberID") || keySet.contains("LastOwnJabberID")) && (keySet.contains("OwnPhoneNumber") || keySet.contains("FullUserName"))) {
            return WA_USER_PLIST;
        } else if (keySet.contains("Threema device ID")) {
            return THREEMA_USER_PLIST;
        }
        return BPLIST;
    }

    /**
     * @param input
     *            input stream must support reset
     * @param metadata
     *            input metadata for the document
     * @return
     * @throws IOException
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        input.mark(8);
        try {
            byte[] bytes = input.readNBytes(6);
            if (bytes.length < 6) {
                return MediaType.OCTET_STREAM;
            }
            if (bytes[0] != 'b' || bytes[1] != 'p' || bytes[2] != 'l' || bytes[3] != 'i' || bytes[4] != 's' || bytes[5] != 't') {
                return MediaType.OCTET_STREAM;
            }
        } catch (IOException e) {
            return MediaType.OCTET_STREAM;
        } finally {
            input.reset();
        }

        NSObject rootObj = null;
        if (input instanceof TikaInputStream) {
            Object obj = ((TikaInputStream) input).getOpenContainer();
            if (obj instanceof NSObject) {
                rootObj = (NSObject) obj;
            }
        }
        if (rootObj == null) {
            try {
                if (input instanceof TikaInputStream && ((TikaInputStream) input).hasFile()) {
                    rootObj = PropertyListParser.parse(((TikaInputStream) input).getFile());
                } else {
                    rootObj = PropertyListParser.parse(input);
                }
                if (input instanceof TikaInputStream) {
                    ((TikaInputStream) input).setOpenContainer(rootObj);
                }
            } catch (PropertyListFormatException | ParseException | ParserConfigurationException | SAXException e) {
                throw new IOException("problem parsing root", e);
            }
        }

        if (rootObj instanceof NSDictionary) {
            return detectOnKeys(((NSDictionary) rootObj).getHashMap().keySet());
        }
        return BPLIST;
    }
}
