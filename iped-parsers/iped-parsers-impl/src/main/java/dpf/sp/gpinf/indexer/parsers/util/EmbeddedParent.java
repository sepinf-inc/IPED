package dpf.sp.gpinf.indexer.parsers.util;

@Deprecated
public class EmbeddedParent {

    Object obj;

    public Object getObj() {
        return obj;
    }

    public void setObj(Object obj) {
        this.obj = obj;
    }

    public EmbeddedParent(Object o) {
        obj = o;
    }
}
