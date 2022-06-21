package iped.engine.lucene;

import java.io.IOException;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;

public class DocValuesUtil {

    public static final String getVal(SortedDocValues sdv, int doc) {
        try {
            if (sdv.advanceExact(doc)) {
                return sdv.lookupOrd(sdv.ordValue()).utf8ToString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final String getVal(BinaryDocValues bdv, int doc) {
        BytesRef ret = getBytesRef(bdv, doc);
        return ret != null ? ret.utf8ToString() : null;
    }

    public static final BytesRef getBytesRef(BinaryDocValues bdv, int doc) {
        try {
            if (bdv.advanceExact(doc)) {
                return bdv.binaryValue();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final int getOrd(SortedDocValues sdv, int doc) {
        try {
            if (sdv.advanceExact(doc)) {
                return sdv.ordValue();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static final Long get(NumericDocValues ndv, int doc) {
        try {
            if (ndv.advanceExact(doc)) {
                return ndv.longValue();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
