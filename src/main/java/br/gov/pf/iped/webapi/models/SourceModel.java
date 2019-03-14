package br.gov.pf.iped.webapi.models;

import io.swagger.annotations.ApiModelProperty;

/** SourceModel represents a IPED source:
 * {
 *   "id": 0,
 *   "path": "string"
 * }
*/
public class SourceModel {
	private int id;
	private String path;
	
	@ApiModelProperty
	public int getId() {
		return id;
	}
	public void setId(int id) {
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
