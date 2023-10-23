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

var lastAddedPlacemark = null;

function GeoXmlIped(myvar, map, url, opts) {
    if(opts.clustering){
    }else{
        opts.clustering={};
    }
    opts.clustering.gridSize = 50;
	GeoXml.call(this, myvar, map, url, opts);

	this.selectedArray=[];
	this.addpromises=[];
	this.calcBounds = new google.maps.LatLngBounds();

	this.navigationPos = 0;

    html = "";
    this.infoWindowOptions = { 
                    content: html, 
                    pixelOffset: new google.maps.Size(0, 2)
                };
    this.infoWindow = new google.maps.InfoWindow(this.infoWindowOptions);

}

GeoXmlIped.prototype.createMarkerIcon = function (icon){
    let scale = 1;

    return new google.maps.MarkerImage( icon,
        new google.maps.Size(32*scale, 32*scale), //size
        new google.maps.Point(0, 0), //origin
        new google.maps.Point(16*scale, 16*scale), //anchor
        new google.maps.Size(32*scale, 32*scale) //scaledSize 
        );
}

GeoXmlIped.prototype.setHighlightedIcon = function (icon){
    this.highlightedIcon = this.createMarkerIcon(icon);
}

GeoXmlIped.prototype.setHighlightedCheckedIcon = function (icon){
    this.highlightedCheckedIcon = this.createMarkerIcon(icon);
}

GeoXmlIped.prototype.setCheckedIcon = function (icon){
    this.checkedIcon = this.createMarkerIcon(icon);
}

GeoXmlIped.prototype.setIcon = function (icon){
    this.icon = this.createMarkerIcon(icon);
}

GeoXmlIped.prototype.addPlacemark = function (gid, name, descr, lat, longit, checked, selected){
    let point = new google.maps.LatLng(lat, longit);
    let href = '';
    var m = new google.maps.Marker({position: point, map: this.map});
    m.id = gid;
    m.title = name;
    m.name = name;
    m.descr = descr;
    m.href = href;
    m.geoxml = this;
    if(lastAddedPlacemark){
        m.previous=lastAddedPlacemark;
        lastAddedPlacemark.next=m;
    }else{
        m.previous=null;
    }
    m.next=null;
    lastAddedPlacemark=m;
    var obj = { "type": "point", "title": name, "description": escape(descr), "href": href, "shadow": null, "visibility": true, "x": point.x, "y": point.y, "id": m.id };
    this.kml[0].marks.push(obj);
    //function(point, name, desc, styleid, idx, instyle, visible, kml_id, markerurl,snip) 
    m.extendedData = {};
    m.extendedData.id = gid;
    m.extendedData.selected = selected;
    m.extendedData.checked = checked;    
    
    let icon = null;
    if(m.extendedData.selected=='true'){
        if(m.extendedData.checked =='true'){
            icon = this.highlightedCheckedIcon;
        }else{
            icon = this.highlightedIcon;
        }
    }else{
        if(m.extendedData.checked =='true'){
            icon = this.checkedIcon;
        }else{
            icon = this.icon;
        }
    }
    
    m.setIcon(icon);

    let that = this;
    
    that.addMarker(m, name, 0, '', true);
    
    m.arrayPos = that.overlayman.markers.length-1;  
    that.bounds.extend(m.getPosition());      
    that.handleMarkerEvents(m);    
}

GeoXmlIped.prototype.addMarker = function (marker, title, idx, sidebar, visible, forcevisible){
    try{
        marker.hidden = false;
        marker.title = title;
        this.overlayman.folders[idx].push(this.overlayman.markers.length);
        this.overlayman.markers.push(marker);
        marker.onMap = true;
        if(!!marker.label){ marker.label.setMap(this.overlayman.map);}
    
        marker.isAdded = true;
        this.overlayman.cluster.markers_.push(marker);
    
        if(sidebar){
            this.overlayman.folderhtml[idx].push(sidebar);
        }
    }catch(e){
        alert(e);
    }
}

GeoXmlIped.prototype.createNextDirectionLine = function(m){
    if(m.nextLine){
    }else{
        if(m.next){
            m.nextLine =  new google.maps.Polyline({
                geodesic:true,
                icons:[
                    {
                    icon:lineSymbol,
                    offset: "100%"
                    }
                ],
                });
            const path = m.nextLine.getPath();
            path.push(m.getPosition());
            path.push(m.next.getPosition());
        }
    }
}

GeoXmlIped.prototype.createDirectionLines = function(m){
    this.createNextDirectionLine(m);
    if(m.previousLine){
    }else{
        if(m.previous){
            this.createNextDirectionLine(m.previous);
            m.previousLine = m.previous.nextLine;
        }
    }
}

const lineSymbol = {
    path: google.maps.SymbolPath.FORWARD_CLOSED_ARROW,
  };

GeoXmlIped.prototype.showDirectionLines = function(m){
    this.createDirectionLines(m);
    if(m.nextLine){
        //this.nextLine.setStyle(this.parent.nextlinestyle);
        m.nextLine.setOptions({strokeColor: 'red'});
        m.nextLine.setMap(this.map);
    }
    if(m.previousLine) {
        //this.previousLine.setStyle(this.parent.previouslinestyle);
        m.previousLine.setOptions({strokeColor: 'blue'});
        m.previousLine.setMap(this.map);
    }
    m.directionLinesVisible=true;
}

GeoXmlIped.prototype.hideDirectionLines = function(m){
    this.createDirectionLines(m);
    if(m.nextLine) m.nextLine.setMap(null);
    if(m.previousLine) m.previousLine.setMap(null); 
    this.directionLinesVisible=false;
}

GeoXmlIped.prototype.flushPlacemarks = function() {
    var that=this;
    Promise.all(that.addpromises).then((markers)=>{
        try{
            that.map.fitBounds(that.bounds);
        }catch(e){
            alert(e);
        }
    });
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
  			this.selectedArray.push(markers[i]);
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
  			this.selectedArray.push(markers[i]);
  			this.ajustaIcone(markers[i]);
  			if (typeof callback === "function") {
  				callback(markers[i]);
  			}
  		}
  	}
  	
  	return markersin;
}

GeoXmlIped.prototype.ajustaIcone = function( m ) {
	var icon = null;
    if(m.extendedData.selected=='true'){
        if(m.extendedData.checked =='true'){
            icon = this.highlightedCheckedIcon;
        }else{
            icon = this.highlightedIcon;
        }
    }else{
        if(m.extendedData.checked =='true'){
            icon = this.checkedIcon;
        }else{
            icon = this.icon;
        }
    }
	m.setIcon(icon);
    if(m.extendedData.selected == 'true'){
        m.setZIndex(mark.getZIndex()+1);
    }
}

GeoXmlIped.prototype.checkMarker = function(mpos, checked){
	for (i = 0; i <this.selectedArray.length; i++) {
		var m = selectedArray[i];
        m.extendedData.selected = 'false';
        this.ajustaIcone(m);
	}
	this.selectedArray=[];

	m = this.overlayman.markers[mpos];
	m.extendedData.selected = 'true';
	this.selectedArray.push(m);
	m.extendedData.checked = ''+checked;
	this.ajustaIcone(m);
}

/* Extendido para processar extendedData de Placemarks e Listeners */
GeoXmlIped.prototype.handlePlacemark = function(mark, idx, depth, fullstyle) {
	//chama o método herdado
	GeoXml.prototype.handlePlacemark.call(this, mark, idx, depth, fullstyle);

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

    var m = this.overlayman.markers[this.overlayman.markers.length-1];
    m.arrayPos = this.overlayman.markers.length-1;
    this.handleMarkerEvents(m);
}	

GeoXmlIped.prototype.handleMarkerEvents = function(m, idx, depth, fullstyle) {

    m.infoWindow = this.infoWindow;
    
	/* Adiciona listeners */
	google.maps.event.addListener(m, "mouseover", function(){ window.app.markerMouseEnteredBF(this.extendedData.id) });
	google.maps.event.addListener(m, "mouseout", function(){ window.app.markerMouseExitedBF(this.extendedData.id) });
	google.maps.event.addListener(m, "click", function(){
        try{
            var html = "<h1 " + this.titlestyle + ">" + m.name + "</h1>";
            html +=  "<div " + this.descstyle + ">" + m.descr + "</div>";
        
            var marcado = '';
            if(m.checked){
                marcado = 'checked';
            }
            html = html.substring(0,html.indexOf(">")+1)+"<input type=\"checkbox\" id=\"ck_marcador_"+m.extendedData.id+"\" "+marcado+" onclick=\"window.app.checkMarkerBF(\'"+m.extendedData.id+"\', this.checked);gxml.checkMarker("+m.arrayPos+", this.checked);\" />" + html.substring(html.indexOf(">")+1, html.length);
            m.infoWindow.setContent(html);
            m.infoWindow.open({anchor: m, map});
            
            
            var e = window.event;
            var button = (typeof e.which != "undefined") ? e.which : e.button;
            
            let wasSelected = this.extendedData.selected; 
            
            if(!(e.shiftKey)){
                //desseleciona todos os itens imitando o comportamento da tabela
                for(var i =0; i<this.geoxml.selectedArray.length; i++){
                    this.geoxml.selectedArray[i].extendedData.selected = 'false';
                    this.geoxml.ajustaIcone(this.geoxml.selectedArray[i]);
                    this.geoxml.hideDirectionLines(this.geoxml.selectedArray[i]);
                }
                this.geoxml.selectedArray=[];
            }
            
            if(wasSelected == 'true'){
                this.extendedData.selected='false';
                let i = this.geoxml.selectedArray.indexOf(this);
                if(i>-1){
                    this.geoxml.selectedArray.splice(i,1);
                }
                this.geoxml.hideDirectionLines(this);
            }else{
                this.extendedData.selected='true';
                this.geoxml.selectedArray.push(this);
                this.geoxml.showDirectionLines(this);
            }
            var that=this;
            
            //seleciona o item clicado
            this.geoxml.navigationPos=this.arrayPos;
            if(e.shiftKey){
                window.app.markerMouseClickedBF(this.extendedData.id, button, 'shift'); 
                if(e.ctrlKey){
                    if(this.extendedData.checked=='true'){
                        this.extendedData.checked='false';
                        window.app.checkMarkerBF(this.id, false);
                    }else{
                        this.extendedData.checked='true';
                        window.app.checkMarkerBF(this.id, true);
                    }
                }
            }else{
                if(e.ctrlKey){
                    if(this.extendedData.checked=='true'){
                        this.extendedData.checked='false';
                        window.app.checkMarkerBF(this.id, false);
                    }else{
                        this.extendedData.checked='true';
                        window.app.checkMarkerBF(this.id, true);
                    }
                }
                window.app.markerMouseClickedBF(this.extendedData.id, button, '');  
            }

            setTimeout(()=>{that.geoxml.ajustaIcone(that);},10);
            setTimeout(()=>{
                for(var i =0; i<this.geoxml.overlayman.markers.length; i++){
                    if(this.geoxml.overlayman.markers[i].id == that.id){
                        window.mpos = i;                        
                    }
                }
            },1);
        }catch(e){
            alert(e);
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
            let wasSelected = m.extendedData.selected;
			if(wasSelected!=selecionado){
                if(selecionado == 'true'){
                    m.extendedData.selected = 'true';
                    this.selectedArray.push(m);
                }else{
                    m.extendedData.selected = 'false';
                    let index = this.selectedArray.indexOf(m);
                    if(index>-1){
                        this.selectedArray.splice(index,1);
                    }
                }
                this.ajustaIcone(m);            
            }
		}		
	}
}

/* função para seleção programática de item no mapa */
GeoXmlIped.prototype.marca = function(mid, marcado) {
	for (i = 0; i <this.overlayman.markers.length; i++) {
		var m = this.overlayman.markers[i];
		if(m.extendedData.id == mid){
            let wasChecked = m.extendedData.checked;
			if(marcado == 'false'){
				m.extendedData.checked = 'false';
				ckbox = document.getElementById("ck_marcador_"+mid);
				if(ckbox != null){
					ckbox.checked = false;
				}
			}else{
				m.extendedData.checked = 'true';
				ckbox = document.getElementById("ck_marcador_"+mid);
				if(ckbox != null){
					ckbox.checked = true;
				}
			}
			if(wasChecked!=marcado){
                this.ajustaIcone(m);            
            }
		}		
	}
}

/* função para seleção programática de item no mapa */
GeoXmlIped.prototype.centralizaSelecao = function() {
    var m = this.selectedArray[0];
    
    if(m){
        m.geoxml.map.panTo(m.getPosition());
    }
}