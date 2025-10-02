package iped.viewers.search;

import java.util.EventObject;

public class SearchEvent extends EventObject {
    private static final long serialVersionUID = -9126965849951582631L;
    private final int type;
    public static final int termChange = 1;
    public static final int prevHit = 2;
    public static final int nextHit = 3;

    public SearchEvent(Object source, int type) {
        super(source);
        this.type = type;
    }

    public int getType() {
        return type;
    }
}
