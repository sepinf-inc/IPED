package br.gov.pf.iped.webapi.json;

import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;

/** DocPropsModel list properties of a document:
 * {
 *  "source": 0,
 *  "id": 0,
 *  "luceneId": 0,
 *  "properties": {},
 *  "bookmarks": [
 *    "string"
 *  ],
 *  "selected": false
 *}
*/
public class DocPropsJSON {
	private int source;
	private int id;
	private int luceneId;
	private Map<String, String[]> properties;
	private List<String> bookmarks;
	private boolean selected;
	
	@ApiModelProperty()
	public int getSource() {
		return source;
	}
	public void setSource(int source) {
		this.source = source;
	}

	@ApiModelProperty()
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	
	@ApiModelProperty()
	public int getLuceneId() {
		return luceneId;
	}
	public void setLuceneId(int luceneId) {
		this.luceneId = luceneId;
	}

	@ApiModelProperty()
	public Map<String, String[]> getProperties() {
		return properties;
	}
	public void setProperties(Map<String, String[]> properties) {
		this.properties = properties;
	}

	@ApiModelProperty()
	public List<String> getBookmarks() {
		return bookmarks;
	}
	public void setBookmarks(List<String> bookmarks) {
		this.bookmarks = bookmarks;
	}
	
	@ApiModelProperty()
	public boolean isSelected() {
		return selected;
	}
	public void setSelected(boolean selected) {
		this.selected = selected;
	}
}
