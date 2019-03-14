package br.gov.pf.iped.webapi.json;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;

/** DocIDGroupModel lists IDs of a single source:
 * {
 *   "source": 0,
 *   "ids":[0,1,2]
 * }
*/
public class DocIDGroupJSON{
	private Integer source;
	private List<Integer> ids;
	
	public DocIDGroupJSON() {}
	public DocIDGroupJSON(Integer source, List<Integer> ids) {
		this.source = source;
		this.ids = ids;
	}
	
	@ApiModelProperty
	public Integer getSource() {
		return source;
	}
	public void setSource(Integer source) {
		this.source = source;
	}

	@ApiModelProperty
	public List<Integer> getIds() {
		return ids;
	}
	public void setIds(List<Integer> ids) {
		this.ids = ids;
	}
}
