package br.gov.pf.iped.webapi.models;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="DataList")
public class DataListModel<T>{
	private T[] IDs;
	
	public DataListModel(T[] IDs) {
		this.IDs = IDs;
	}
	
	@ApiModelProperty()
	public T[] getData(){
		return this.IDs;
	}
}