package iped.parsers.zoomdpapi;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

/**
 * Detects Zoom forensic artifacts by filename:
 * - Zoom.us.ini → application/x-zoom-dpapi-ini
 *
 * The encrypted .enc.db databases cannot be detected by content
 * inspection (no SQLite header), so they are found by the parser
 * via IItemSearcher when processing the INI file.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomDetector implements Detector {

    private static final long serialVersionUID = 1L;

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {
        String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (name == null) {
            name = metadata.get("resourceName");
        }

        if (name != null && name.equalsIgnoreCase("Zoom.us.ini")) {
            return ZoomDpapiParser.ZOOM_INI;
        }

        return MediaType.OCTET_STREAM;
    }
}
