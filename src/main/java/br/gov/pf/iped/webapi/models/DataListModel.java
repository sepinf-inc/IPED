package br.gov.pf.iped.webapi.models;

import java.util.Arrays;
import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** DataListModel puts an array in a "data" property:
 * {
 *   "data": []
 * }
*/
@ApiModel(value="DataList")
public class DataListModel<T>{
	private List<T> IDs;
	
	public DataListModel(T[] IDs) {
		this.IDs = Arrays.asList(IDs);
	}
	
	public DataListModel(List<T> IDs) {
		this.IDs = IDs; 
	}
	
	@ApiModelProperty()
	public List<T> getData(){
		return this.IDs;
	}
}