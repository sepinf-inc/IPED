/*
 * Classname             GeoXmlIped
 * 
 * Version information   1.0
 *
 * Date                  26/07/2016
 * 
 * author                Patrick Dalla Bernardina
 * 
 * Extensão da classe GeoXML para integrar com as funcionalidades de seleção de marcadores
 * bem como com os Event Listeners das classes Java.
 * 
 * */

GeoXmlIped.prototype = Object.create(GeoXml.prototype);
GeoXmlIped.prototype.constructor=GeoXmlIped;

function GeoXmlIped(myvar, map, url, opts) {
	GeoXml.call(this, myvar, map, url, opts);
	
	this.navigationPos = 0;
}

GeoXmlIped.prototype.selecionaRetangulo = function (rect, proj, callback){
	var sw = proj.fromContainerPixelToLatLng(new google.maps.Point(rect.left, rect.bottom));
  	var ne = proj.fromContainerPixelToLatLng(new google.maps.Point(rect.right, rect.top));

  	var bnds = new google.maps.LatLngBounds(sw, ne);

  	var markers = this.overlayman.cluster.getMarkers();
  	var markersin = [];  	
  	
  	for (var i = 0; i < markers.length ; i++ ){
  		if(bnds.contains(markers[i].getPosition())){
  			markersin.push(markers[i].extendedData.id);
  			markers[i].extendedData.selected = 'true';
			this.ajustaIcone(markers[i]);
  			if (typeof callback === "function") {
  				callback(markers[i]);  			
  			}
  		}
  	}
  	
  	return markersin;
}

GeoXmlIped.prototype.selecionaCirculo = function (pInicio, pFim, proj, callback){
   	var inicio = proj.fromContainerPixelToLatLng(pInicio);
  	var fim = proj.fromContainerPixelToLatLng(pFim);
  	
	var dist = google.maps.geometry.spherical.computeDistanceBetween(inicio, fim);
  	var markers = this.overlayman.cluster.getMarkers();
  	var markersin = [];  	
  	for (var i = 0; i < markers.length ; i++ ){
  		var distM = google.maps.geometry.spherical.computeDistanceBetween(inicio, markers[i].getPosition());
  		if(dist > distM){
  			markersin.push(markers[i].extendedData.id);
  			markers[i].extendedData.selected = 'true';
  			this.ajustaIcone(markers[i]);
  			if (typeof callback === "function") {
  				callback(markers[i]);
  			}
  		}
  	}
  	
  	return markersin;
}

GeoXmlIped.prototype.ajustaIcone = function( mark ) {
	var bicon = mark.getIcon();
	if(mark.extendedData.selected == 'true'){
		if(mark.extendedData.checked == 'true'){
			bicon.url=this.icone_marcador_selecionado_m;
		}else{
			bicon.url=this.icone_marcador_selecionado;
		}
	}else{
		if(mark.extendedData.checked == 'true'){
			bicon.url=this.icone_marcador_m;
		}else{
			bicon.url=this.icone_marcador;
		}
	}
	mark.setIcon(bicon);	
}

GeoXmlIped.prototype.checkMarker = function(mpos, checked){
	for (i = 0; i <this.overlayman.markers.length; i++) {
		var m = this.overlayman.markers[i];
		m.extendedData.selected = 'false';
		this.ajustaIcone(m);
	}

	m = this.overlayman.markers[mpos];
	m.extendedData.selected = 'true';
	m.extendedData.checked = ''+checked;
	this.ajustaIcone(m);
}

/* Extendido para processar extendedData de Placemarks e Listeners */
GeoXmlIped.prototype.handlePlacemark = function(mark, idx, depth, fullstyle) {
	//chama o método herdado
	GeoXml.prototype.handlePlacemark.call(this, mark, idx, depth, fullstyle);
	
	
	//pega o ultimo marcador criado (que corresponde ao que acabou de ser criado
	var m = this.overlayman.markers[this.overlayman.markers.length-1];
	m.arrayPos = this.overlayman.markers.length-1;

	var e = mark.getElementsByTagName("ExtendedData");
	if(e.length<=0) return;
	
	m.extendedData = {};
	var marcado = '';
	for (i = 0; i <e.length; i++) {
		var d = e[i].getElementsByTagName("Data");
		for (j = 0; j <d.length; j++) {
			values = d[j].getElementsByTagName("value");
			m.extendedData[d[j].getAttribute("name")] = values[0].childNodes[0].nodeValue;

			if(d[j].getAttribute("name")=="checked"){
				if(values[0].childNodes[0].nodeValue=="true"){
					marcado = "checked";
				}
			}
		}
	}

	this.ajustaIcone(m);

	/* Adiciona checkbox ao infoWindow*/
	
	var html = m.infoWindow.getContent();
	html = html.substring(0,html.indexOf(">")+1)+"<input type=\"checkbox\" id=\"ck_marcador_"+m.extendedData.id+"\" "+marcado+" onclick=\"window.app.marcaMarcadorBF("+m.extendedData.id+", this.checked);gxml.checkMarker("+m.arrayPos+", this.checked);\" />" + html.substring(html.indexOf(">")+1, html.length);
	m.infoWindow.setContent(html);
	
	/* Adiciona listeners */
	google.maps.event.addListener(m, "mouseover", function(){ window.app.markerMouseEnteredBF(this.extendedData.id) });
	google.maps.event.addListener(m, "mouseout", function(){ window.app.markerMouseExitedBF(this.extendedData.id) });
	google.maps.event.addListener(m, "click", function(){
	    var e = window.event;
		var button = (typeof e.which != "undefined") ? e.which : e.button;
		
		if(!(e.shiftKey||e.ctrlKey)){
			//desseleciona todos os itens imitando o comportamento da tabela
			for(var i =0; i<this.geoxml.overlayman.markers.length; i++){
				this.geoxml.overlayman.markers[i].extendedData.selected = 'false';
				this.geoxml.ajustaIcone(this.geoxml.overlayman.markers[i]);
			}
		}
		
		//seleciona o item clicado
		this.geoxml.navigationPos=this.arrayPos;
		if(e.shiftKey){
			if(this.extendedData.selected == 'true'){
				this.extendedData.selected='false';
			}else{
				this.extendedData.selected='true';
			}
			this.geoxml.ajustaIcone(this);
			
			window.app.markerMouseClickedBF(this.extendedData.id, button, 'shift');	
		}else{
			this.extendedData.selected='true';
			this.geoxml.ajustaIcone(this);
			
			window.app.markerMouseClickedBF(this.extendedData.id, button, '');	
		}
		 
		});
	google.maps.event.addListener(m, "dblclick", function(){ 
	    var e = window.event;
		var button = (typeof e.which != "undefined") ? e.which : e.button;
		window.app.markerMouseDblClickedBF(this.extendedData.id, button); 
		});
	google.maps.event.addListener(m, "mousedown", function(){ window.app.markerMousePressedBF(this.extendedData.id) });
	google.maps.event.addListener(m, "mouseup", function(){ window.app.markerMouseReleasedBF(this.extendedData.id) });
}

/* Extendido para processar informação da ordem de navegação */
GeoXmlIped.prototype.processKML = function(node, marks, title, sbid, depth, paren) {
	GeoXml.prototype.processKML.call(this, node, marks, title, sbid, depth, paren);
	
	this.tourOrder = "id";
	var tours = node.getElementsByTagName("gx:Tour");
	if(tours.length>0){
		var names = tours[0].getElementsByTagName("name");
		this.tourOrder = names[0].childNodes[0].nodeValue;
	}
}

/* função para seleção programática de item no mapa */
GeoXmlIped.prototype.seleciona = function(mid, selecionado) {
	for (i = 0; i <this.overlayman.markers.length; i++) {
		var m = this.overlayman.markers[i];
		if(m.extendedData.id == mid){
			if(selecionado == 'true'){
				m.extendedData.selected = 'true';
			}else{
				m.extendedData.selected = 'false';
			}
			this.ajustaIcone(m);			
		}		
	}
}
