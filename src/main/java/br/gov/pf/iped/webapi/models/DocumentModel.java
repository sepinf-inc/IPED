package br.gov.pf.iped.webapi.models;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="Document")
public class DocumentModel{
	private int source;
	private int id;
	
	public DocumentModel() {
	}
	
	public DocumentModel(int source, int id) {
		this.source = source;
		this.id = id;
	}
	
	@ApiModelProperty()
	public int getSource(){
		return this.source;
	}

	public void setSource(int source) {
		this.source = source;
	}

	@ApiModelProperty()
	public int getId(){
		return this.id;
	}

	public void setId(int id) {
		this.id = id;
	}
}   	

