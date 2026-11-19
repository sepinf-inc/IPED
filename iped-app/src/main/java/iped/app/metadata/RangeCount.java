package iped.app.metadata;

import java.text.NumberFormat;

import iped.utils.LocalizedFormat;

public class RangeCount extends ValueCount {
    double start, end;

    RangeCount(double start, double end, int ord, int count) {
        super(null, ord, count);
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        NumberFormat nf = LocalizedFormat.getNumberInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(getVal());
        sb.append(" ("); //$NON-NLS-1$
        sb.append(nf.format(count));
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String getVal() {
        StringBuilder sb = new StringBuilder();
        NumberFormat nf = LocalizedFormat.getNumberInstance();
        sb.append(nf.format(start));
        if (start != end && (!Double.isNaN(start) || !Double.isNaN(end))) {
            sb.append(' ');
            sb.append(MetadataSearch.RANGE_SEPARATOR);
            sb.append(' ');
            sb.append(nf.format(end));
        }
        return sb.toString();
    }

    public double getStart() {
        return start;
    }

    public double getEnd() {
        return end;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RangeCount) {
            RangeCount rc = (RangeCount) obj;
            if (this.start == rc.start && this.end == rc.end) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(start);
    }

}
