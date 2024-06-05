package iped.parsers.threema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import iped.utils.IOUtil;

/**
 *
 * @author André Rodrigues Costa <pfeifer.fmp@pf.gov.br>
 */
public class Util {

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

    private static byte[] readResourceAsBytes(String resource) {
        byte[] result = null;
        try {
            result = IOUtil.loadInputStream(Util.class.getResourceAsStream(resource));
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
