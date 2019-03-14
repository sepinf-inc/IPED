package br.gov.pf.iped.webapi.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;

/** SourceToIDsModel lists documents grouped by source:
 * {
 *  "data": [
 *    {
 *      "source": 0,
 *      "ids": [ 1, 2, 3 ]
 *    },
 *    {
 *      "source": 1,
 *      "ids": [ 1, 2, 3 ]
 *    }
 *  ]
 * }
 */
public class SourceToIDsModel {
	
	private Map<Integer, List<Integer>> sourceToids;
	
	public SourceToIDsModel() {
		this.sourceToids = new HashMap<Integer, List<Integer>>();
	}
	
	public SourceToIDsModel(List<DocIDModel> docs) {
		this();
		for (DocIDModel doc: docs) {
			Integer source = new Integer(doc.getSource());
			Integer id = new Integer(doc.getId());
			if (!this.sourceToids.containsKey(source)) {
				this.sourceToids.put(source, new ArrayList<Integer>());
			}
			List<Integer> ids = this.sourceToids.get(source);
			ids.add(id);
		}
	}
	
	@ApiModelProperty
	public List<DocIDGroupModel> getData() {
		List<DocIDGroupModel> result = new ArrayList<DocIDGroupModel>();
		for (Map.Entry<Integer, List<Integer>> entry: this.sourceToids.entrySet()) {
			result.add(new DocIDGroupModel(entry.getKey(), entry.getValue()));
		}
		return result;
	}
	
	public void setData(List<DocIDGroupModel> data) {
		for (DocIDGroupModel grp: data) {
			this.sourceToids.put(grp.getSource(), grp.getIds());
		}
	}
}
