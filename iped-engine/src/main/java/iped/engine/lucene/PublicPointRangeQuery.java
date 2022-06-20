package iped.engine.lucene;

import org.apache.commons.codec.binary.Hex;
import org.apache.lucene.search.PointRangeQuery;

public class PublicPointRangeQuery extends PointRangeQuery {

    private PointRangeQuery origQuery;

    public PublicPointRangeQuery(String field, PointRangeQuery origQuery) {
        super(field, origQuery.getLowerPoint(), origQuery.getUpperPoint(), origQuery.getNumDims());
        this.origQuery = origQuery;
    }

    @Override
    protected String toString(int dimension, byte[] value) {
        //return origQuery.toString(dimension, value);
        return "Dim:" + dimension + " Value:" + Hex.encodeHexString(value);
    }

}
