package iped.parsers.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import iped.utils.IOUtil;

/**
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class ChatUtil {

    private static final String navControls = 
              "    <div id=\"nav-controls\" class=\"nav-controls\">\n"
            + "        <div id=\"topbar-spinner\" class=\"topbar-spinner\"></div>\n"
            + "        <div id=\"nav-content\" class=\"nav-content\" style=\"display: none;\">\n"
            + "            <div class=\"nav-buttons-row\">\n"
            + "                <button id=\"btn-prev\" title=\"" + Messages.getString("ChatReport.Prev") + "\">&#9650;</button>\n"
            + "                <button id=\"btn-next\" title=\"" + Messages.getString("ChatReport.Next") + "\">&#9660;</button>\n"
            + "             </div>\n" 
            + "             <span id=\"nav-counter\" class=\"nav-counter\">0 / 0</span>\n"
            + "             <button id=\"btn-clear\" title=\"" + Messages.getString("ChatReport.Clear") + "\">&#10005;</button>\n"
            + "        </div>\n"
            + "    </div>\n";

    public static String getNavControls() {
        return navControls; 
    }

    public static String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static String getUTF8String(ResultSet rs, String field) throws SQLException {
        String result = null;
        byte[] bytes = rs.getBytes(field);
        if (bytes != null) {
            result = new String(bytes, StandardCharsets.UTF_8);
        }
        return result;
    }

    public static String getNameFromId(String id) {
        if (id != null && id.contains("@")) { //$NON-NLS-1$
            id = id.split("@")[0]; //$NON-NLS-1$
        }
        return id;
    }

    public static String readResourceAsString(String resource) {
        byte[] bytes = readResourceAsBytes(resource);
        String result = ""; //$NON-NLS-1$
        if (bytes != null) {
            result = new String(bytes, StandardCharsets.UTF_8);
        }
        return result;
    }

    public static byte[] readResourceAsBytes(String resource) {
        byte[] result = null;
        try {
            result = IOUtil.loadInputStream(ChatUtil.class.getResourceAsStream(resource));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String readResourceAsBase64String(String resource) {
        byte[] bytes = readResourceAsBytes(resource);
        String result = ""; //$NON-NLS-1$
        if (bytes != null) {
            result = encodeBase64(bytes);
        }
        return result;
    }

    public static String getImageResourceAsEmbedded(String resource) {
        String ext = resource.substring(resource.lastIndexOf('.') + 1);
        String type = "jpg"; //$NON-NLS-1$
        if (ext.length() > 0) {
            type = ext;
        }
        return "data:image/" + type + ";base64," + readResourceAsBase64String(resource); //$NON-NLS-1$ $NON-NLS-2$
    }
    
    public static String nullToEmpty(String s) {
        if (s == null) {
            return ""; //$NON-NLS-1$
        }
        return s;
    }
}
