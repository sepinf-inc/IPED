package br.gov.pf.iped.webapi.json;

import io.swagger.annotations.ApiModelProperty;

/**
 * SourceModel represents a IPED source: { "id": "A", "path": "string" }
 */
public class SourceJSON {
    private String id;
    private String path;

    @ApiModelProperty
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @ApiModelProperty
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
