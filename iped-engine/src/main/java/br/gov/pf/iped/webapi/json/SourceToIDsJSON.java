package br.gov.pf.iped.webapi.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;

/**
 * SourceToIDsModel lists documents grouped by source: { "data": [ { "source":
 * "A", "ids": [ 1, 2, 3 ] }, { "source": 1, "ids": [ 1, 2, 3 ] } ] }
 */
public class SourceToIDsJSON {

    private Map<String, List<Integer>> sourceToids;

    public SourceToIDsJSON() {
        this.sourceToids = new HashMap<String, List<Integer>>();
    }

    public SourceToIDsJSON(List<DocIDJSON> docs) {
        this();
        for (DocIDJSON doc : docs) {
            String source = doc.getSource();
            Integer id = new Integer(doc.getId());
            if (!this.sourceToids.containsKey(source)) {
                this.sourceToids.put(source, new ArrayList<Integer>());
            }
            List<Integer> ids = this.sourceToids.get(source);
            ids.add(id);
        }
    }

    @ApiModelProperty
    public List<DocIDGroupJSON> getData() {
        List<DocIDGroupJSON> result = new ArrayList<DocIDGroupJSON>();
        for (Map.Entry<String, List<Integer>> entry : this.sourceToids.entrySet()) {
            result.add(new DocIDGroupJSON(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public void setData(List<DocIDGroupJSON> data) {
        for (DocIDGroupJSON grp : data) {
            this.sourceToids.put(grp.getSource(), grp.getIds());
        }
    }
}
