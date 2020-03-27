package dpf.mg.udi.gpinf.bittorrent;

import java.io.IOException;
import java.io.InputStream;
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
        dict = new BencodeInputStream(is).readDictionary();
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
        if (obj == null || !(obj instanceof String)) {
            return ""; //$NON-NLS-1$
        }
        return (String) obj;
    }

    public BencodedDict getDict(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof Map)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> childDict = (Map<String, Object>) obj;
        return new BencodedDict(childDict, df);
    }
    
    public List<Object> getList(String key) {
        Object obj = getObject(key);
        if (obj == null || !(obj instanceof List)) {
            return null;
        }
        
        @SuppressWarnings("unchecked")
        List<Object> theList = (List<Object>) obj;
        List<Object> resp = new ArrayList<>(theList.size());
        
        for (Object elem: theList) {
            if (elem instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childDict = (Map<String, Object>) elem;
                resp.add(new BencodedDict(childDict, df));
            } else {
                resp.add(elem);
            }
        }
        
        return resp;
    }
}
