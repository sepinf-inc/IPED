package br.gov.pf.iped.webapi.json;

import java.util.Arrays;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * DataListModel puts an array in a "data" property: { "data": [] }
 */
@ApiModel(value = "DataList")
public class DataListJSON<T> {
    private List<T> IDs;

    public DataListJSON(T[] IDs) {
        this.IDs = Arrays.asList(IDs);
    }

    public DataListJSON(List<T> IDs) {
        this.IDs = IDs;
    }

    @ApiModelProperty()
    public List<T> getData() {
        return this.IDs;
    }
}