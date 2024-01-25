package iped.parsers.bittorrent;

import java.io.ByteArrayOutputStream;
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

import org.apache.commons.codec.binary.Hex;

import com.dampcake.bencode.BencodeInputStream;
import com.dampcake.bencode.BencodeOutputStream;

import iped.parsers.util.Util;

/**
 * Helper class for decoding bencoded streams
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@pf.gov.br>
 */
public class BencodedDict {

    private Map<String, Object> dict;
    private DateFormat df;

    public BencodedDict(InputStream is, DateFormat df) throws IOException {
        this(is, df, false);
    }

    public BencodedDict(InputStream is, DateFormat df, boolean canBeIncomplete) throws IOException {
        if (canBeIncomplete) {
            dict = new BencodedIncompleteInputStream(is, StandardCharsets.UTF_8, true).readDictionary();
        } else {
            dict = new BencodeInputStream(is, StandardCharsets.UTF_8, true).readDictionary();
        }
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

    public Long getLongNull(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof Long)) {
            return null;
        }
        return (Long) obj;
    }

    public String getString(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof ByteBuffer)) {
            return ""; //$NON-NLS-1$
        }
        ByteBuffer buffer = (ByteBuffer) obj;
        return getUnknownCharsetString(buffer.array());
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
                resp.add(getUnknownCharsetString(buff.array()));
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
                    resp.add(getUnknownCharsetString(buff.array()));
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
                resp = Hex.encodeHexString(buffer.array(), false);
            }
        }

        return resp;
    }

    public byte[] getDictBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BencodeOutputStream bos = new BencodeOutputStream(baos)) {
            bos.writeDictionary(dict);
            return baos.toByteArray();
        } catch (IOException e) {
        }
        return null;
    }

    public byte[] getBytes(String key) {
        byte[] bytes = null;

        if (containsKey(key)) {
            Object objBuffer = getObject(key);
            if (objBuffer != null && objBuffer instanceof ByteBuffer) {
                ByteBuffer buffer = (ByteBuffer) objBuffer;
                bytes = buffer.array().clone();
            }
        }

        return bytes;
    }

    private String getUnknownCharsetString(byte[] data) {
        return Util.decodeUnknowCharset(data);
    }

    @Override
    public String toString() {
        return dict.toString();
    }

    public boolean isIncomplete() {
        return (dict instanceof BencodedIncompleteInputStream) && ((BencodedIncompleteInputStream) dict).isIncomplete();
    }
}
