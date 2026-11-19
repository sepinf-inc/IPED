package iped.parsers.zoomdpapi;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

/**
 * Detects Zoom forensic artifacts by filename:
 * - Zoom.us.ini -> application/x-zoom-dpapi-ini
 *
 * Returns null when the file is not a Zoom artifact so that
 * other detectors can still assign the correct MIME type.
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

        if (name != null) {
            // Extract just the filename from a potential full path
            int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            if (lastSep >= 0) {
                name = name.substring(lastSep + 1);
            }

            if (name.equalsIgnoreCase("Zoom.us.ini")) {
                return ZoomDpapiParser.ZOOM_INI;
            }
        }

        // Return null to let other detectors handle non-Zoom files
        return null;
    }
}
