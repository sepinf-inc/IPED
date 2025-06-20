package iped.utils;

public class TempAttributeInputStream extends EmptyInputStream {

    private String key;
    private Object object;

    public TempAttributeInputStream(String key, Object object) {
        this.key = key;
        this.object = object;
    }

    public String getKey() {
        return key;
    }

    public Object getObject() {
        return object;
    }

}
