/**/
GeoXmlIped.prototype = Object.create(GeoXml.prototype);
GeoXmlIped.prototype.constructor=GeoXmlIped;

function GeoXmlIped(myvar, map, url, opts) {
	GeoXml.call(this, myvar, map, url, opts);
}

GeoXmlIped.prototype.selecionaRetangulo = function (rect, proj, callback){
	
	var sw = proj.fromContainerPixelToLatLng(new google.maps.Point(rect.left, rect.bottom));
  	var ne = proj.fromContainerPixelToLatLng(new google.maps.Point(rect.right, rect.top));

  	var bnds = new google.maps.LatLngBounds(sw, ne);

  	var markers = this.overlayman.cluster.getMarkers();
  	var markersin = [];  	
  	
  	for (var i = 0; i < markers.length ; i++ ){
  		//alert(bnds+"\n"+markers[i].getPosition());
  		
  		if(bnds.contains(markers[i].getPosition())){
  			markersin.push(markers[i].extendedData.id);
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
  			if (typeof callback === "function") {
  				callback(markers[i]);  			
  			}
  		}
  	}
  	
  	return markersin;
}

/* Extendido para processar extendedData e Listeners */
GeoXmlIped.prototype.handlePlacemark = function(mark, idx, depth, fullstyle) {
	//chama o método herdado
	GeoXml.prototype.handlePlacemark.call(this, mark, idx, depth, fullstyle);
	
	
	//pega o ultimo marcador criado (que corresponde ao que acabou de ser criado
	var m = this.overlayman.markers[this.overlayman.markers.length-1];

	var e = mark.getElementsByTagName("ExtendedData");
	if(e.length<=0) return;
	
	m.extendedData = {};
	for (i = 0; i <e.length; i++) {
		var d = e[i].getElementsByTagName("Data");
		for (j = 0; j <d.length; j++) {
			values = d[j].getElementsByTagName("value");
			m.extendedData[d[j].getAttribute("name")] = values[0].childNodes[0].nodeValue;
		}
	}
	
	/* Adiciona listeners */
	google.maps.event.addListener(m, "mouseover", function(){ markerMouseEnteredBF(this.extendedData.id) });
	google.maps.event.addListener(m, "mouseout", function(){ markerMouseExitedBF(this.extendedData.id) });
	google.maps.event.addListener(m, "click", function(){
	    var e = window.event;
		var button = (typeof e.which != "undefined") ? e.which : e.button;
		markerMouseClickedBF(this.extendedData.id, button); 
		});
	google.maps.event.addListener(m, "dblclick", function(){ 
	    var e = window.event;
		var button = (typeof e.which != "undefined") ? e.which : e.button;
		markerMouseDblClickedBF(this.extendedData.id, button); 
		});
	google.maps.event.addListener(m, "mousedown", function(){ markerMousePressedBF(this.extendedData.id) });
	google.maps.event.addListener(m, "mouseup", function(){ markerMouseReleasedBF(this.extendedData.id) });
	
	
}
