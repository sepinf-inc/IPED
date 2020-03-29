package dpf.mg.udi.gpinf.bittorrent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dampcake.bencode.BencodeInputStream;

/**
 * Helper class for decoding bencoded streams
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class BencodedDict {

    private Map<String, Object> dict;
    private DateFormat df;

    public BencodedDict(InputStream is, DateFormat df) throws IOException {
        dict = new BencodeInputStream(is, StandardCharsets.UTF_8, true).readDictionary();
        this.df = df;
    }

    private BencodedDict(Map<String, Object> dict, DateFormat df) {
        this.dict = dict;
        this.df = df;
    }

    public boolean containsKey(String key) {
        return dict.containsKey(key);
    }

    public Set<String> keySet() {
        return dict.keySet();
    }

    public String getDate(String key) {
        String resp = ""; //$NON-NLS-1$
        long num = getLong(key);
        if (num != 0) {
            Date date = new Date(num * 1000);
            resp = df.format(date);
        }
        return resp;
    }

    private Object getObject(String key) {
        if (dict.containsKey(key)) {
            return dict.get(key);
        }
        return null;
    }

    public long getLong(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof Long)) {
            return 0;
        }
        return (Long) obj;
    }

    public String getString(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof ByteBuffer)) {
            return ""; //$NON-NLS-1$
        }
        ByteBuffer buffer = (ByteBuffer) obj;
        return new String(buffer.array(), StandardCharsets.UTF_8);
    }

    public BencodedDict getDict(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof Map)) {
            return null;
        }

        @SuppressWarnings("unchecked") //$NON-NLS-1$
        Map<String, Object> childDict = (Map<String, Object>) obj;
        return new BencodedDict(childDict, df);
    }

    public List<Object> getList(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof List)) {
            return null;
        }

        @SuppressWarnings("unchecked") //$NON-NLS-1$
        List<Object> theList = (List<Object>) obj;
        List<Object> resp = new ArrayList<>(theList.size());

        for (Object elem : theList) {
            if (elem instanceof Map) {
                @SuppressWarnings("unchecked") //$NON-NLS-1$
                Map<String, Object> childDict = (Map<String, Object>) elem;
                resp.add(new BencodedDict(childDict, df));
            } else if (elem instanceof ByteBuffer) {
                ByteBuffer buff = (ByteBuffer) elem;
                resp.add(new String(buff.array(), StandardCharsets.UTF_8));
            } else {
                resp.add(elem);
            }
        }

        return resp;
    }

    /**
     * Returns a list of all String objects in list. Ignore elements that are not
     * Strings.
     * 
     * @param key
     * @return
     */
    public List<String> getListOfStrings(String key) {
        List<String> resp = new ArrayList<>();
        List<Object> listOfObj = getList(key);
        if (listOfObj != null) {
            for (Object obj : listOfObj) {
                if (obj instanceof ByteBuffer) {
                    ByteBuffer buff = (ByteBuffer) obj;
                    resp.add(new String(buff.array(), StandardCharsets.UTF_8));
                } else if (obj instanceof String) {
                    resp.add((String) obj);
                }
            }
        }
        return resp;
    }

    public String getHexEncodedBytes(String key) {
        String resp = ""; //$NON-NLS-1$

        if (containsKey(key)) {
            Object objBuffer = getObject(key);
            if (objBuffer != null && objBuffer instanceof ByteBuffer) {
                ByteBuffer buffer = (ByteBuffer) objBuffer;
                resp = new String(encodeHex(buffer.array()));
            }
        }

        return resp;
    }
    
    /**
     * Used to build output as Hex
     */
    private static final char[] DIGITS_LOWER =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    
    /**
     * Copied from apache commons codec binary.
     * 
     * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
     * The returned array will be double the length of the passed array, as it takes two characters to represent any
     * given byte.
     *
     * @param data
     *            a byte[] to convert to Hex characters
     * @param toDigits
     *            the output alphabet (must contain at least 16 chars)
     * @return A char[] containing the appropriate characters from the alphabet
     *         For best results, this should be either upper- or lower-case hex.
     * @since 1.4
     */
    private static char[] encodeHex(final byte[] data) {
        final int l = data.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = DIGITS_LOWER[(0xF0 & data[i]) >>> 4];
            out[j++] = DIGITS_LOWER[0x0F & data[i]];
        }
        return out;
    }
}
