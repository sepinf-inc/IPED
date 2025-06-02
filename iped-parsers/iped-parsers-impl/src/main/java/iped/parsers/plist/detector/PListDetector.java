package iped.parsers.plist.detector;

import static org.apache.tika.detect.apple.BPListDetector.BPLIST;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.apple.BPListDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.xml.sax.SAXException;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;


/**
 * Based on org.apache.tika.detect.apple.BPListDetector
 *  https://github.com/apache/tika/blob/main/tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-apple-module/src/main/java/org/apache/tika/detect/apple/BPListDetector.java
 */
public class PListDetector implements Detector {

  private static final long serialVersionUID = 1L;

  public static MediaType WA_USER_PLIST = MediaType.application("x-whatsapp-user-plist");
  public static MediaType THREEMA_USER_PLIST = MediaType.application("x-threema-user-plist");
  public static MediaType NSKEYEDARCHIVER_PLIST = MediaType.application("x-apple-nskeyedarchiver");
  public static MediaType CAAR_PLIST = MediaType.application("x-plist-caar");

    public static MediaType detectOnDict(NSDictionary dict, Metadata metadata) {

        MediaType type = BPListDetector.detectOnKeys(dict.keySet());
        if (!BPListDetector.BPLIST.equals(type)) {
            return type;
        }

        if ((dict.containsKey("OwnJabberID") || dict.containsKey("LastOwnJabberID")) && (dict.containsKey("OwnPhoneNumber") || dict.containsKey("FullUserName"))) {
            return WA_USER_PLIST;
        } else if (dict.containsKey("Threema device ID")) {
            return THREEMA_USER_PLIST;
        } else if (isCAAR(metadata)) {
            return CAAR_PLIST;
        } else if (isNSKeyedArchiver(dict)) {
            return NSKEYEDARCHIVER_PLIST;
        }
        return BPListDetector.BPLIST;
    }

    public static boolean isNSKeyedArchiver(NSDictionary dict) {
        NSObject archiver = dict.get("$archiver");
        if (archiver instanceof NSString) {
            if (archiver.toString().equalsIgnoreCase("NSKeyedArchiver")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isCAAR(Metadata metadata) {
        String ext = StringUtils.substringAfterLast(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY), ".");
        return "caar".equals(ext);
    }

    /**
     * @param input    input stream must support reset
     * @param metadata input metadata for the document
     * @return
     * @throws IOException
     */
    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }
        input.mark(8);
        byte[] bytes = new byte[8];

        try {
            int read = IOUtils.read(input, bytes);
            if (read < 6) {
                return MediaType.OCTET_STREAM;
            }
        } catch (IOException e) {
            return MediaType.OCTET_STREAM;
        } finally {
            input.reset();
        }

        int i = 0;
        if (bytes[i++] != 'b' || bytes[i++] != 'p' || bytes[i++] != 'l' || bytes[i++] != 'i' ||
                bytes[i++] != 's' || bytes[i++] != 't') {
            return MediaType.OCTET_STREAM;
        }
        //TODO: extract the version with the next two bytes if they were read
        NSObject rootObj = null;
        try {
            if (input instanceof TikaInputStream && ((TikaInputStream) input).hasFile()) {
                rootObj = PropertyListParser.parse(((TikaInputStream) input).getFile());
            } else {
                rootObj = PropertyListParser.parse(input);
            }
            if (input instanceof TikaInputStream) {
                ((TikaInputStream) input).setOpenContainer(rootObj);
            }
        } catch (PropertyListFormatException | ParseException |
                ParserConfigurationException | SAXException e) {
            throw new IOException("problem parsing root", e);
        }
        if (rootObj instanceof NSDictionary) {
            return detectOnDict((NSDictionary) rootObj, metadata);
        }
        return BPLIST;
    }
}
