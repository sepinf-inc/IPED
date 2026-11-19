package iped.app.metadata;

import java.text.NumberFormat;

import iped.app.ui.Messages;
import iped.engine.localization.CategoryLocalization;
import iped.utils.LocalizedFormat;

public class ValueCount {
    LookupOrd lo;
    protected int ord, count;
    String cachedStringValue = null;

    public ValueCount(LookupOrd lo, int ord, int count) {
        this.lo = lo;
        this.ord = ord;
        this.count = count;
    }

    public String getVal() {
        try {
            String val = lo.lookupOrd(ord);
            if (lo.isCategory) {
                val = CategoryLocalization.getInstance().getLocalizedCategory(val);
            }
            return val;

        } catch (Exception e) {
            // LookupOrd get invalid if UI is updated when processing (IndexReader closed)
            // e.printStackTrace();
            return Messages.getString("MetadataPanel.UpdateWarn"); //$NON-NLS-1$
        }
    }

    @Override
    public String toString() {
        if (cachedStringValue == null) {
            NumberFormat nf = LocalizedFormat.getNumberInstance();
            cachedStringValue = getVal() + " (" + nf.format(count) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return cachedStringValue;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ValueCount) && ((ValueCount) obj).ord == this.ord;
    }

    @Override
    public int hashCode() {
        return ord;
    }

    public int getOrd() {
        return ord;
    }

    public void setOrd(int ord) {
        this.ord = ord;
    }

    public void incrementCount() {
        count++;
    }

}
