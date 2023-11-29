package iped.parsers.telegram;

import java.util.HashMap;
import java.util.Map;

public class PostBoxObject {
    int hash;
    final Map<String, Object> fields = new HashMap<String, Object>();

    public PostBoxObject getPostBoxObject(String key) {
        Object obj = fields.get(key);
        if (obj != null && obj instanceof PostBoxObject) {
            return (PostBoxObject) obj;
        }
        return null;
    }

    public String getString(String key) {
        Object obj = fields.get(key);
        if (obj != null && obj instanceof String) {
            return (String) obj;
        }
        return null;
    }

    public double getDouble(String key) {
        Object obj = fields.get(key);
        if (obj != null && obj instanceof Double) {
            return (Double) obj;
        }
        return 0;
    }

    public int getInteger(String key) {
        Object obj = fields.get(key);
        if (obj != null && obj instanceof Integer) {
            return (Integer) obj;
        }
        return 0;
    }

    public long getLong(String key) {
        Object obj = fields.get(key);
        if (obj != null && obj instanceof Long) {
            return (Long) obj;
        }
        return 0;
    }

    public PostBoxObject[] getPostBoxObjectArray(String key) {
        Object obj = fields.get(key);
        if (obj != null && obj instanceof PostBoxObject[]) {
            return (PostBoxObject[]) obj;
        }
        return null;
    }

    public byte[] getBytes(String key) {
        Object obj = fields.get(key);
        if (obj != null && obj instanceof byte[]) {
            return (byte[]) obj;
        }
        return null;
    }
}
