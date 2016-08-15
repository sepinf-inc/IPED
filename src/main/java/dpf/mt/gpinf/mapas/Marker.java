package dpf.mt.gpinf.mapas;

import java.util.Date;
import java.util.HashMap;

public class Marker {
	int id;
	double lat;
	double lng;
	String titulo;
	String descricao;
	HashMap<String, String> extendedData;
	Date beginTimeSpan;
	Date endTimeSpan;
	Date Timestamp;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public double getLat() {
		return lat;
	}
	public void setLat(double lat) {
		this.lat = lat;
	}
	public double getLng() {
		return lng;
	}
	public void setLng(double lng) {
		this.lng = lng;
	}
	public String getTitulo() {
		return titulo;
	}
	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}
	public String getDescricao() {
		return descricao;
	}
	public void setDescricao(String descricao) {
		this.descricao = descricao;
	}
	public HashMap<String, String> getExtendedData() {
		return extendedData;
	}
	public void setExtendedData(HashMap<String, String> extendedData) {
		this.extendedData = extendedData;
	}
	public Date getBeginTimeSpan() {
		return beginTimeSpan;
	}
	public void setBeginTimeSpan(Date beginTimeSpan) {
		this.beginTimeSpan = beginTimeSpan;
	}
	public Date getEndTimeSpan() {
		return endTimeSpan;
	}
	public void setEndTimeSpan(Date endTimeSpan) {
		this.endTimeSpan = endTimeSpan;
	}
	public Date getTimestamp() {
		return Timestamp;
	}
	public void setTimestamp(Date timestamp) {
		Timestamp = timestamp;
	}
}
