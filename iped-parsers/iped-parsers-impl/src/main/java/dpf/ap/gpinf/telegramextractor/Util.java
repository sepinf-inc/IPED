package dpf.ap.gpinf.telegramextractor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.xml.bind.DatatypeConverter;

import dpf.sp.gpinf.indexer.util.IOUtil;

public class Util {

    private static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String base64ToHex(String str) {
        // ja é o hash
        if (str.length() == 64 && str.matches("^[a-f\\d]+$")) {
            return str;
        }
        return byteArrayToHex(DatatypeConverter.parseBase64Binary(str));
    }

    public static Object getAtribute(Object obj, String atr) throws Exception {
        Field f = obj.getClass().getField(atr);
        return f.get(obj);

    }

    public static Object deserialize(String classe, byte[] dados) {
        try {
            Object osd = Class.forName("SerializedData").getConstructor(byte[].class).newInstance(dados);
            Method m = osd.getClass().getMethod("readInt32");
            int aux = (Integer) m.invoke(osd, false);
            m = Class.forName(classe).getMethod("TLdeserialize");
            return m.invoke(null, osd, aux, false);
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return null;
        // TLRPC.User u = TLRPC.User.TLdeserialize(s, s.readInt32(false), false);
    }

    public static String hashFile(InputStream is) {
        String hash = null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buffer = new byte[255];
            while (is.read(buffer) != -1) {
                digest.update(buffer);
            }
            hash = byteArrayToHex(digest.digest());

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return hash;
    }

    public static String readResourceAsString(String resource) {
        try {
            byte[] bytes = IOUtil.loadInputStream(Util.class.getResourceAsStream(resource));
            return new String(bytes, StandardCharsets.UTF_8);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

}
