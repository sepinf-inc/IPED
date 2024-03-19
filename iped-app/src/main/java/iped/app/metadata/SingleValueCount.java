package iped.app.metadata;

import java.text.NumberFormat;

import iped.utils.LocalizedFormat;

public class SingleValueCount extends ValueCount implements Comparable<SingleValueCount> {
    double value;

    SingleValueCount(double value) {
        super(null, 0, 0);
        this.value = value;
    }

    @Override
    public String toString() {
        NumberFormat nf = LocalizedFormat.getNumberInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(nf.format(value));
        sb.append(" ("); //$NON-NLS-1$
        sb.append(nf.format(count));
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String getVal() {
        NumberFormat nf = LocalizedFormat.getNumberInstance();
        return nf.format(value);
    }

    public int compareTo(SingleValueCount o) {
        return Double.compare(value, o.value);
    }
}
