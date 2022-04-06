package org.apache.lucene.search;

import org.apache.lucene.search.PointRangeQuery;

public class PublicPointRangeQuery extends PointRangeQuery {

    private PointRangeQuery origQuery;

    public PublicPointRangeQuery(String field, PointRangeQuery origQuery) {
        super(field, origQuery.getLowerPoint(), origQuery.getUpperPoint(), origQuery.getNumDims());
        this.origQuery = origQuery;
    }

    @Override
    protected String toString(int dimension, byte[] value) {
        return origQuery.toString(dimension, value);
    }

}
