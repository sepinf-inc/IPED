package br.gov.pf.iped.webapi.json;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * DocIDModel identifies a single document: { "source": "A", "id": 0 }
 */
@ApiModel(value = "Document")
public class DocIDJSON {
    private String source;
    private int id;

    public DocIDJSON() {
    }

    public DocIDJSON(String source, int id) {
        this.source = source;
        this.id = id;
    }

    @ApiModelProperty()
    public String getSource() {
        return this.source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @ApiModelProperty()
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
