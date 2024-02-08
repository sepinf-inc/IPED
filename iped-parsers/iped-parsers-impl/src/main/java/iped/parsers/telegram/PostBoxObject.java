package iped.parsers.telegram;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PostBoxObject {
    int hash;
    final Map<String, Object> fields = new HashMap<String, Object>();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PostBoxObject{");
        boolean first = true;
        for (String key : fields.keySet()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(key).append('=');
            Object val = fields.get(key);
            String s = "null";
            if (val != null) {
                if (val instanceof PostBoxObject[]) {
                    s = Arrays.toString((PostBoxObject[]) val);
                } else if (val instanceof byte[]) {
                    s = "byte[" + ((byte[]) val).length + "]";
                } else if (val instanceof byte[][]) {
                    s = "byte[" + ((byte[][]) val).length + "][]";
                } else if (val instanceof String[]) {
                    s = Arrays.toString((String[]) val);
                } else if (val instanceof int[]) {
                    s = Arrays.toString((int[]) val);
                } else if (val instanceof long[]) {
                    s = Arrays.toString((long[]) val);
                } else {
                    s = val.toString();
                }
            }
            sb.append(s);
        }
        sb.append('}');
        return sb.toString();
    }

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
