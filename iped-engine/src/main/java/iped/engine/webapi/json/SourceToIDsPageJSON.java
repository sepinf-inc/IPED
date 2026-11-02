package iped.engine.webapi.json;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;

/**
 * SourceToIDsPageJSON is the explicitly paginated variant of
 * {@link SourceToIDsJSON} (design 07a):
 * { "data": [ { "source": "A", "ids": [ 1, 2, 3 ] } ], "total": 42 }
 * where total is the count of all documents matching the effective query in
 * the request scope, regardless of the returned window. It is used only when
 * the client sends limit and/or offset; the unpaginated legacy response keeps
 * using {@link SourceToIDsJSON} unchanged.
 */
public class SourceToIDsPageJSON extends SourceToIDsJSON {

    private int total;

    public SourceToIDsPageJSON() {
    }

    public SourceToIDsPageJSON(List<DocIDJSON> docs, int total) {
        super(docs);
        this.total = total;
    }

    @ApiModelProperty
    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
