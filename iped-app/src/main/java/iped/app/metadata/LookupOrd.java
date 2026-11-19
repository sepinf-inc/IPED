package iped.app.metadata;

import java.io.IOException;

public abstract class LookupOrd {
    boolean isCategory = false;

    public abstract String lookupOrd(int ord) throws IOException;

    public boolean isCategory() {
        return isCategory;
    }

    public void setCategory(boolean isCategory) {
        this.isCategory = isCategory;
    }
}
