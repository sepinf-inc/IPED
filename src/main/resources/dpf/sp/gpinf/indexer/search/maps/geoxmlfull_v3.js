/******************************************************************************\
*  geoxmlfullv3.js		                               by Lance Dyas          *
*  A Google Maps API Extension  GeoXml parser                                 *
*  GeoXml Parser based on my maps kml parser by Mike Williams called egeoxml  *
*  Additions include:   GML/WFS/GeoRSS/GPX expanded GE KML style support      * 
*  		http://www.dyasdesigns.com/geoxml/GeoXmlSamples.html				  *                                          
\******************************************************************************/
// Constructor
function KMLObj(title,desc,op,fid) {
	this.title = title;
  	this.description = escape(desc);
  	this.marks = [];
	this.folders = [];
	this.groundOverlays = [];
	this.open = op;
	this.folderid = fid;
	}
if (typeof console === "undefined" || typeof console.log === "undefined") {
	console = {};
	console.log = function() {};
}
function Lance$(mid){ return document.getElementById(mid);}
var topwin = self;
var G = google.maps;
 
function GeoXml(myvar, map, url, opts) {
  // store the parameters
  this.myvar = myvar;
  this.opts = opts || {};
  this.mb = new MessageBox(map,this,"mb",this.opts.messagebox);
  this.map = map;
  this.map.geoxml = this;
  this.url = url;
  if (typeof url == "string") {
    this.urls = [url];
  } else {
    this.urls = url;
	this.url= this.url[0];
  }
  
  if (opts.openbyid)
  	this.openbyid = opts.openbyid;
  if (opts.openbyname)
  	this.openbyname = opts.openbyname;
  
  this.showLabels = this.opts.showLabels || true;
  this.mb.style = this.opts.messagestyle || { backgroundColor: "silver"};
  this.alwayspop = this.opts.alwaysinfopop || false;
  this.veryquiet = this.opts.veryquiet || false;
  this.quiet = this.opts.quiet || false;
  this.suppressFolders = this.opts.suppressallfolders || false;
  this.rectangleLegend = this.opts.simplelegend || false;
  // infowindow styles
  this.titlestyle = this.opts.titlestyle || 'style = "font-family: arial, sans-serif;font-size: medium;font-weight:bold;"';
  this.descstyle = this.opts.descstyle || 'style = "font-family: arial, sans-serif;font-size: small;padding-bottom:.7em;"';
  if(this.opts.directionstyle && typeof this.opts.directionstyle != "undefined"){
	this.directionstyle = this.opts.directionstyle;
  	}
  else {
  this.directionstyle = 'style="font-family: arial, sans-serif;font-size: small;padding-left: 1px;padding-top: 1px;padding-right: 4px;"';
  }
  // sidebar
  this.sidebarfn = this.opts.sidebarfn || GeoXml.addSidebar;
  // elabel options 
  this.pointlabelopacity = this.opts.pointlabelopacity || 100;
  this.polylabelopacity = this.opts.polylabelopacity || 100;
   // other useful "global" stuff
  this.hilite  = this.opts.hilite || { color:"#aaffff",opacity: 0.3, textcolor:"#000000" };
  this.latestsidebar = "";
  this.forcefoldersopen = false;
  if(typeof this.opts.allfoldersopen !="undefined"){ this.forcefoldersopen = this.opts.allfoldersopen;}
  this.iwmethod = this.opts.iwmethod || "click";
  this.linkmethod = this.opts.linkmethod || "dblclick";
  this.linktarget = this.opts.linktarget|| "_self";
  this.contentlinkmarkers = false;
  if(typeof this.opts.contentlinkmarkers == "boolean" ){
       this.contentlinkmarkers = this.opts.contentlinkmarkers;  
       }
  this.extcontentmarkers = false;
  if(typeof this.opts.extcontentmarkers == "boolean" ){
       this.extcontentmarkers = this.opts.extcontentmarkers;  
       }
  
  this.dohilite = true;
  if(typeof this.opts.dohilite != "undefined" && this.opts.dohilite==false){
	this.dohilite = false;
	}
  this.clickablepolys = true;
  this.zoomHere = 15; 
  if(typeof this.opts.zoomhere == "number" ){
	 this.zoomHere = this.opts.zoomhere;
 	 }
  if(typeof this.opts.clickablepolys == "boolean"){
	this.clickablepolys = this.opts.clickablepolys;
  	}
  this.clickablemarkers = true;
  if(typeof this.opts.clickablemarkers == "boolean" ){
       this.clickablemarkers = this.opts.clickablemarkers;  
       }
  this.opendivmarkers = '';
  if(typeof this.opts.opendivmarkers == "string" ){
       this.opendivmarkers = this.opts.opendivmarkers;  
       }
	   
  this.opts.singleInfoWindow = true;
  if (this.opts.singleInfoWindow)
	  google.maps.event.addListener(map, 'click', function() {
		  if (this.geoxml.lastMarker.infoWindow.getMap())
		  	this.geoxml.lastMarker.infoWindow.close();
		  this.geoxml.lastmarker = null;
		  });
  
  this.clickablelines = true;
  if(typeof this.opts.clickablelines == "boolean" ){
       this.clickablelines = this.opts.clickablelines;  
       }
  if(typeof this.opts.nolegend !="undefined"){
		this.nolegend = true;
		}
  if(typeof this.opts.preloadHTML == "undefined"){
	this.opts.preloadHTML = true;
  	}

  this.sidebariconheight = 16;
  if(typeof this.opts.sidebariconheight == "number"){
	 this.sidebariconheight = this.opts.sidebariconheight;
  	}
  this.sidebarsnippet = false;
  if(typeof this.opts.sidebarsnippet == "boolean"){
	this.sidebarsnippet = this.opts.sidebarsnippet;
  	}
  this.hideall = false;
  if(this.opts.hideall){ this.hideall = this.opts.hideall; }

  if(this.opts.markerpane && typeof this.opts.markerpane != "undefined"){
	this.markerpane = this.opts.markerpane;
	}
  else {
	var div = document.createElement("div");
	div.style.border = ""; 
	div.style.position = "absolute";
	div.style.padding = "0px";
	div.style.margin = "0px";
	div.style.fontSize = "0px";
	div.zIndex = 1001;
	//map.getPane(G_MAP_MARKER_PANE).appendChild(div);
	this.markerpane = div;
	this.markerpaneOnMap = false;
	}

// Mike: Not clear why this is set conflicts with the elabel library
//  var c = map.getDiv();
//  c.style.fontSize = "0px";
  
  if(typeof proxy!="undefined"){ this.proxy = proxy; }
  if (this.opts.token&&this.opts.id) { this.token = '&'+this.opts.token+'=1&id='+this.opts.id;} else {this.token='';}

  if(!this.proxy && typeof getcapproxy !="undefined") { 
	  	if(fixUrlEnd){ getcapproxy = fixUrlEnd(getcapproxy);  } 
 		}
  this.publishdirectory = "http://www.dyasdesigns.com/tntmap/";
  topwin = top;
  try {topname=top.title;}
  	catch(err){topwin=self;}
  if(topwin.publishdirectory){this.publishdirectory = topwin.publishdirectory; }
  if(topwin.standalone){this.publishdirectory = "";}
  this.kmlicon =  this.publishdirectory +"images/ge.png";
  this.docicon = this.publishdirectory +"images/ge.png";
  this.docclosedicon = this.publishdirectory +"images/geclosed.png";
  this.foldericon = this.publishdirectory + "images/folder.png";
  this.folderclosedicon = this.publishdirectory + "images/folderclosed.png";
  this.gmlicon = this.publishdirectory + "images/geo.gif";
  this.rssicon = this.publishdirectory + "images/rssb.png";
  this.globalicon = this.publishdirectory + "images/geo.gif"; 
  this.WMSICON = "<img src=\""+this.publishdirectory+"images/geo.gif\" style=\"border:none\" />";
  GeoXml.WMSICON = this.WMSICON;
  this.baseLayers = [];
  this.bounds = new google.maps.LatLngBounds();
  this.style = {width:2,opacity:0.75,fillopacity:0.4};
  this.style.color = this.randomColor();
  this.style.fillcolor = this.randomColor();
  this.iwwidth = this.opts.iwwidth || 400;
  this.maxiwwidth = this.opts.maxiwwidth || 0;
  this.iwheight = this.opts.iwheight || 0;
  this.lastMarker = {};   
  this.verySmall = 0.0000001;
  this.progress = 0;
  this.ZoomFactor = 2;
  this.NumLevels = 18;
  this.maxtitlewidth = 0; 
  this.styles = []; 
  this.currdeschead = "";
  this.jsdocs = [];
  this.jsonmarks = [];
  this.polyset = []; /* used while rendering */
  this.polygons = []; /*stores indexes to multi-polygons */ 
  this.polylines = []; /*stores indexes to multi-line */ 
  this.multibounds = []; /*stores extents of multi elements */
  if (typeof this.opts.clustering == "undefined") {
	this.opts.clustering = {};
	}
  if (typeof this.opts.clustering.lang == "undefined") {
    this.opts.clustering.lang = {txtzoomin:"Zoom In",txtclustercount1:"...and",txtclustercount2:"more" };
    }
	  
  this.overlayman = new OverlayManager(map, this, this.opts.clustering);
  this.overlayman.rowHeight = 20;
  if(this.opts.sidebarid){ this.basesidebar = this.opts.sidebarid; }
  this.kml = [new KMLObj("GeoXML","",true,0)];
  this.overlayman.folders.push([]);
  this.overlayman.subfolders.push([]);
  this.overlayman.folderhtml.push([]);
  this.overlayman.folderhtmlast.push(0);
  this.overlayman.folderBounds.push(new google.maps.LatLngBounds()); 
  this.wmscount = 0;
  
  // patch for tooltips which dont seem to want to close 
	google.maps.event.addListener(this.map, 'mousemove', function() { window.setTimeout(function(e) { GeoXml.tooltip.hide();}, 1);}); 
			
  this.unnamedpath="un-named path";
  this.unnamedplace="un-named place";
  this.unnamedarea="un-named area";
  
  // input type size
  if (typeof this.opts.inputsize == "undefined")
  	  this.inputsize = 35;
  else
  	  this.inputsize = this.opts.inputsize;

  // Language parameters
  if (typeof this.opts.lang == "undefined") {
	  this.lang = {};
	  this.lang.txtdir = "Get Directions:";
	  this.lang.txtto = "To Here";
	  this.lang.txtfrom = "From Here";
	  this.lang.txtsrchnrby = "Search nearby";
	  this.lang.txtzoomhere = "Zoom Here";
	  this.lang.txtaddrstart = "Start address:";
	  this.lang.txtgetdir = "Go";
	  this.lang.txtback = "&#171; Back";
	  this.lang.txtsearchnearby = "Search nearby: e.g. \"pizza\"";
	  this.lang.txtsearch = "Go";
  } else {
	  this.lang = this.opts.lang;
  }
}

GeoXml.prototype.setOpacity = function(opacity){
	this.opts.overrideOpacity = opacity;
	//alert("now using opacity "+opacity);
	for(var m=0;m<this.overlayman.markers.length;m++){
		var marker = this.overlayman.markers[m];
		if (marker.getPaths){ //polygon set fill opacity
		//	alert(marker.fillColor);
			this.overlayman.markers[m].fillOpacity = opacity;
			this.overlayman.markers[m].setOptions({fillOpacity:opacity});
			}
		else {
			if(marker.getPath){
				//alert(marker.strokeColor+" "+marker.strokeWeight)
				this.overlayman.markers[m].strokeOpacity = opacity;
				this.overlayman.markers[m].setOptions({strokeOpacity:opacity});
				}
			}
		
		}
	};
	
GeoXml.stripHTML = function(s){
	return (s.replace(/(<([^>]+)>)/ig,""));
	};

GeoXml.prototype.showIt = function (str, h, w) {
	var features = "status=yes,resizable=yes,toolbar=0,height=" + h + ",width=" + h + ",scrollbars=yes";
	var myWin;
	if (topwin.widget) {
		alert(str);
		this.mb.showMess(str);
		}
	else {
		myWin = window.open("", "_blank", features);
		myWin.document.open("text/xml");
		myWin.document.write(str);
		myWin.document.close();
		}
	};
	
GeoXml.prototype.clear = function () {
	for(var m=0;m<this.overlayman.markers.length;m++){
		this.overlayman.RemoveMarker(this.overlayman.markers[m]);
		}
	this.kml = [new KMLObj("GeoXML","",true,0)];
 	this.maxtitlewidth = 0;
  	this.styles = []; 
	// associative array
  	this.jsdocs = [];
  	this.jsonmarks = [];
  	this.polyset = []; 
	/* used while rendering */
  	this.polylines = [];
  	this.multibounds = []; 
	this.bounds = new google.maps.LatLngBounds();
  	this.overlayman = new OverlayManager(this.map, this,this.opts.clustering);
  	this.overlayman.rowHeight = 20;
	if(typeof this.basesidebar !="undefined" && this.basesidebar !=""){
		Lance$(this.basesidebar).innerHTML = "";
		}
	this.currdeschead = "";
  	this.overlayman.folders.push([]);
  	this.overlayman.subfolders.push([]);
  	this.overlayman.folderhtml.push([]);
  	this.overlayman.folderhtmlast.push(0);
	this.overlayman.byname = [];
    this.overlayman.byid = [];
	this.filteredNames = [];
	this.folderCBNames = [];
  	this.overlayman.folderBounds.push(new google.maps.LatLngBounds()); 
 	this.wmscount = 0;
	};

 
// Create Marker
GeoXml.prototype.createMarkerJSON = function(item,idx) {
	var that = this;
	
	var style = that.makeIcon(style, item.href);
 	var point = new google.maps.LatLng(item.y,item.x);
	that.overlayman.folderBounds[idx].extend(point);
	that.bounds.extend(point);
	
	if(item.shadow){ style.shadow = item.shadow; }
		else{ style.shadow = null; }
	if (!!that.opts.createmarker) {
          	that.opts.createmarker(point, item.title, unescape(item.description), null, idx, style, item.visibility, item.id, item.href, item.snip);
        	} 
	else {
          	that.createMarker(point, item.title, unescape(item.description), null, idx, style, item.visibility, item.id, item.href, item.snip);
        	}
	};

GeoXml.prototype.createMarker = function(point, name, desc, styleid, idx, instyle, visible, kml_id, markerurl,snip) {
	   	var myvar = this.myvar;
	    var icon;
		var shadow;
	    var href;
		var scale = 1;
		if(instyle && instyle.scale){
			scale = instyle.scale;
			}
		var bicon;
		if ((instyle && (typeof instyle.url == "undefined" || instyle.url == "")) && this.showLabels){
			if ((scale*12)< 7){
				scale = 0.6;
				}
			var fs = (12*scale)+"px";
			var style = { fontSize: fs, fontFamily: "Verdana, Arial, Sans-serif" };
			if (instyle && instyle.textColor){
				style.color = instyle.textColor;
				}
			//console.log("labelColor="+instyle.textColor);
            var m = new GeoXml.Label(point,name,"",this.map, style);
			m.title = name;
			m.id = kml_id;
			var obj = { "type": "point", "title": name, "description": name, "href": href, "shadow": shadow, "visibility": visible, "x": point.x, "y": point.y, "id": m.id };
			this.kml[idx].marks.push(obj);
			var parm;
			var blob = '<svg xmlns="http://www.w3.org/2000/svg" style="margin-left:0px;margin-top:0px" version="1.2" width="16px" height="16px">';
			blob += '<text stroke="#bfb3b3" transform="matrix(0.757796 0 0 0.757796 0.200575 4.77596)" xml:space="preserve" text-anchor="middle" font-family="serif" font-size="24" id="svg_15" y="12.958634" x="7.370384" stroke-linecap="null" stroke-linejoin="null" stroke-dasharray="null" stroke-width="0" fill="'+ style.color +'">A</text>';
			blob += '<text stroke="#bfb3b3" transform="matrix(0.781022 0 0 0.781022 2.09056 3.86291)" xml:space="preserve" text-anchor="middle" font-family="serif" font-size="24" id="svg_16" y="8.571944" x="13.233881" stroke-linecap="null" stroke-linejoin="null" stroke-dasharray="null" stroke-width="0" fill="'+style.color+'">a</text>';
			blob += '</svg>';
			var desc2="";
			if(this.tileset){
				parm = this.tileset + "$$$" + name + "$$$marker$$$" + n + "$$$" + blob + "$$$" + visible + "$$$null$$$" + desc2;
				m.sidebarid = this.tileset + "sb" + n;
				}
			else {
				parm = this.myvar + "$$$" + name + "$$$marker$$$" + n + "$$$" + blob + "$$$" + visible + "$$$null$$$" + desc2;
				m.sidebarid = this.myvar + "sb" + n;
				}
			if (!!this.opts.addmarker) {
				this.opts.addmarker(m, name, idx, parm, visible);
			} else {
				this.overlayman.addMarker(m, name, idx, parm, visible);
				}
			return;
			}
			
		if(instyle){
			bicon = instyle;
			}
		else {
			var bicon = new google.maps.MarkerImage("http://maps.google.com/mapfiles/kml/pal3/icon40.png",
				new google.maps.Size(32*scale, 32*scale), //size
				new google.maps.Point(0, 0), //origin
				new google.maps.Point(16*scale, 16*scale), //anchor
				new google.maps.Size(32*scale, 32*scale) //scaledSize 
				);
			}

	    if (this.opts.baseicon) {
			bicon.size = this.opts.baseicon.size;
			bicon.origin = this.opts.baseicon.origin;
			bicon.anchor = this.opts.baseicon.anchor;
			if (scale){
				if(instyle){
					bicon.scaledSize = instyle.scaledSize;
					}
				}
			else {
				bicon.scaledSize = this.opts.baseicon.scaledSize;
				}
			scale = 1;
			}
		icon = bicon;	
	    if (this.opts.iconFromDescription) {
	        var text = desc;
	        var pattern = new RegExp("<\\s*img", "ig");
	        var result;
	        var pattern2 = /src\s*=\s*[\'\"]/;
	        var pattern3 = /[\'\"]/;
	        while ((result = pattern.exec(text)) != null) {
	            var stuff = text.substr(result.index);
	            var result2 = pattern2.exec(stuff);
	            if (result2 != null) {
	                stuff = stuff.substr(result2.index + result2[0].length);
	                var result3 = pattern3.exec(stuff);
	                if (result3 != null) {
	                    var imageUrl = stuff.substr(0, result3.index);
	                    href = imageUrl;
	                }
	            }
	        }
	        shadow = null;
	        if (!href) {
	            href = "http://maps.google.com/mapfiles/kml/pal3/icon40.png";
				}
	        icon = bicon;//new google.maps.MarkerImage(bicon);
			bicon.size = null;
			bicon.scaledSize = new google.maps.Size(32*scale, 32*scale); //scaledSize 
			icon.url = href;  
			}
	    else {
	        href = "http://maps.google.com/mapfiles/kml/pal3/icon40";
	        if (instyle == null || typeof instyle == "undefined") {
	            shadow = href + "s.png";
	            href += ".png";
	            if (this.opts.baseicon) {
	                href = this.opts.baseicon.url;
	               // shadow = this.opts.baseicon.shadow;
					}
				}
	        else {
	            if (instyle.url) { href = instyle.url; }
	           // if (instyle.shadow) { shadow = instyle.shadow; }
				}
	        icon = bicon; //new google.maps.MarkerImage(bicon);
			icon.url = href; //, href, null, shadow);
			}
	    var iwoptions = this.opts.iwoptions || {};
	    var markeroptions = this.opts.markeroptions || {};
	    var icontype = this.opts.icontype || "style";
		
	    if (icontype == "style") {
			var blark = this.styles[styleid];
	        if (!!blark) {
				icon = bicon;//new GIcon(bicon, this.styles[style].href, null, this.styles[style].shadow);
				icon.url = blark.url;
				icon.anchor = blark.anchor;
	            href = blark.url;
				}
			}
	    markeroptions.icon = icon;

		if (this.contentlinkmarkers){
			var text = desc;
			var pattern = new RegExp ("<\\s*a", "ig");
			var result;
			var pattern2 = /href\s*=\s*[\'\"]/;
			var pattern3 = /[\'\"]/;
			while ((result = pattern.exec(text))!= null) {
				var stuff = text.substr(result.index);
				var result2 = pattern2.exec(stuff);
					if (result2!= null) {
						stuff = stuff.substr(result2.index+result2[0].length);
						var result3 = pattern3.exec(stuff);
						if (result3!= null) {
							var urlLink = stuff.substr(0,result3.index);
							}
						}
					}
				}
		if (this.extcontentmarkers){
			var contentUrl = Array();
			var text = desc;
			var pattern = new RegExp ("<\\s*object", "ig");
			var result;
			var pattern1 = /<\/\s*object>/i;
			var pattern2 = /data\s*=\s*[\'\"]/;
			var pattern3 = /[\'\"]/;
			var x = 0;
			while ((result = pattern.exec(text))!= null) {
				var stuff = text.substr(result.index);
				var result1 = pattern1.exec(stuff);
				var result2 = pattern2.exec(stuff);
					if (result2!= null) {
						var stuff2 = stuff.substr(result2.index+result2[0].length);
						var result3 = pattern3.exec(stuff2);
						if (result3!= null) {
							var urlLink = stuff2.substr(0,result3.index);
							urlLink = urlLink.replace("http://","");
							contentUrl[x] = urlLink;
							text = text.substr(0, result.index)+"<span id='geoxmlobjcont"+x+"'></span>"+stuff.substr(result1.index+result1[0].length, stuff.length);
							x++;
							}
						}
					}
				desc = text;
		}	 
  		//markeroptions.image = icon.image;
		//markeroptions.image = icon.image;
		var start = icon.url.substring(0,4); //handle relative urls
		if(start.match(/^http/i)||start.substr(0,1)=='/') {
			}
		else {
			if(typeof this.url == "string"){
				var slash = this.url.lastIndexOf("/");
				var changed = false;
				var subchanged = false;
				var newurl;
				if(slash != -1){
					newurl = this.url.substring(0,slash);
					changed = true;
					slash = 0;
					}
				
				while(slash != -1 && icon.url.match(/^..\//)){
					slash = newurl.lastIndexOf("/");
					icon.url = icon.url.substring(3);
					if (slash != -1){
						newurl = newurl.substring(0,slash);
						}
					changed = true;
					}
					
				if(newurl != "" && icon.url.match(/^..\//)){
					newurl = "";
					icon.url = icon.url.substring(3);
					}
			 
				if(newurl ==""){ markeroptions.icon.url = icon.url; }
				else { markeroptions.icon.url = newurl+"/"+ icon.url; }
				}
			}
		
		var ta=document.createElement("textarea");
		ta.innerHTML=name;
		name = ta.value;
	    markeroptions.title = name;

		markeroptions.clickable = true;
		markeroptions.pane = this.markerpane;
		markeroptions.position = point;
		
		var m = new google.maps.Marker(markeroptions);
	    m.id = kml_id;
		m.urlLink=urlLink;
		m.geoxml = this;
	    var obj = { "type": "point", "title": name, "description": escape(desc), "href": href, "shadow": shadow, "visibility": visible, "x": point.x, "y": point.y, "id": m.id };
	    this.kml[idx].marks.push(obj);

	    if (this.opts.pointlabelclass) {
	        var l = new ELabel(point, name, this.opts.pointlabelclass, this.opts.pointlabeloffset, this.pointlabelopacity, true);
	        m.label = l;
	        l.setMap(this.map); 
			}
	    var html, html1, html2, html3, html4;
//	    var awidth = this.iwwidth;
//	    if (desc.length * 8 < awidth) {
//	        awidth = desc.length * 8;
//	    }
//	    if (awidth < name.length * 10) {
//	        awidth = name.length * 10;
//	    }
//	    if(this.maxiwwidth && awidth > this.maxiwwidth ){
//			awidth = this.maxiwwidth;
//	    	}
	    html = "<h1 " + this.titlestyle + ">" + name + "</h1>";
		if(name != desc){
			html +=  "<div " + this.descstyle + ">" + desc + "</div>";
			}
	    var html1;
	    if (this.opts.directions) {
	        html1 = html + '<div ' + this.directionstyle + '>'
                     + this.lang.txtdir+' <a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click2\');return false;">'+this.lang.txtto+'</a> - '
                     + '<a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click3\');return false;">'+this.lang.txtfrom+'</a><br>'
                     + '<a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click4\');return false;">'+this.lang.txtsrchnrby+'</a> | <a href="#" onclick="' + this.myvar + '.map.setCenter(new google.maps.LatLng(' + point.lat() + ',' + point.lng() + '));'+ this.myvar +'.map.setZoom(' + this.zoomHere + ');return false;">'+this.lang.txtzoomhere+'</a></div>';
	        html2 = html + '<div ' + this.directionstyle + '>'
                     + this.lang.txtdir+' '+this.lang.txtto+' - '
                     + '<a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click3\');return false;">'+this.lang.txtfrom+'</a><br>'
                     + this.lang.txtaddrstart+'<form action="http://maps.google.com/maps" method="get" target="_blank">'
                     + '<input type="text" SIZE='+this.inputsize+' MAXLENGTH=80 name="saddr" id="saddr" value="" />'
                     + '<INPUT value="'+this.lang.txtgetdir+'" TYPE="SUBMIT">'
                     + '<input type="hidden" name="daddr" value="' + point.lat() + ',' + point.lng() + "(" + name + ")" + '"/>'
                     + '<br><a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click1\');return false;">'+this.lang.txtback+'</a> | <a href="#" onclick="' + this.myvar + '.map.setCenter(new google.maps.LatLng(' + point.lat() + ',' + point.lng() + '));'+ this.myvar +'.map.setZoom(' + this.zoomHere + ');return false;">'+this.lang.txtzoomhere+'</a></div>';
	        html3 = html + '<div ' + this.directionstyle + '>'
                     + this.lang.txtdir+' <a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click2\');return false;">'+this.lang.txtto+'</a> - '
                     + this.lang.txtfrom+'<br>'
                     + this.lang.txtaddrstart+'<form action="http://maps.google.com/maps" method="get" target="_blank">'
                     + '<input type="text" SIZE='+this.inputsize+' MAXLENGTH=80 name="daddr" id="daddr" value="" />'
                     + '<INPUT value="'+this.lang.txtgetdir+'" TYPE="SUBMIT">'
                     + '<input type="hidden" name="saddr" value="' + point.lat() + ',' + point.lng() + "(" + name + ")" + '"/>'
                     + '<br><a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click1\');return false;">'+this.lang.txtback+'</a> | <a href="#" onclick="' + this.myvar + '.map.setCenter(new google.maps.LatLng(' + point.lat() + ',' + point.lng() + '));'+ this.myvar +'.map.setZoom(' + this.zoomHere + ');return false;">'+this.lang.txtzoomhere+'</a></div>';
	        html4 = html + '<div ' + this.directionstyle + '>'
                     + this.lang.txtsearchnearby+'<br>'
                     + '<form action="http://maps.google.com/maps" method="get" target="_blank">'
                     + '<input type="text" SIZE='+this.inputsize+' MAXLENGTH=80 name="q" id="q" value="" />'
                     + '<INPUT value="'+this.lang.txtsearch+'" TYPE="SUBMIT">'
                     + '<input type="hidden" name="near" value="' + name + ' @' + point.lat() + ',' + point.lng() + '"/>'
         	     + '<br><a href="#" onclick="google.maps.event.trigger(' + this.myvar + '.lastMarker,\'click1\');return false;">'+this.lang.txtback+'</a> | <a href="#" onclick="' + this.myvar + '.map.setCenter(new google.maps.LatLng(' + point.lat() + ',' + point.lng() + '));'+ this.myvar +'.map.setZoom(' + this.zoomHere + ');return false;">'+this.lang.txtzoomhere+'</a></div>';
	        
			google.maps.event.addListener(m, "click1", function() {
				var infoWindowOptions = { 
					content: html1, 
					pixelOffset: new google.maps.Size(0, 2)
				};
				if(this.geoxml.maxiwwidth){
					infoWindowOptions.maxWidth = this.geoxml.maxiwwidth;
					}
				m.infoWindow.setOptions(infoWindowOptions);
	        });
			google.maps.event.addListener(m, "click2", function() {
				var infoWindowOptions = { 
					content: html2, 
					pixelOffset: new google.maps.Size(0, 2)
				};
				if(this.geoxml.maxiwwidth){
					infoWindowOptions.maxWidth = this.geoxml.maxiwwidth;
					}
				m.infoWindow.setOptions(infoWindowOptions);
	        });
	        google.maps.event.addListener(m, "click3", function() {
	           	var infoWindowOptions = { 
					content: html3,
					pixelOffset: new google.maps.Size(0, 2)
				};
				if(this.geoxml.maxiwwidth){
					infoWindowOptions.maxWidth = this.geoxml.maxiwwidth;
					}
				m.infoWindow.setOptions(infoWindowOptions);
	        });
	        google.maps.event.addListener(m, "click4", function() {
			   	var infoWindowOptions = { 
					content: html4,
					pixelOffset: new google.maps.Size(0, 2)
				};
				if(this.geoxml.maxiwwidth){
					infoWindowOptions.maxWidth = this.geoxml.maxiwwidth;
					}
				m.infoWindow.setOptions(infoWindowOptions);
	        });
	    } else {
	        html1 = html;
	    }
  	if(this.opts.markerfollowlinks){
		if(markerurl && typeof markerurl=="string"){
			if(markerurl!=''){
				m.url = markerurl;
	    	  		google.maps.event.addListener(m, this.linkmethod, function() {
					if (m.geoxml.linktarget=="_blank")
						window.open(m.url);										
					if (m.geoxml.linktarget=="_self")
						document.location=m.url;
					try {
					eval(myvar + ".lastMarker = m");
					}
					catch(err){
					}
	        	});
		   }
	    	}
	    }
	    else {
	    if (this.clickablemarkers) {
			m.geoxml = this;
			var infoWindowOptions = { 
				content: html1+"</div>",
				pixelOffset: new google.maps.Size(0, 2)
				};
			if(m.geoxml.maxiwwidth){
					infoWindowOptions.maxWidth = m.geoxml.maxiwwidth;
					}
			m.infoWindow = new google.maps.InfoWindow(infoWindowOptions);
			var parserOptions = this.opts;
			
    // Infowindow-opening event handler
		google.maps.event.addListener(m, this.iwmethod, function() {
				if (m!=m.geoxml.lastMarker) {
					if (!!m.geoxml.opts.singleInfoWindow) {
						if (!!m.geoxml.lastMarker && !!m.geoxml.lastMarker.infoWindow) {
							if (!m.geoxml.lastMarker.onMap)
								m.geoxml.lastMarker.setMap(m.geoxml.map);
							m.geoxml.lastMarker.infoWindow.close();
							if (!m.geoxml.lastMarker.onMap)
								m.geoxml.lastMarker.setMap(null);
						}
						OverlayManager.PopDown( m.geoxml.overlayman );
						m.geoxml.lastMarker = m;
					}
					if (!m.onMap)
						m.geoxml.map.panTo(m.getPosition());
				}
				
				itemClick(m.getTitle(), m.getPosition().lat(), m.getPosition().lng());
				
				if (!m.infoWindow.getMap())
					m.infoWindow.open(m.geoxml.map, this);
			});
	
			}
	    }
		if(this.extcontentmarkers){
			m.geoxml = this;
			var infoWindowOptions = { 
				content: html1+"</div>",
				pixelOffset: new google.maps.Size(0, 2)
				};
			if(m.geoxml.maxiwwidth){
					infoWindowOptions.maxWidth = m.geoxml.maxiwwidth;
					}
			m.infoWindow = new google.maps.InfoWindow(infoWindowOptions);
			var parserOptions = this.opts;
			m.contentUrl = contentUrl;

			google.maps.event.addListener(m, this.iwmethod, function() {
				m.geoxml.lastMarker = m;
				if (m.contentUrl.length>0) {
					m.infoWindow.open(m.geoxml.map, this);
					for (var x=0;x<m.contentUrl.length;x++) {
						var url = m.geoxml.proxy+'url='+m.contentUrl[x];
						setTimeout(m.geoxml.myvar+".getextContent('"+m.contentUrl[x]+"', "+x+")", 100+x*50);
					}
			  } else
					m.infoWindow.open(m.geoxml.map, this);
			});
		}
		if (this.contentlinkmarkers){
			google.maps.event.addListener(m, this.linkmethod, function() {
					if (m.geoxml.linktarget=="_blank")
						window.open(m.urlLink);										
					if (m.geoxml.linktarget=="_self")
						document.location=m.urlLink;
			});
		}
		
		if(this.opendivmarkers!=''){
			m.div = this.opendivmarkers;
			google.maps.event.addListener(m, this.iwmethod, function() {
				if (m!=m.geoxml.lastMarker) {
					if (!!m.geoxml.lastMarker&&!!m.geoxml.lastMarker.setMap) {
						if (!m.geoxml.lastMarker.onMap)
							m.geoxml.lastMarker.setMap(null);
					}
					OverlayManager.PopDown( m.geoxml.overlayman );
					m.geoxml.lastMarker = m;
					m.geoxml.map.panTo(m.getPosition());
				}
				var obj = document.getElementById(m.div);
				if (obj)
				  obj.innerHTML = html1+ "</div>";
			});
		}
		
	    if (this.opts.domouseover) {
	        m.mess = html1 + "</div>";
	        m.geoxml = this;
	        google.maps.event.addListener(m, "mouseover", function(point) { if (!point) { point = m.getPosition(); } m.geoxml.mb.showMess(m.mess, 5000); });
			}
	    var nhtml = "";
	    var parm;
	    if (this.opts.sidebarid) {
	        var folderid = this.myvar + "_folder" + idx;
	        var n = this.overlayman.markers.length;
	        var blob = "&nbsp;<img style=\"vertical-align:text-top;padding:0;margin:0;height:"+this.sidebariconheight+"px;\"  border=\"0\" src=\"" + href + "\">&nbsp;";
			if(this.sidebarsnippet){
			var desc2 = GeoXml.stripHTML(desc);
			desc2 = desc2.substring(0,40);}
			else {desc2 = '';	}
	        parm = this.myvar + "$$$" + name + "$$$marker$$$" + n + "$$$" + blob + "$$$" + visible + "$$$null$$$" + desc2;
	        m.sidebarid = this.myvar + "sb" + n;
	        m.hilite = this.hilite;
	        m.geoxml = this;
			
			m.onOver = function() {
					if(this.geoxml.dohilite){
						var bar = Lance$(this.sidebarid);
						if (bar && typeof bar != "undefined") { 
							bar.style.backgroundColor = this.hilite.color;

							bar.style.color = this.hilite.textcolor;
							}
						}
					};
			m.onOut = function() {
				if(this.geoxml.dohilite){
					var bar = Lance$(this.sidebarid);
					if (bar && typeof bar != "undefined") {
						bar.style.background = "none";
						bar.style.color = "";
						}
					}
				};
	        google.maps.event.addListener(m, "mouseover", m.onOver);
	        google.maps.event.addListener(m, "mouseout", m.onOut);
			}
	    if (!!this.opts.addmarker) {
	        this.opts.addmarker(m, name, idx, parm, visible);
	    } else {
	        this.overlayman.addMarker(m, name, idx, parm, visible);
	    }
	};

// Get external contents
GeoXml.prototype.getextContent = function(url, x){
	var that = this;
    that.DownloadURL(url, function(doc) {
		if (doc) {
			obj = document.getElementById('geoxmlobjcont'+x);	
			if (obj)
				obj.innerHTML = doc;
		}
    }, 'geoxmlobjcont'+x+' '+url, false);
};

// Create Polyline

GeoXml.getDescription = function(node){
   var sub=""; 
   var n = 0;
   var cn; 
	if(typeof XMLSerializer != "undefined") {
	var serializer = new XMLSerializer();
	for(;n<node.childNodes.length;n++){
	 	cn = serializer.serializeToString(node.childNodes.item(n));
	     	sub += cn; 
		}
	}
	else {
		for(;n<node.childNodes.length;n++){
			cn = node.childNodes.item(n);
				sub += cn.xml; 
			}
		}
    var s = sub.replace("<![CDATA[","");
    var u = s.replace("]]>","");
    u = u.replace(/\&amp;/g,"&");
    u = u.replace(/\&lt;/g,"<"); 
    u = u.replace(/\&quot;/g,'"');
     u = u.replace(/\&apos;/g,"'");
    u = u.replace(/\&gt;/g,">");
    return u;
    };

GeoXml.prototype.processLine = function (pnum, lnum, idx, multi){
	var that = this;
	var op = that.polylines[pnum];
	//alert(op.lines +" "+ that.polylines[pnum].lineidx.length);]
	var isnew = true;
	if (pnum > 0){
		var last = that.polylines[pnum-1];
		if (op.name == last.name){
			isnew = false;
			that.polylines[pnum-1] = null;
			pnum = pnum - 1;
			that.polylines[pnum] = last;
			}
		}
	
	var line = op.lines[lnum];
//	alert(pnum +" "+lnum+" "+line);
	var obj;
	var p;
	if(!line){ return; }
    var thismap = this.map;
	var iwoptions = this.opts.iwoptions || {};
 	obj = { points:line, color:op.color, weight:op.width, opacity:op.opacity, type:"line", id: op.id };
	var pline = line;
	if (line.length == 1) {
		pline = line[0];
		}
	p = new google.maps.Polyline({map:this.map,path:pline,strokeColor:op.color,strokeWeight:op.width,strokeOpacity:op.opacity});
	p.bounds = op.pbounds;
	p.id = op.id;
	if (isnew == false) {
		if(this.opts.sidebarid) { p.sidebar = this.latestsidebar; }
		}
	var nhtml = "";
	var n = this.overlayman.markers.length;
	var parm;
	 var awidth = this.iwwidth;
	 var desc = op.description;
	 if(desc.length * 8 <  awidth){
		awidth = desc.length * 8;
 		}
	 if(awidth < op.name.length * 12){
		awidth = op.name.length * 12;
 		}
	var html = "<div style='font-weight: bold; font-size: medium; margin-bottom: 0em;'>"+op.name;
  	html += "</div>"+"<div style='font-family: Arial, sans-serif;font-size: small;width:"+awidth+"px;'>"+desc+"</div>";

	if(lnum == 0){
	 	if(this.opts.sidebarid && isnew) {
    		var s_w = op.width;
			if (s_w <= 2) {s_w = 2; } 
			if (s_w >16) {s_w = 16; };
			var blob;
			if (this.rectangleLegend){
				var m_w = parseInt(((16 - s_w)/2));
				blob = '<svg xmlns="http://www.w3.org/2000/svg" style="margin-left:0px;margin-top:0px" version="1.2" fill="#ffeecc" width="16px" height="16px">';
				if (op.color=='#ffffff' || op.color=='#FFFFFF'){
					blob += ' <rect stroke="none" height="16" width="16" y="0" x="0" stroke-width="null" fill="#cbcbcb"/>';
					}
				blob += ' <rect stroke="none" height="16" width="'+s_w+'" y="0" x="'+m_w+'" stroke-width="null" fill="'+op.color+'"/></svg>';
				}
			else {
				blob = '<svg xmlns="http://www.w3.org/2000/svg" style="margin-left:0px;margin-top:0px" version="1.2" fill="#ffeecc" width="16px" height="16px">';
				if (op.color=='#ffffff' || op.color=='#FFFFFF'){
					blob += ' <rect stroke="none" height="16" width="16" y="0" x="0" stroke-width="null" fill="#cbcbcb"/>';
					}
				blob += '<path stroke="'+op.color+'" d="m1.514515,10.908736c-0.457545,0.489079 0.473927,-7.091639 5.261762,-7.336174c4.787838,-0.244535 -0.457535,7.825252 4.804223,2.445392c5.261755,-5.37986 1.949991,11.609838 2.287748,3.179009"  stroke-width="2" fill="none"/></svg>';
				}
				
			if(this.sidebarsnippet){
				var desc2 = GeoXml.stripHTML(desc);
				desc2 = desc2.substring(0,20);
				}
			else {desc2 = '';}
	      
			parm =  this.myvar+"$$$" +op.name + "$$$polyline$$$" + n +"$$$" + blob + "$$$" + op.visibility + "$$$" + pnum + "$$$" + desc2;
			this.latestsidebar = this.myvar +"sb"+n;
 			}
		}

//	alert(op.lines.length);
	if((lnum+1) < op.lines.length){
		setTimeout(this.myvar+".processLine("+pnum+","+(lnum+1)+",'"+idx+"',"+multi+");",15);
		}
		
	if(this.opts.sidebarid) { p.sidebar = this.latestsidebar; }	
	if(this.opts.domouseover){
		p.mess = html;
		}
  	p.title = op.name;
    p.geoxml = this;
    p.strokeColor = op.color;
    p.strokeWeight = op.width;
	p.strokeOpacity = op.opacity;
	p.hilite = this.hilite;
	p.mytitle = p.title;
	p.map = this.map;
	p.idx = pnum;
	var position = p.getPosition();
	if(this.clickablelines){
		var infoWindowOptions = { 
				content: html,
				pixelOffset: new google.maps.Size(0, 2),
				position: position
				};
		if(this.maxiwwidth){
					infoWindowOptions.maxWidth = this.maxiwwidth;
					}
		p.infoWindow = new google.maps.InfoWindow(infoWindowOptions);
		}

	
  	p.onOver = function(e){
		var pline = this.geoxml.polylines[this.idx];
		if(this.geoxml.dohilite){
			if(this.hidden!=true){
				for(var l=0;l<pline.lineidx.length;l++){
					var mark = this.geoxml.overlayman.markers[pline.lineidx[l]];
					mark.realColor = mark.strokeColor; 
					mark.realOpacity = mark.strokeOpacity;
					mark.setOptions({
						strokeColor:this.geoxml.hilite.color,
						strokeOpacity:this.geoxml.hilite.opacity
						});
					}
				}
			//console.log(this.sidebar);
			if(this.sidebar){
				Lance$(this.sidebar).style.backgroundColor = this.hilite.color;
				Lance$(this.sidebar).style.color = this.hilite.textcolor;
				}
			}
		if(this.title){
			GeoXml.tooltip.show(this.title,e);
			} 
		if(this.mess) { this.geoxml.mb.showMess(this.mess,5000); } else { this.title = "Click for more information about "+this.mytitle; }
		};
  	p.onOut = function(){ 
		if(this.geoxml.dohilite){
			var pline = this.geoxml.polylines[this.idx];
			if(this.hidden!=true){
			//alert(pline.lineidx);
				for(var l=0; l < pline.lineidx.length; l++){
					var mark = this.geoxml.overlayman.markers[pline.lineidx[l]];
					mark.setOptions({
						strokeColor:p.realColor,
						strokeOpacity:p.realOpacity
						});
					//mark.redraw(true);
					}
				}
			
			if(this.sidebar){
				Lance$(this.sidebar).style.background = "none";
				Lance$(this.sidebar).style.color = "";
				}
			}
		GeoXml.tooltip.hide();
		this.geoxml.mb.hideMess();
		};

 	google.maps.event.addListener(p,"mouseout",p.onOut);
 	google.maps.event.addListener(p,"mouseover",p.onOver);

  	google.maps.event.addListener(p,"click", 
		function(point) {
			if(p.geoxml.clickablelines||doit){ 
				if (p!=p.geoxml.lastMarker) {
					if (!!p.geoxml.opts.singleInfoWindow) {
						if (!!p.geoxml.lastMarker && !!p.geoxml.lastMarker.infoWindow) {
							if (!p.geoxml.lastMarker.onMap)
								p.geoxml.lastMarker.setMap(p.geoxml.map);
							p.geoxml.lastMarker.infoWindow.close();
							if (!p.geoxml.lastMarker.onMap)
								p.geoxml.lastMarker.setMap(null);
							}
					p.geoxml.lastMarker = p;
					}
				}
				
				var dest;
				var doit = false;
				if(!point) { 
					doit = true; //sidebar click
					dest = p.infoWindow.position;
					var pline = this.geoxml.polylines[this.idx];
					p.geoxml.map.fitBounds(pline.bounds);
				} else {
					dest = point.latLng;
					p.infoWindow.setPosition(dest);
					p.infoWindow.open(p.geoxml.map); 
					}
				}
			} 
		);
	obj.name = op.name;
    obj.description = escape(op.description);
	if(this.hideall) { 
		op.visibility = false;
		}
	obj.visibility = op.visibility;
	this.kml[idx].marks.push(obj); 
		
	var ne = p.getBounds().getNorthEast();
	var sw = p.getBounds().getSouthWest();
	
	this.bounds.extend(ne);
	this.bounds.extend(sw);
	this.overlayman.folderBounds[idx].extend(sw);
	this.overlayman.folderBounds[idx].extend(ne); 	
	this.polylines[pnum].bounds.extend(ne);
	this.polylines[pnum].bounds.extend(sw);
	
	n = this.overlayman.markers.length;
	this.polylines[pnum].lineidx.push(n);
//	console.log(op.name +" = "+ idx +" aka "+this.polylines[pnum].lineidx);
//	(marker, title, idx, sidebar, visible, forcevisible)
 	this.overlayman.addMarker(p, op.name, idx, parm, op.visibility,true);
};

GeoXml.prototype.createPolyline = function(lines,color,width,opacity,pbounds,name,desc,idx, visible, kml_id) {
	var that = this;
	var isnew = true;
	var p = {};
	if(!color){p.color = that.randomColor();}
	else { p.color = color; }
	if(!opacity){p.opacity= 0.45;}
		else { p.opacity = opacity; }
	if(!width){p.width = 4;}
		 else{  p.width = width; }
	p.idx = idx; 
	p.visibility = visible;
	if(that.hideall){ p.visibility = false; }
	p.name = name;
	p.bounds = new google.maps.LatLngBounds();
	p.description = desc;
	p.lines = [];
	p.lines.push(lines);
	p.lineidx = [];
	p.id = kml_id;
	that.polylines.push(p);
	setTimeout(that.myvar+".processLine("+(that.polylines.length-1)+",0,'"+idx+"',true);",15);
	};

// Create Polygon

GeoXml.prototype.processPLine = function(pnum,linenum,idx) {
        	
	//alert(p.lines.length);
	var p = this.polyset[pnum];
	var line = p.lines[linenum];
	var obj = {};
	
	if(line && line.length){
		p.obj.polylines.push(line);
		}

	if(linenum == p.lines.length-1){	
		this.finishPolygon(p.obj,idx);
		}
	else {
	    setTimeout(this.myvar+".processPLine("+pnum+","+(linenum+1)+",'"+idx+"');",5);
	    }
	};	

GeoXml.prototype.finishPolygon = function(op,idx) {
  op.type = "polygon"; 
  this.finishPolygonJSON(op,idx,false);
   };

GeoXml.prototype.getBounds = function(polygon) {
                var bounds = new google.maps.LatLngBounds();
                var paths = polygon.getPaths();
                var path;
               
                for (var p = 0; p < paths.getLength(); p++) {
                        path = paths.getAt(p);
                        for (var i = 0; i < path.getLength(); i++) {
                                bounds.extend(path.getAt(i));
                        }
                }

                return bounds;
	};
	
GeoXml.prototype.finishPolygonJSON = function(op,idx,updatebound,lastpoly) {
  var that = this;
  var iwoptions = that.opts.iwoptions || {};
  if(typeof op.visibility == "undefined") { op.visibility=true; }
  if(that.hideall){ op.visibility = false; }
  var desc = unescape(op.description);
  op.opacity = op.fillOpacity;
  var p = {};
  p.paths = op.polylines;
	//alert("my description"+ desc);
  var html = "<p style='font-family: Arial, sans-serif; font-weight: bold; font-size: medium; margin-bottom: 0em; margin-top:0em'>"+op.name+"</p>";
  if(desc != op.name){
  html += "<div style='font-family: Arial, sans-serif;font-size: small;width:"+this.iwwidth+"px;'>"+desc+"</div>";
  }
   
 var newgeom = (lastpoly != "p_"+op.name);
  if(newgeom && this.opts.sidebarid){
	this.latestsidebar = that.myvar +"sb"+  this.overlayman.markers.length;
	}
  else {
	this.latestsidebar = "";
  	}

  if(that.opts.domouseover){
  	p.mess = html;
	}
	if(op.strokeColor){
		p.strokeColor = op.strokeColor;
		}
	else {
		p.strokeColor = op.color;
		}
	if(op.outline) {
		if(op.strokeWeight){
			p.strokeWeight = op.strokeWeight;
			}
		else {
			p.strokeWeight = op.width;
			}
		p.strokeOpacity = op.strokeOpacity;
		}
	else {
		p.strokeWeight = 0;
		p.strokeOpacity = 0;
		}
  p.hilite = that.hilite;
  if(!op.fill)
	p.fillOpacity = 0.0;
  else
	p.fillOpacity = op.opacity;
  p.fillColor = op.color.toString();
  var polygon = new google.maps.Polygon(p); //{paths:op.polylines}
  polygon.mb = that.mb;
  if(that.domouseover){
	polygon.mess = html;
	}
  polygon.geoxml = that;
  polygon.title = op.name;
  polygon.id = op.id;
  var n = this.overlayman.markers.length;
  if(newgeom){
	that.multibounds.push(new google.maps.LatLngBounds());
 	that.polygons.push([]);
	}
  var len = that.multibounds.length-1;
  that.multibounds[len].extend(polygon.getBounds().getSouthWest());
  that.multibounds[len].extend(polygon.getBounds().getNorthEast()); 
  that.polygons[that.polygons.length-1].push(n);
  polygon.polyindex = that.polygons.length-1;
  polygon.geomindex = len;
  polygon.sidebarid = this.latestsidebar;
  
  
  
	var infoWindowOptions = { 
					content: html,
					pixelOffset: new google.maps.Size(0, 2),
					position: polygon.getCenter()
					};
	if(this.maxiwwidth){
			infoWindowOptions.maxWidth = this.maxiwwidth;
			}
			
	polygon.infoWindow = new google.maps.InfoWindow(infoWindowOptions);
				
  polygon.onOver = function(e){ 
		if(this.geoxml.dohilite){
			if(this.sidebarid){
				var bar = Lance$(this.sidebarid);
				if(!!bar){
					bar.style.backgroundColor = this.hilite.color;
					bar.style.color = this.hilite.textcolor;
					}
				}
			if(this.geoxml.clickablepolys){
			
				var poly = this.geoxml.polygons[this.polyindex];
				if(poly && this.hidden!=true) {
					for (var pg =0;pg < poly.length;pg++) {
					var mark = this.geoxml.overlayman.markers[poly[pg]];
					var color;
					mark.realColor = p.fillColor;
					mark.realOpacity = p.fillOpacity;
					mark.setOptions({fillColor:this.hilite.color,fillOpacity:this.hilite.opacity});
					}
				}
			}
		}
		if(polygon.title){
			GeoXml.tooltip.show(polygon.title,e);
			}
	if(this.mess){ polygon.geoxml.mb.showMess(this.mess,5000); }
	};

		 
  polygon.onOut = function(){ 
	if(this.geoxml.dohilite){
		if(this.sidebarid){
			var bar = Lance$(this.sidebarid);
			if(!!bar){
				bar.style.background= "none";
				bar.style.color = "";
				}
			}
		var poly;
		if(this.geoxml.clickablepolys) {
			poly = this.geoxml.polygons[this.polyindex];
			}
		if(poly && this.hidden != true) {
			for (var pg =0;pg < poly.length;pg++) {
				var mark = this.geoxml.overlayman.markers[poly[pg]];
				var color = mark.realColor.toString();
				var opacity = mark.realOpacity.toString();
				mark.setOptions({fillColor:color,fillOpacity:opacity});
				//mark.redraw(true);
				}
			}
		}
	if(this.mess){ this.geoxml.mb.hideMess(); }
	};
	
	polygon.onClick = function(point) {
		if(!!!point && this.geoxml.alwayspop){
			bounds = this.geoxml.multibounds[this.geomindex]; 
			this.geoxml.map.fitBounds(bounds);
			point = {};
			point.latLng = bounds.getCenter(); 
			}
		if(!!!point){ 
			this.geoxml.mb.showMess("Zooming to "+polygon.title,3000);
			bounds = this.geoxml.multibounds[this.geomindex];  
			this.geoxml.map.fitBounds(bounds);
			point = bounds.getCenter();
		} else {
			point = point.latLng;
			}
	 
		if(this.geoxml.clickablepolys){ 
			if (!!this.geoxml.opts.singleInfoWindow) {
				if (!!this.geoxml.lastMarker && !!this.geoxml.lastMarker.infoWindow) {
					this.geoxml.lastMarker.infoWindow.close();
					}
				this.geoxml.lastMarker = this;
				}
			this.infoWindow.setPosition(point);
			this.infoWindow.open(this.geoxml.map);
			}
		};
	
	google.maps.event.addListener(polygon,"click",polygon.onClick );
	google.maps.event.addListener(polygon,"mouseout",polygon.onOut);
	google.maps.event.addListener(polygon,"mouseover",polygon.onOver);

  op.description = escape(desc);
  this.kml[idx].marks.push(op);
  polygon.setMap(this.map);
  var bounds;  
	 
	

if(this.opts.polylabelclass && newgeom ) {
 	var epoint =  this.getBounds(polygon).getCenter();
        var off = this.opts.polylabeloffset;
	if(!off){ off= new google.maps.Size(0,0); }
	off.x = -(op.name.length * 6);
 	var l = new ELabel(epoint, " "+op.name+" ", this.opts.polylabelclass, off, this.polylabelopacity, true);
	polygon.label = l;
	l.setMap(this.map); 
	}

  var nhtml ="";
  var parm;
 
  if (this.basesidebar &&  newgeom) { 
    var folderid = this.myvar+"_folder"+idx;
    var blob;
	if (this.rectangleLegend){ 
		blob = '<svg xmlns="http://www.w3.org/2000/svg" style="margin-left:0px;margin-top:0px" version="1.2" width="16px" height="16px">';
		blob += ' <rect stroke="none" height="16" width="16" y="0" x="0" stroke-width="null" fill="'+op.color+'"/></svg>';
		}
	else {
		blob = '<svg xmlns="http://www.w3.org/2000/svg" style="margin-left:0px;margin-top:0px" version="1.2" width="16px" height="16px">';
		blob += '<path stroke="'+op.strokeColor+'" transform="rotate(139.901 9.70429 10.2675)" fill="'+op.color+'" stroke-dasharray="null" stroke-linejoin="null" stroke-linecap="null" d="m2.72366,9.83336c3.74686,-4.221 6.00924,-2.11097 7.43079,1.52863c1.42154,3.63961 3.85727,-1.60143 6.07385,1.67422c2.21659,3.27565 -4.2,6.26012 -7.17232,7.93434" id="svg_2"/></svg>';
		}
		
    if(this.sidebarsnippet){
		var desc2 = GeoXml.stripHTML(desc);
		desc2 = desc2.substring(0,20);}
	else {desc2 = '';}
    parm =  this.myvar+"$$$" +op.name + "$$$polygon$$$" + n +"$$$" + blob + "$$$" +op.visibility+"$$$null$$$"+desc2; 
    }
   if(updatebound) {
  	var ne = polygon.getBounds().getNorthEast();
   	var sw = polygon.getBounds().getSouthWest();
   	this.bounds.extend(ne);
   	this.bounds.extend(sw);
   	this.overlayman.folderBounds[idx].extend(sw);
   	this.overlayman.folderBounds[idx].extend(ne);
	}
   this.overlayman.addMarker(polygon,op.name,idx, parm, op.visibility);
   return op.name;
   };

GeoXml.prototype.finishLineJSON = function(po, idx, lastlinename){
	var m;
	var that = this;
	var thismap = this.map;
	m = new google.maps.Polyline({path:po.points,strokeColor:po.color,strokeWeight:po.weight,strokeOpacity:po.opacity,clickable:this.clickablelines}); 
	m.mytitle = po.name;
	m.title = po.name;
	m.strokeColor = po.color;
	m.strokeOpacity = po.opacity;
	m.geoxml = this;
    m.hilite = this.hilite;
	var n = that.overlayman.markers.length;
	var lineisnew = false;
	var pnum;
	if(("l_"+po.name) != lastlinename){
		lineisnew = true;
		that.polylines.push(po);
		pnum = that.polylines.length-1;
		that.polylines[pnum].lineidx = [];
		that.polylines[pnum].lineidx.push(n);
		that.latestsidebar = that.myvar +"sb"+n;
		}
	else {
		pnum = that.polylines.length-1;
		that.polylines[pnum].lineidx.push(n);
		}

	if(this.opts.basesidebar){
		m.sidebarid = that.latestsidebar;
		}
  	m.onOver = function(){
		if(this.geoxml.dohilite){
			if(!!this.sidebarid){
				var bar = Lance$(this.sidebarid);	
				if(bar && typeof bar !="undefined")
					{bar.style.backgroundColor = this.hilite.color;}
				}
			this.realColor = this.strokeColor;
			if(m.hidden!=true){
				if(m && typeof m!="undefined"){ 
				m.setOptions({strokeColor:this.hilite.color}); }
				//this.redraw(true);
				}
			}
		if(this.mess) { this.geoxml.mb.showMess(this.mess,5000); } else { this.title = "Click for more information about "+this.mytitle; }
		};
  	m.onOut = function(){ 	
		if(this.geoxml.dohilite){
			if(!!this.sidebarid){
				var bar = Lance$(this.sidebarid);	
				if(bar && typeof bar !="undefined"){bar.style.background = "none"; }
				}
			if(m.hidden!=true){
				if(m && typeof m!="undefined"){ m.setOptions({strokeColor:this.realColor}); }
				//this.redraw(true);
				}
			}
		if(this.mess){ this.geoxml.mb.hideMess(); }
		};
 
	google.maps.event.addListener(m,"mouseover",m.onOver);
	google.maps.event.addListener(m,"mouseover",m.onOut);
	 

	var parm = "";
	that.kml[idx].marks.push(po);
	var desc = unescape(po.description);
	 var awidth = this.iwwidth;
 	if(desc.length * 8 <  awidth){
		awidth = desc.length * 8;
 		}
 	if(awidth < po.name.length * 12){
		awidth = po.name.length * 12;
 		}

	var html = "<div style='font-family: Arial, sans-serif; font-weight: bold; font-size: medium; margin-bottom: 0em;'>"+po.name +"</div>";
	if (po.name != desc) {
		html += "<div style='font-family: Arial, sans-serif;font-size: small;width:"+awidth+"px'>"+desc+"</div>";
		}
	m.map = this.map;
	var infoWindowOptions = { 
				content: html,
				pixelOffset: new google.maps.Size(0, 2),
				position:point
				};
	if(this.maxiwwidth){
			infoWindowOptions.maxWidth = this.maxiwwidth;
			}
	m.infoWindow = new google.maps.InfoWindow(infoWindowOptions);
	if(this.clickablelines){
  		google.maps.event.addListener(m,"click", function(point) {
		if(!!!point){ point=m.getPosition(); } 
			m.infoWindow.open();
		} );
		}

	if(that.basesidebar && lineisnew) {
		var blob;
		if (this.rectangleLegend){
			var s_w = po.weight;
			if (s_w < 1){ s_w = 1; }
			var m_w = parseInt(((16 - s_w)/2));
			blob = '<svg xmlns="http://www.w3.org/2000/svg" style="margin-left:0px;margin-top:0px" version="1.2" width="16px" height="16px">';
			blob += ' <rect stroke="none" height="16" width="'+s_w+'" y="0" x="'+m_w+'" stroke-width="null" fill="'+po.color+'"/></svg>';
			}
		else {
			blob = '<svg xmlns="http://www.w3.org/2000/svg" style="margin-left:0px;margin-top:0px" version="1.2" width="16px" height="16px">';
			if (op.color=='#ffffff' || op.color=='#FFFFFF'){
				blob += ' <rect stroke="none" height="16" width="16" y="0" x="0" stroke-width="null" fill="#cbcbcb"/>';
				}
			blob += '<path stroke="'+op.color+'" d="m1.514515,10.908736c-0.457545,0.489079 0.473927,-7.091639 5.261762,-7.336174c4.787838,-0.244535 -0.457535,7.825252 4.804223,2.445392c5.261755,-5.37986 1.949991,11.609838 2.287748,3.179009"  stroke-width="'+po.weight+'" fill="none"/></svg>';
			}
		if(typeof po.visibility == "undefined"){ po.visibility = true; }
			if(this.sidebarsnippet){
				var desc2 = GeoXml.stripHTML(desc);
				desc2 = desc2.substring(0,20);}
			else {desc2 = '';}
		parm =  that.myvar+"$$$" +po.name + "$$$polyline$$$" + n +"$$$" + blob + "$$$" +po.visibility+"$$$"+(that.polylines.length-1)+"$$$"+desc2;
 		}	
	
	var ne = m.getBounds().getNorthEast();
	var sw = m.getBounds().getSouthWest();
	that.bounds.extend(ne);
	that.bounds.extend(sw);
	that.overlayman.folderBounds[idx].extend(sw);
	that.overlayman.folderBounds[idx].extend(ne);
	that.overlayman.addMarker(m, po.name, idx, parm, po.visibility);	
	return(po.name);	
	};
	
GeoXml.prototype.handlePlaceObj = function(num, max, idx, lastlinename, depth){
	var that = this;
	var po = that.jsonmarks[num];
	var name = po.name;
	if(po.title){ name = po.title; }
	if(name.length+depth > that.maxtitlewidth){ that.maxtitlewidth = name.length+depth; }
	switch (po.type) {
			case "polygon" :
				lastlinename = "p_"+ that.finishPolygonJSON(po,idx,true,lastlinename);
				break;
			case "line" :  
			case "polyline" :
				lastlinename = "l_"+ that.finishLineJSON(po,idx,lastlinename);		
				break;
			case "point":
          			that.createMarkerJSON(po,idx);
				lastlinename = "";
				break;
		 	}
	if (num < max-1){
		var act = that.myvar+".handlePlaceObj("+(num+1)+","+max+","+idx+",\""+lastlinename+"\","+depth+");";
		document.status = "processing "+name;
		setTimeout(act,1);
		}
	else {
		lastlinename = "";		
		if(num == that.jsonmarks.length-1){
			that.progress--;
    			if (that.progress <= 0) {
      		 	// Shall we zoom to the bounds?
      				if (!that.opts.nozoom) {
						that.map.fitBounds(that.bounds); 
      					}
      				google.maps.event.trigger(that,"parsed");
					that.setFolders();
					that.mb.showMess("Finished Parsing",1000);
					that.ParseURL();	
				}
	 		}
		}
	};

GeoXml.prototype.parseJSON  = function (doc, title, latlon, desc, sbid){
	var that = this;
 	that.overlayman.miStart = new Date();
	that.jsdocs = eval('(' + doc + ')');
	var bar = Lance$(that.basesidebar);
	if(bar){ bar.style.display=""; }
	that.recurseJSON(that.jsdocs[0], title, desc, that.basesidebar, 0);
	};

GeoXml.prototype.setFolders = function() {
	var that = this;
	var len = that.kml.length;
	for(var i=0;i<len;i++){
		var fid = that.kml[i].folderid;
		var fidstr = new String(fid);
		var fb = fidstr.replace("_folder","FB");
	 	var fi = Lance$(fb);
		var fob = Lance$(fid);
 		if(fob !== null && fid!= that.opts.sidebarid) {
			if(!!that.kml[i].open){
				fob.style.display='block';
				}
			else {
				fob.style.display='none';
				if(fi.src==that.foldericon){ fi.src = that.folderclosedicon;}
				if(fi.src==that.docicon){ fi.src = that.docclosedicon; }
				}
			}
		}
	 
	};
 
GeoXml.prototype.recurseJSON = function (doc, title, desc, sbid, depth){
	var that = this;
	var polys = doc.marks;
	var name = doc.title;
	if(!sbid){ sbid = 0; }
	var description = unescape(doc.description);
	if(!description && desc){ description = desc; }
	var keepopen = that.forcefoldersopen;
	if(doc.open){ keepopen = true; }
	var visible = true;
	if(typeof doc.visibility!="undefined" && doc.visibility){visible = true; }
	if(that.hideall){visible = false;}
       	var snippet = doc.snippet;
	var idx = that.overlayman.folders.length;
	if(!description){ description = name; }
	var folderid;
	var icon;
	that.overlayman.folders.push([]);
	that.overlayman.subfolders.push([]);
    	that.overlayman.folderhtml.push([]);
    	that.overlayman.folderhtmlast.push(0);
	that.overlayman.folderBounds.push(new google.maps.LatLngBounds());
	that.kml.push(new KMLObj(title,description,keepopen));
	if((!depth && (doc.folders && doc.folders.length >1)) || doc.marks.length){
		if(depth < 2 || doc.marks.length < 1) { icon = that.globalicon; }
		else { icon = that.foldericon;}
		folderid = that.createFolder(idx, name, sbid, icon, description, snippet, keepopen, visible);
		} 
	else {
		folderid = sbid;
		}
	var parm, blob;
	var nhtml ="";
	var html;
	var m;
	var num = that.jsonmarks.length;
	var max = num + polys.length;
 	for(var p =0;p<polys.length;p++){
		var po = polys[p];
		that.jsonmarks.push(po);
		desc = unescape(po.description);
		m = null;
 		if(that.opts.preloadHTML && desc && desc.match(/<(\s)*img/i)){
			var preload = document.createElement("span");
     		preload.style.visibility = "visible";
			preload.style.position = "absolute";
			preload.style.left = "-1200px";
			preload.style.top = "-1200px";
			preload.style.zIndex = this.overlayman.markers.length; 
     		document.body.appendChild(preload);
			preload.innerHTML = desc;
			}	 
		}	

	if(that.groundOverlays){
		}

	if(polys.length){ that.handlePlaceObj(num,max,idx,null,depth); }
	var fc = 0;
	var fid = 0;
	if(typeof doc.folders!="undefined"){
		fc = doc.folders.lenth;
		for(var f=0;f<doc.folders.length;++f){
			var nextdoc = that.jsdocs[doc.folders[f]];
			fid = that.recurseJSON(nextdoc, nextdoc.title, nextdoc.description, folderid, (depth+1));
			that.overlayman.subfolders[idx].push(fid);
			that.overlayman.folderBounds[idx].extend(that.overlayman.folderBounds[fid].getSouthWest());
			that.overlayman.folderBounds[idx].extend(that.overlayman.folderBounds[fid].getNorthEast());
			if(fid != idx){ that.kml[idx].folders.push(fid); }
			}
		}

        if(fc || polys.length ){
		that.bounds.extend(that.overlayman.folderBounds[idx].getSouthWest());
		that.bounds.extend(that.overlayman.folderBounds[idx].getNorthEast());
		}

	return idx;
	};

GeoXml.prototype.createPolygon = function(lines,color,width,opacity,fillcolor,fillOpacity, pbounds, name, desc, folderid, visible,fill,outline,kml_id) {
  var thismap = this.map;
  
  var p = {};	
  p.obj = {"description":desc,"name":name };
  p.obj.polylines = []; 
  p.obj.id = kml_id;
  p.obj.visibility = visible;
  p.obj.fill = fill;
  p.obj.outline = outline; 
  p.obj.fillcolor = fillcolor;
  p.obj.strokeColor = color; 
  p.obj.strokeOpacity = opacity;
  
  if(!color){p.obj.strokeColor = this.style.color;}
  else { p.obj.strokeColor = color; }
 
  if(!fillcolor){ p.obj.color = this.randomColor(); }
  else {p.obj.color = fillcolor;}

  if(!!opacity){p.obj.opacity= opacity;}
	else{ 
		p.obj.opacity = this.style.opacity; 
		p.obj.strokeOpacity = this.style.opacity;
		}

  if(!!fillOpacity){p.obj.fillOpacity = fillOpacity;}
   else { 

	   p.obj.fillOpacity = this.style.fillopacity;
		}

  if(!width){p.obj.strokeWeight = this.style.width;}
  else{ p.obj.strokeWeight = width; }

  p.bounds = pbounds;
  p.lines = lines;
  p.sidebarid = this.opts.sidebarid;
  this.polyset.push(p);
 // document.status = "processing poly "+name;
 // alert(name);
  setTimeout(this.myvar+".processPLine("+(this.polyset.length-1)+",0,'"+folderid+"')",1);
};

GeoXml.prototype.toggleFolder = function(i){
	var f = Lance$(this.myvar+"_folder"+i);
	var tb = Lance$(this.myvar+"TB"+i);

	var folderimg = Lance$(this.myvar+'FB'+i);

	if(f.style.display=="none"){
			f.style.display="";
			if(tb){ tb.style.fontWeight = "normal"; }
				if(folderimg.src == this.folderclosedicon){
					folderimg.src = this.foldericon;
					}
				if(folderimg.src == this.docclosedicon){
					folderimg.src = this.docicon;
					}
			}
		else{ 
			f.style.display ="none"; 
			if(tb){ tb.style.fontWeight = "bold"; }
				if(folderimg.src == this.foldericon){
					folderimg.src = this.folderclosedicon;
					}
				if(folderimg.src == this.docicon){
					folderimg.src = this.docclosedicon;
					}
			}
		 
	};

GeoXml.prototype.saveJSON = function(){

	if(topwin.standalone){
		var fpath = browseForSave("Select a directory to place your json file","JSON Data Files (*.js)|*.js|All Files (*.*)|*.*","JSON-DATA");

 		if(typeof fpath!="undefined"){
			var jsonstr = JSON.stringify(this.kml);
			 saveLocalFile (fpath+".js",jsonstr); 
			}
		return;
		}

	if(typeof JSON != "undefined"){
		var jsonstr = JSON.stringify(this.kml);
		if(typeof serverBlessJSON!="undefined"){
			serverBlessJSON(escape(jsonstr),"MyKJSON"); 
			}
		else {
			this.showIt(jsonstr);
			}
		}
	else {
		var errmess="No JSON methods currently available";
		if(console){
			console.error(errmess);
			}
		else { alert(errmess); }
		}
	};

GeoXml.prototype.hide = function(){
	//if(this.polylines.length > 0 || this.polygons.length > 0){
		this.contentToggle(1,false);
		this.overlayman.currentZoomLevel = -1;
		OverlayManager.Display(this.overlayman);
		google.events.trigger(this,"changed");
	//	}
	//else {
	//does not support matching sidebar entry toggling yet
	//	this.markerpane.style.display = "none";
	//	alert("hiding marker pane");
	//	}
	};
GeoXml.prototype.setMap = function(map){
	if(map){
		this.show();
		}
	else {
		this.hide();
		}
	};
GeoXml.prototype.show = function(){
	//if(this.polylines.length > 0 || this.polygons.length > 0){
		this.contentToggle(1,true);
		this.overlayman.currentZoomLevel = -1;
		OverlayManager.Display(this.overlayman);
	
	//	}
	//else {
	//does not support matching sidebar entry toggling yet
		//alert("showing marker pane");
	//	this.markerpane.style.display = "";
	//	}
	};

GeoXml.prototype.toggleContents = function(i,show){
	this.contentToggle(i,show);
	this.overlayman.currentZoomLevel = -1;
	OverlayManager.Display(this.overlayman);
	//setTimeout("OverlayManager.Display("+this.var+".overlayman)",10000);
	};

GeoXml.prototype.contentToggle = function(i,show){
 	var f = this.overlayman.folders[i];
	var cb;
	var j;
	
	var m;
	if(typeof f == "undefined"){
		this.mb.showMess("folder "+f+" not defined");
		return;
		}
	//alert(f.length+" "+this.overlayman.markers.length);
	if(show){
	for (j=0;j<f.length;j++){
		   this.overlayman.markers[f[j]].setMap(this.map);
		   this.overlayman.markers[f[j]].onMap = true;
		   if(!!this.overlayman.markers[f[j]].label){ this.overlayman.markers[f[j]].label.setMap(this.map)  }

			if(this.basesidebar){	
				cb = Lance$(this.myvar+''+f[j]+'CB');
				if(cb && typeof cb!="undefined"){ cb.checked = true; }
				}
			this.overlayman.markers[f[j]].hidden = false;

			}
		}
	else {
	   for (j=0;j<f.length;j++){
			this.overlayman.markers[f[j]].hidden = true;
			this.overlayman.markers[f[j]].onMap = false;
			this.overlayman.markers[f[j]].setMap(null);
		   if(!!this.overlayman.markers[f[j]].label){ this.overlayman.markers[f[j]].label.setMap(null)  }
			
			if(this.basesidebar){
				cb = Lance$(this.myvar+''+f[j]+'CB');
				if(cb && typeof cb!="undefined" ){cb.checked = false;}
				}
			
			}
		}

	var sf = this.overlayman.subfolders[i];
	if(typeof sf!="undefined"){
 		for (j=0;j<sf.length;j++){
			if(sf[j]!=i){
				if(this.basesidebar){
	 				cb = Lance$(this.myvar+''+sf[j]+'FCB');
					if(cb && typeof cb!="undefined"){ cb.checked = (!!show);}
					}
				this.contentToggle(sf[j],show);
				}
			}
		 }
		//google.events.trigger(this,"changed");
		//console.log("changed "+f);
	};


GeoXml.prototype.showHide = function(a,show, p){ // if a is not defined then p will be.
	var m, i;
 	if(a!== null){	
		if(show){
			this.overlayman.markers[a].setMap(this.map);
			this.overlayman.markers[a].onMap = true;
			this.overlayman.markers[a].hidden = false; 
//			if(!!this.overlayman.markers[a].label){ this.overlayman.markers[a].label.show();  }
			if(!!this.overlayman.markers[a].label){ this.overlayman.markers[a].label.setMap(this.map)  }
			}	
		else  { 
			this.overlayman.markers[a].setMap(null);
			this.overlayman.markers[a].onMap = false;
			this.overlayman.markers[a].hidden = true;
//			if(!!this.overlayman.markers[a].label){ this.overlayman.markers[a].label.hide(); }		
			if(!!this.overlayman.markers[a].label){ this.overlayman.markers[a].label.setMap(null); }              
			}
		}
	else {
		var ms = this.polylines[p];
		 
		if(show){
			for(i=0;i<ms.lineidx.length;i++){
				var li = ms.lineidx[i];
				this.overlayman.markers[li].setMap(this.map); 
				this.overlayman.markers[li].onMap = true;
				this.overlayman.markers[li].hidden = false;	
			//	alert(this.overlayman.markers[li].title);
//				if(!!m.label){m.label.show(); }
				if(!!ms.label){ms.label.setMap(this.map); }
				}
		    }
		else {
			for(i=0;i<ms.lineidx.length;i++){
				this.overlayman.markers[ms.lineidx[i]].setMap(null); 
				this.overlayman.markers[ms.lineidx[i]].onMap = false;
				this.overlayman.markers[ms.lineidx[i]].hidden = true;	
				if(!!ms.label){ms.label.setMap(null); }
				}
		    }
	    }
	this.overlayman.currentZoomLevel = -1;
	OverlayManager.Display(this.overlayman,true);
	};


GeoXml.prototype.toggleOff = function(a,show){
	if(show){ 
		this.overlayman.markers[a].setMap(this.map);
		this.overlayman.markers[a].hidden = false; 
		}	
	else  { 
		this.overlayman.markers[a].setMap(null);
		this.overlayman.markers[a].hidden = true;
		}
	if(this.labels && this.labels.onMap){
		this.labels.setMap(null);
 		this.labels.setMap(this.map); 
		}
	};

// Sidebar factory method One - adds an entry to the sidebar
GeoXml.addSidebar = function(myvar, name, type, e, graphic, ckd, i, snippet) {
   var check = "checked";
   if(ckd=="false"){ check = ""; }
    var h="";
    var mid = myvar+"sb"+e;
    if(snippet && snippet != "undefined"){
	snippet = "<br><span class='"+myvar+"snip'>"+snippet+"</span>";
    	}
    else {
	    snippet = "";
    }
	console.log(name);
   switch(type) {
   case  "marker" :  h = '<li id="'+mid+'" onmouseout="google.maps.event.trigger(' + myvar+ '.overlayman.markers['+e+'],\'mouseout\');" onmouseover="google.maps.event.trigger(' + myvar+ '.overlayman.markers['+e+'],\'mouseover\');" ><input id="'+myvar+''+e+'CB" type="checkbox" style="vertical-align:middle" '+check+' onclick="'+myvar+'.showHide('+e+',this.checked)"><a href="#" onclick="google.maps.event.trigger(' + myvar+ '.overlayman.markers['+e+'],\'click\');return false;">'+ graphic + '&nbsp;' + name + '</a>'+snippet+'</li>';
   break;
  case  "polyline" :  h = '<li id="'+mid+'"  onmouseout="'+myvar+ '.overlayman.markers['+e+'].onOut();" onmouseover="'+myvar+ '.overlayman.markers['+e+'].onOver();" ><input id="'+myvar+''+e+'CB" type="checkbox" '+check+' onclick="'+myvar+'.showHide(null,this.checked,'+i+')"><span style="margin-top:6px;"><a href="#" onclick="google.maps.event.trigger(' + myvar+ '.overlayman.markers['+e+'],\'click\');return false;">&nbsp;' + graphic + '&nbsp;' + name + '</a></span>'+snippet+'</li>';
  break;
  case "polygon": h = '<li id="'+mid+'"  onmouseout="'+myvar+ '.overlayman.markers['+e+'].onOut();" onmouseover="'+myvar+ '.overlayman.markers['+e+'].onOver();" ><input id="'+myvar+''+e+'CB" type="checkbox" '+check+' onclick="'+myvar+'.showHide('+e+',this.checked)"><span style="margin-top:6px;"><a href="#" onclick="google.maps.event.trigger(' + myvar+ '.overlayman.markers['+e+'],\'click\');return false;">&nbsp;' + graphic + '&nbsp;' + name + '</a></span></nobr>'+snippet+'</li>';
  break;
 case "groundoverlay": h = '<li id="'+mid+'"><input id="'+myvar+''+e+'CB" type="checkbox" '+check+' onclick="'+myvar+'.showHide('+e+',this.checked)"><span style="margin-top:6px;"><a href="#" onclick="google.maps.event.trigger(' + myvar+ '.overlayman.markers['+e+'],\'zoomto\');return false;">&nbsp;' + graphic + '&nbsp;' + name + '</a></span>'+snippet+'</li>';
   break;
case "tiledoverlay": h = '<li id="'+mid+'"><nobr><input id="'+myvar+''+e+'CB" type="checkbox" '+check+' onclick="'+myvar+'.toggleOff('+e+',this.checked)"><span style="margin-top:6px;"><a href="#" oncontextMenu="'+myvar+'.upgradeLayer('+i+');return false;" onclick="google.maps.event.trigger(' + myvar+ '.overlayman.markers['+e+'],\'zoomto\');return false;">'+GeoXml.WMSICON +'&nbsp;'+ name + '</a><br />'+ graphic + '&nbsp;' +'</span>'+snippet+'</li>';
   break;
}
return h;
};

// Dropdown factory method
GeoXml.addDropdown = function(myvar,name,type,i,graphic) {
    return '<option value="' + i + '">' + name +'</option>';
};

// Request to Parse an XML file

GeoXml.prototype.parse = function(titles) {
 var that = this;
 var names =[];
 if(typeof titles !="undefined"){
 if(typeof titles!= "string") {
 	names = titles;
	}
 else {
	names = titles.split(",");
	}
}
 that.progress += that.urls.length;
 for (var u=0; u<that.urls.length; u++) {
   var title = names[u];
  if(typeof title =="undefined" || !title || title =="null" ){
  	var segs = that.urls[u].split("/");
	title = segs[segs.length-1];
	}
   that.mb.showMess("Loading "+title);
   var re = /\.js$/i;
   if(that.urls[u].search(re) != -1){
	that.loadJSONUrl(this.urls[u], title);
	}
   else {
 	that.loadXMLUrl(this.urls[u], title);	}
 }
};

GeoXml.prototype.removeAll = function() {
	this.allRemoved = true;
	for (var a=0;a < this.overlayman.markers.length; a++) {
		this.toggleOff(a,false);
		}
	};
	
GeoXml.prototype.addAll = function() {
	this.allRemoved = false;
	for (var a=0;a < this.overlayman.markers.length; a++) {
		this.toggleOff(a,true);
		}
	};
	
GeoXml.prototype.processString = function(doc,titles,latlon) {
  var names =[];
 if(titles) {
 	names = titles.split(",");
	}
  if (typeof doc == "string") {
    this.docs = [doc];
  } else {
    this.docs = doc;
  }
  this.progress += this.docs.length;
  for (var u=0; u<this.docs.length; u++) {
    this.mb.showMess("Processing "+names[u]);
    this.processing(this.parseXML(this.docs[u]),names[u],latlon);
  }
};

// Cross-browser xml parsing
GeoXml.prototype.parseXML = function( data ) {
		var xml, tmp;
		try {
			if ( window.DOMParser ) { // Standard
				tmp = new DOMParser();
				xml = tmp.parseFromString( data , "text/xml" );
			} else { // IE
				xml = new ActiveXObject( "Microsoft.XMLDOM" );
				xml.async = "false";
				xml.loadXML( data );
			}
		} catch( e ) {
			xml = undefined;
		}
		if ( !xml || !xml.documentElement || xml.getElementsByTagName( "parsererror" ).length ) {
			var errmess = "Invalid XML: " + data;
			if (console){
				console.error(errmess);
				}
			else { alert(errmess); }
		}
		return xml;
	};

GeoXml.prototype.getText = function( elems ) {
	var ret = "", elem;
	if (!elems||!elems.childNodes)
		return ret;
		
	elems = elems.childNodes;

	for ( var i = 0; elems[i]; i++ ) {
		elem = elems[i];

		// Get the text from text nodes and CDATA nodes
		if ( elem.nodeType === 3 || elem.nodeType === 4 ) {
			ret += elem.nodeValue;

		// Traverse everything else, except comment nodes
		} else if ( elem.nodeType !== 8 ) {
			ret += this.getText( elem.childNodes );
		}
	}

	return ret;
};

GeoXml.prototype.processXML = function(doc,titles,latlon) {
 var names =[];
 if(typeof titles !="undefined"){
 	if(typeof titles == "string") {
 		names = titles.split(",");
		}
	 else {  names = titles; }
	}

  if(typeof doc == "array"){
	this.docs = doc;
	}
  else {
 	this.docs = [doc];
	}
  this.progress += this.docs.length;
  for (var u=0; u<this.docs.length; u++) {
	var mess = "Processing "+names[u];
	this.mb.showMess(mess);
  	this.processing(this.docs[u],names[u],latlon);
	}
};

GeoXml.prototype.makeDescription = function(elem, title, depth) {
         var d = ""; 
	 var len = elem.childNodes.length;
	 var ln = 0;
	 var val;

	 while (len--) {
		var subelem = elem.childNodes.item(ln);
		var nn = subelem.nodeName;
		var sec = nn.split(":");
		var base = "";
		if(sec.length>1){	       
			base = sec[1];
			}
		else { base = nn;}
 	
		if(base.match(/^(lat|long|visible|visibility|boundedBy|StyleMap|drawOrder|styleUrl|posList|coordinates|Style|Polygon|LineString|Point|LookAt|drawOrder|Envelope|Box|MultiPolygon|where|guid)/)){
 			this.currdeschead = ""; 
			}
		else {
			
			if(base.match(/#text|the_geom|SchemaData|ExtendedData|#cdata-section/)){}
			else {
				if(base.match(/Snippet/i)){ 
						}
				else {	
					if(base.match(/SimpleData/)){
						base = subelem.getAttribute("name");
						}
					this.currdeschead = "<b>&nbsp;"+base+"&nbsp;</b> :";
					}
				}
			val = subelem.nodeValue;
			if(nn == "link"){
				var href = subelem.getAttribute("href");
				if(href && href!='null'){
					val = '<a target="_blank" title="'+href+'" href="' + href + '">Link</a>';
					}
				else {
					if(val && val!= "null"){
					val = '<a target="_blank" title="'+val+'" href="' + val + '">Link</a>';
						}
					}
				this.currdeschead = "Link to Article"; 
				}
			if(base.match(/(\S)*(name|title)(\S)*/i)){
			 	if(!val){ val = this.getText(subelem) }
				title = val;
				if(val && typeof title!="undefined" && title.length > this.maxtitlewidth){
					this.maxtitlewidth = title.length;
					}
				this.currdeschead="";
				}
			else {
				 if(val && val.match(/(\S)+/)){		
					if (val.match(/^http:\/\/|^https:\/\//i)) {
        	    				val = '<a target="_blank" " href="' + val + '">[go]</a>';
      		    				}
					else {
						if(!title || title==""){
							title = val;	
							if(val && typeof title!="undefined" && title.length > this.maxtitlewidth){
								this.maxtitlewidth = title.length;
								}
							}
						}
				
					}
			   if(val && val !="null" && val!='  ' && val!= ' ' && (val.match(/(\s|\t|\n)*/)!=true)) { 
				if(this.currdeschead != ''){ d += '<br />';}
				d += this.currdeschead + ""+val+""; this.currdeschead = ""; 
			   	}
			
				if(subelem.childNodes.length){
		 			var con = this.makeDescription(subelem, title, depth+1);
					if(con){
						d += con.desc;
						if(typeof con.title!="undefined" && con.title){
						 	title = con.title;
							if(title.length > this.maxtitlewidth){
								this.maxtitlewidth = title.length + depth;
								}
							}
						}
					}
				}

			}
		
		ln++;
		}
	var dc = {};
	dc.desc = d;
	dc.title = title;
	return dc;
	};

GeoXml.prototype.randomColor = function(){ 
	var color="#";
	for (var i=0;i<6;i++){
		var idx = parseInt(Math.random()*16,10)+1;
		color += idx.toString(16);
		}
	return (color.substring(0,7));
	//return color;
	};

GeoXml.prototype.handleGeomark = function (mark, idx, trans) {
     var that = this;
     var desc, title, name, style;
     title = "";
     desc = "";
     var styleid = 0;
     var lat, lon;
     var visible = true;
     if(this.hideall){visible = false;}
     var fill = true;
     var outline = true;
     var width, color, opacity, fillOpacity, fillColor;
     var cor = [];
     var node, nv, cm;
	var coords = "";
	var poslist=[];
	var point_count =0;
	var box_count=0;
	var line_count=0;
	var poly_count=0;
	var p;
	var points = [];
	var cc, l;
    var pbounds = new google.maps.LatLngBounds();
    var coordset=mark.getElementsByTagName("coordinates");
	if(coordset.length <1){
	    coordset=mark.getElementsByTagName("gml:coordinates");
	    }
	if(coordset.length <1){
	   	coordset = [];
	    	poslist =mark.getElementsByTagName("gml:posList");
		if(poslist.length <1) { poslist = mark.getElementsByTagName("posList"); }
		for(l =0;l<poslist.length;l++){
			coords = " ";
			cor = this.getText(poslist.item(l)).split(' ');
			if(that.isWFS){
			for(cc=0;cc<(cor.length-1);cc++){
					if(cor[cc] && cor[cc]!=" " && !isNaN(parseFloat(cor[cc]))){
						coords += ""+parseFloat(cor[cc])+","+parseFloat(cor[cc+1]);
						coords += " ";
						cc++;
						}
					}
				}
			else {
				for(cc=0;cc<(cor.length-1);cc++){
					if(cor[cc] && cor[cc]!=" " && !isNaN(parseFloat(cor[cc]))){
						coords += ""+parseFloat(cor[cc+1])+","+parseFloat(cor[cc]);
						coords += " ";
						cc++;
						}
					}
				}
			if(coords){
 				if(poslist.item(l).parentNode && (poslist.item(l).parentNode.nodeName == "gml:LineString") ){ line_count++; }
					else { poly_count++; }
				cm = "<coordinates>"+coords+"</coordinates>";
				node = this.parseXML(cm);
				if(coordset.push){ coordset.push(node); }
				}
			}

		var pos = mark.getElementsByTagName("gml:pos");
		if(pos.length <1){ pos = mark.getElementsByTagName("gml:pos"); }
		if(pos.length){
			for(p=0;p<pos.length;p++){
				nv = this.getText(pos.item(p));
				cor = nv.split(" ");
				if(!that.isWFS){
					node = this.parseXML("<coordinates>"+cor[1]+","+cor[0]+"</coordinates>");
					}
				else {
					node = this.parseXML("<coordinates>"+cor[0]+","+cor[1]+"</coordinates>");
					}
				if(coordset.push){ coordset.push(node); }
				}
			}
	    }

	var newcoords = false;
	point_count =0;
	box_count=0;
	line_count=0;
	poly_count=0;
     
	var dc = that.makeDescription(mark,"");
	desc = "<div id='currentwindow' style='overflow:auto;height:"+this.iwheight+"px' >"+dc.desc+"</div> ";
	if(!name && dc.title){
		name = dc.title;
		if(name.length > this.maxtitlewidth){
			this.maxtitlewidth = name.length;
			}
		}
	     
    
     if(newcoords && typeof lat!="undefined"){
		coordset.push(""+lon+","+lat);
		}
    
     var lines = [];
	 var polygonlines = [];
     var point;
     var skiprender;
     var bits;
	  
     for(var c=0;c<coordset.length;c++){
        skiprender = false;
        if (coordset[c].parentNode && (coordset[c].parentNode.nodeName == "gml:Box" || coordset[c].parentNode.nodeName == "gml:Envelope")) {
            skiprender = true;
            }
       
       coords = this.getText(coordset[c]); 
       coords += " ";
       coords=coords.replace(/\s+/g," "); 
          // tidy the whitespace
       coords=coords.replace(/^ /,"");    
          // remove possible leading whitespace
       coords=coords.replace(/, /,",");   
          // tidy the commas
       var path = coords.split(" ");
          // Is this a polyline/polygon?
          
     if (path.length == 1 || path[1] =="") {
            bits = path[0].split(",");
            point = new google.maps.LatLng(parseFloat(bits[1])/trans.ys-trans.y,parseFloat(bits[0])/trans.xs-trans.x);
            that.bounds.extend(point);
            // Does the user have their own createmarker function?
	    if(!skiprender){
		    if(typeof name == "undefined"){ 
				name = GeoXml.stripHTML(dc.desc);
				desc=''; 
				}
			if (name == desc) { desc = ""; }
        	    if (!!that.opts.createmarker) {
          		    that.opts.createmarker(point, name, desc, styleid, idx, null, visible);
        		    } 
		    else {
          		    that.createMarker(point, name, desc, styleid, idx, null, visible);
        		    }
		    }
	    }
      else {
        // Build the list of points
       	for (p=0; p<path.length-1; p++) {
         	 bits = path[p].split(",");
         	 point = new google.maps.LatLng(parseFloat(bits[1])/trans.ys-trans.y,parseFloat(bits[0])/trans.xs-trans.x);
         	 points.push(point);
         	 pbounds.extend(point);
         	 }
	 	that.bounds.extend(pbounds.getNorthEast());
	 	that.bounds.extend(pbounds.getSouthWest());
		if(!skiprender) { lines.push(points); }
	     }
	}
 	if(!lines || lines.length <1) { return; }
        var linestring=mark.getElementsByTagName("LineString");
	    if(linestring.length <1){
		linestring=mark.getElementsByTagName("gml:LineString");
		}
        if (linestring.length || line_count>0) {
          // its a polyline grab the info from the style
          if (!!style) {
            width = style.strokeWeight; 
            color = style.strokeColor; 
            opacity = style.strokeOpacity; 
          } else {
            width = this.style.width;
            color = this.style.color;
            opacity = this.style.opacity;
          }
          // Does the user have their own createpolyline function?
	  if(typeof name == "undefined"){ name = GeoXml.stripHTML(dc.desc); }
	  if (name == desc) { desc = ""; }
          if (!!that.opts.createpolyline) {
            that.opts.createpolyline(lines,color,width,opacity,pbounds,name,desc,idx,visible);
          } else {
            that.createPolyline(lines,color,width,opacity,pbounds,name,desc,idx,visible);
          }
        }
        var polygons=mark.getElementsByTagName("Polygon");
	if(polygons.length <1){
		polygons=mark.getElementsByTagName("gml:Polygon");
		}

        if (polygons.length || poly_count>0) {
          // its a polygon grab the info from the style
          if (!!style) {
            width = style.strokeWeight; 
            color = style.strokeColor; 
            opacity = style.strokeOpacity; 
            fillOpacity = style.fillOpacity; 
            fillColor = style.fillColor; 
            fill = style.fill;
			outline = style.outline;
          } 
	fillColor = this.randomColor();
	color = this.randomColor();
	fill = 1;
	outline = 1;
	//alert("found Polygon");
	if(typeof name == "undefined"){ name = GeoXml.stripHTML(desc); desc = "" }
	if (name == desc) { desc = ""; }
 	if (!!that.opts.createpolygon) {
            that.opts.createpolygon(lines,color,width,opacity,fillColor,fillOpacity,pbounds,name,desc,idx,visible,fill,outline);
          } else {
            that.createPolygon(lines,color,width,opacity,fillColor,fillOpacity,pbounds,name,desc,idx,visible,fill,outline);
          }
      }  
    };
	

GeoXml.prototype.handlePlacemark = function(mark, idx, depth, fullstyle) {
		var mgeoms = mark.getElementsByTagName("MultiGeometry");
		if(mgeoms.length < 1){
			this.handlePlacemarkGeometry(mark,mark,idx,depth,fullstyle);
			}
		else {
			var p;
			var pts = mgeoms[0].getElementsByTagName("Point");
			for (p=0;p<pts.length; p++){
				this.handlePlacemarkGeometry(mark,pts[p],idx,depth,fullstyle);
				}
			var lines = mgeoms[0].getElementsByTagName("LineString");
			for (p=0;p<lines.length; p++){
				this.handlePlacemarkGeometry(mark,lines[p],idx,depth,fullstyle);
				}
			var polygons = mgeoms[0].getElementsByTagName("Polygon");
			for (p=0;p<polygons.length; p++){
				this.handlePlacemarkGeometry(mark,polygons[p],idx,depth,fullstyle);
				}
			}		
		};
	
GeoXml.prototype.handlePlacemarkGeometry = function(mark, geom, idx, depth, fullstyle) {
        var that = this;
        var desc, title, name, style;
        title = "";
        desc = "";
        var styleid = 0;
        var lat, lon;
        var visible = true;
        if (this.hideall) { visible = false; }
        var newcoords = false;
        var outline;
        var opacity;
        var fillcolor;
        var fillOpacity;
        var color;
        var width;
        var pbounds;
        var fill;
        var points = [];
        var lines = [];
        var bits = [];
        var point;
        var cor, node, cm, nv;
        var l, pos, p, j, k, cc;
        var kml_id = mark.getAttribute("id");
        var point_count = 0;
        var box_count = 0;
        var line_count = 0;
        var poly_count = 0;
        var coords = "";
        var markerurl = "";
        var snippet = "";
        l = mark.getAttribute("lat");
        if (typeof l != "undefined") { lat = l; }
        l = mark.getAttribute("lon");
        if (typeof l != "undefined") {
            newcoords = true;
            lon = l;
			}
        l = 0;
        var coordset = geom.getElementsByTagName("coordinates");
        if (coordset.length < 1) {
            coordset = geom.getElementsByTagName("gml:coordinates");
			}
        if (coordset.length < 1) {
            coordset = [];
            var poslist = geom.getElementsByTagName("gml:posList");
            if (!poslist.length) {
                poslist = geom.getElementsByTagName("posList");
            }
            for (l = 0; l < poslist.length; l++) {
                coords = " ";
                var plitem = this.getText(poslist.item(l)) + " ";
                plitem = plitem.replace(/(\s)+/g, ' ');
                cor = plitem.split(' ');
                if (that.isWFS) {
                    for (cc = 0; cc < (cor.length - 1); cc++) {
                        if (!isNaN(parseFloat(cor[cc])) && !isNaN(parseFloat(cor[cc + 1]))) {
                            coords += "" + parseFloat(cor[cc]) + "," + parseFloat(cor[cc + 1]);
                            coords += " ";
                            cc++;
                        }
                    }
                }
                else {
                    for (cc = 0; cc < (cor.length - 1); cc++) {
                        if (!isNaN(parseFloat(cor[cc])) && !isNaN(parseFloat(cor[cc + 1]))) {
                            coords += "" + parseFloat(cor[cc + 1]) + "," + parseFloat(cor[cc]);
                            coords += " ";
                            cc++;
                        }
                    }
                }
                if (coords) {
                    if (poslist.item(l).parentNode && (poslist.item(l).parentNode.nodeName == "gml:LineString")) { line_count++; }
                    else { poly_count++; }
                    cm = "<coordinates>" + coords + "</coordinates>";
                    node = this.parseXML(cm);
                    if (coordset.push) { coordset.push(node); }
                }
            }

            pos = geom.getElementsByTagName("gml:pos");
            if (pos.length < 1) { pos = geom.getElementsByTagName("gml:pos"); }
            if (pos.length) {
                for (p = 0; p < pos.length; p++) {
                    nv = this.getText(pos.item(p)) + " ";
                    cor = nv.split(' ');
                    if (!that.isWFS) {
                        node = this.parseXML("<coordinates>" + cor[1] + "," + cor[0] + "</coordinates>");
                    }
                    else {
                        node = this.parseXML("<coordinates>" + cor[0] + "," + cor[1] + "</coordinates>");
                    }
                    if (coordset.push) { coordset.push(node); }
                }
            }
        }




        for (var ln = 0; ln < mark.childNodes.length; ln++) {
            var nn = mark.childNodes.item(ln).nodeName;
            nv = this.getText(mark.childNodes.item(ln));
            var ns = nn.split(":");
            var base;
            if (ns.length > 1) { base = ns[1].toLowerCase(); }
            else { base = ns[0].toLowerCase(); }

            var processme = false;
            switch (base) {
                case "name":
                    name = nv;
                    if (name.length + depth > this.maxtitlewidth) { this.maxtitlewidth = name.length + depth; }
                    break;
                case "title":
                    title = nv;
                    if (title.length + depth > this.maxtitlewidth) { this.maxtitlewidth = title.length + depth; }
                    break;
                case "desc":
                case "description":
                    desc = GeoXml.getDescription(mark.childNodes.item(ln));
                    if (!desc) { desc = nv; }
					var srcs = desc.match(/src=\"(.*)\"/i);
			//alert("matching srcs : "+srcs.index + " "+srcs.input);
					if(srcs){
						for (var sr=1;sr<srcs.length;sr++){
							if(srcs[sr].match(/^http/)){
								}
							else {
								if(this.url.match(/^http/)){
									//remove all but last slash of url
									var slash = this.url.lastIndexOf("/");
									if(slash != -1){
										newsrc = this.url.substring(0,slash)+"/" + srcs[sr];
										desc = desc.replace(srcs[sr],newsrc);
										}
									//alert(desc);
									}
								else {
									//compute directory of html add relative path of kml and relative path of src.
									var slash = this.url.lastIndexOf("/");
									if(slash != -1){
										newsrc = this.url.substring(0,slash)+"/" + srcs[sr];
										desc = desc.replace(srcs[sr],newsrc);
										}
									//var path = window.location.href+" "+this.url+" "+srcs[sr];
									//alert(path +"\n"+desc);
									}
								}
							}
						}
                    if (that.opts.preloadHTML && desc && desc.match(/<(\s)*img/i)) {
                        var preload = document.createElement("span");
                        preload.style.visibility = "visible";
                        preload.style.position = "absolute";
                        preload.style.left = "-12000px";
                        preload.style.top = "-12000px";
                        preload.style.zIndex = this.overlayman.markers.length;
						preload.onload = function(){ preload.style.visibility = "hidden"; }
                        document.body.appendChild(preload);
                        preload.innerHTML = desc;
						}
                    if (desc.match(/^http:\/\//i)) {
                        var flink = desc.split(/(\s)+/);
                        if (flink.length > 1) {
                            desc = "<a href=\"" + flink[0] + "\">" + flink[0] + "</a>";
                            for (var i = 1; i < flink.length; i++) {
                                desc += flink[i];
                            }
                        }
                        else {
                            desc = "<a href=\"" + desc + "\">" + desc + "</a>";
                        }
                    }
                    break;
                case "visibility":
                    if (nv == "0") { visible = false; }
                    break;
                case "Snippet":
                case "snippet":
                    snippet = nv;
                    break;
                case "href":
                case "link":
                    if (nv) {
                        desc += "<p><a target='_blank' href='" + nv + "'>link</a></p>";
                        markerurl = nv;
                    }
                    else {
                        var href = mark.childNodes.item(ln).getAttribute("href");
                        if (href) {
                            var imtype = mark.childNodes.item(ln).getAttribute("type");
                            if (imtype && imtype.match(/image/)) {
                                desc += "<img style=\"width:256px\" src='" + href + "' />";
                            }
                            markerurl = href;
                        }
                    }
                    break;
                case "author":
                    desc += "<p><b>author:</b>" + nv + "</p>";
                    break;
                case "time":
                    desc += "<p><b>time:</b>" + nv + "</p>";
                    break;
                case "lat":
                    lat = nv;
                    break;
                case "long":
                    lon = nv;
                    newcoords = true;
                    break;
                case "box":
                    box_count++; processme = true; break;
                case "styleurl":
                    styleid = nv;
					style = that.styles[styleid];
                    break;
                case "stylemap":
                    var found = false;
                    node = mark.childNodes.item(ln);
                    for (j = 0; (j < node.childNodes.length && !found); j++) {
                        var pair = node.childNodes[j];
                        for (k = 0; (k < pair.childNodes.length && !found); k++) {
                            var pn = pair.childNodes[k].nodeName;
                            if (pn == "Style") {
                                style = this.handleStyle(pair.childNodes[k],null,style);
                                found = true;
                            }
                        }
                    }
					
                    break;
                case "Style":
                case "style":
                    styleid = null;
                    style = this.handleStyle(mark.childNodes.item(ln),null,style);
                    break;
            }
            if (processme) {
                cor = nv.split(' ');
                coords = "";
                for (cc = 0; cc < (cor.length - 1); cc++) {
                    if (!isNaN(parseFloat(cor[cc])) && !isNaN(parseFloat(cor[cc + 1]))) {
                        coords += "" + parseFloat(cor[cc + 1]) + "," + parseFloat(cor[cc]);
                        coords += " ";
                        cc++;
                    }
                }
                if (coords != "") {
                    node = this.parseXML("<coordinates>" + coords + "</coordinates>");
                    if (coordset.push) { coordset.push(node); }
                }
            }

        }

        if (!name && title) { name = title; }

        if (fullstyle) {
			//alert("overriding style with" +fullstyle.url);
            style = fullstyle;
			}
	  var iwheightstr;
		if (this.iwheight != 0){
			iwheightstr = "height:"+this.iwheight+"px";
			}
        if (typeof desc == "undefined" || !desc || this.opts.makedescription) {
            var dc = that.makeDescription(mark, "");
			
            desc = "<div id='currentpopup' style='overflow:auto;" + iwheightstr + "' >" + dc.desc + "</div> ";
            if (!name && dc.title) {
                name = dc.title;
                if ((name.length + depth) > this.maxtitlewidth) {
                    this.maxtitlewidth = name.length + depth;
                }
            }
        }
		else {
			if(this.iwheight){
				desc = "<div id='currentpopup' style='overflow:auto;" + iwheightstr+"' >" + desc + "</div> ";
				}
			}

 
		if (coordset.length == 0 && typeof lat!= "undefined") {
			point = new google.maps.LatLng(lat, lon);
			this.overlayman.folderBounds[idx].extend(point);
						// Does the user have their own createmarker function?
			if (!skiprender) {
				if (typeof name == "undefined") { name = GeoXml.stripHTML(desc); desc = "";}
				if (name == desc) { desc = ""; }
				if (!!that.opts.createmarker) {
					that.opts.createmarker(point, name, desc, styleid, idx, style, visible, kml_id, markerurl, snippet);
					}
				else {
					that.createMarker(point, name, desc, styleid, idx, style, visible, kml_id, markerurl, snippet);
					}
				}
			}
		else {
			for (var c = 0; c < coordset.length; c++) {
				var skiprender = false;
				if (coordset[c].parentNode && (coordset[c].parentNode.nodeName.match(/^(gml:Box|gml:Envelope)/i))) {
					skiprender = true;
					}
				coords = this.getText(coordset[c]);
				coords += " ";
				coords = coords.replace(/(\s)+/g, " ");
				// tidy the whitespace
				coords = coords.replace(/^ /, "");
				// remove possible leading whitespace
				//coords=coords +" "; 
				////ensure trailing space
				coords = coords.replace(/, /, ",");
				// tidy the commas
				var path = coords.split(" ");
				// Is this a polyline/polygon?

				if (path.length == 1 || path[1] == "") {
						bits = path[0].split(",");
						point = new google.maps.LatLng(parseFloat(bits[1]), parseFloat(bits[0]));
						this.overlayman.folderBounds[idx].extend(point);
						// Does the user have their own createmarker function?
						if (!skiprender) {
							if (typeof name == "undefined") { name = GeoXml.stripHTML(desc); desc = ""; }
							if (name == desc) { desc = ""; }
							if (!!that.opts.createmarker) {
								that.opts.createmarker(point, name, desc, styleid, idx, style, visible, kml_id, markerurl, snippet);
							}
							else {
								that.createMarker(point, name, desc, styleid, idx, style, visible, kml_id, markerurl, snippet);
							}
						}
					}
				else {
					// Build the list of points
					points = [];
					pbounds = new google.maps.LatLngBounds();
					for (p = 0; p < path.length - 1; p++) {
						bits = path[p].split(",");
						point = new google.maps.LatLng(parseFloat(bits[1]), parseFloat(bits[0]));
						points.push(point);
						pbounds.extend(point);
						}
					this.overlayman.folderBounds[idx].extend(pbounds.getSouthWest());
					this.overlayman.folderBounds[idx].extend(pbounds.getNorthEast());
					this.bounds.extend(pbounds.getSouthWest());
					this.bounds.extend(pbounds.getNorthEast());
					if (!skiprender) { lines.push(points); }
				}
			}
		}
        if (!lines || lines.length < 1) { return; }
		var nn = coordset[0].parentNode.nodeName;
        if (nn.match(/^(LineString)/i)||nn.match(/^(gml:LineString)/i)) {
            // its a polyline grab the info from the style
            if (!!style) {
                width = style.strokeWeight;
                color = style.strokeColor;
                opacity = style.strokeOpacity;
            } else {
                width = this.style.width;
                color = this.style.color;
                opacity = this.style.opacity;
            }
            // Does the user have their own createmarker function?
            if (typeof name == "undefined") { name = GeoXml.stripHTML(desc); desc = ""; }
			if (name == desc) { desc = ""; }
            if (!!that.opts.createpolyline) {
                that.opts.createpolyline(lines, color, width, opacity, pbounds, name, desc, idx, visible, kml_id);
            } else {
                that.createPolyline(lines, color, width, opacity, pbounds, name, desc, idx, visible, kml_id);
            }
        }
	//	alert(coordset[0].parentNode.nodeName);
        if (nn.match(/^(LinearRing)/i) || nn.match(/^(gml:LinearRing)/i)) {
            // its a polygon grab the info from the style
            if (!!style) {
                width = style.strokeWeight;
                color = style.strokeColor;
                opacity = style.strokeOpacity;
                fillOpacity = style.fillOpacity;
                fillcolor = style.fillColor;
                fill = style.fill;
                outline = style.outline;
				}	
            if (typeof fill == "undefined") { fill = 1; }
            if (typeof color == "undefined") { color = this.style.color; }
            if (typeof fillcolor == "undefined") { fillcolor = this.randomColor(); }
            if (typeof name == "undefined") { name = GeoXml.stripHTML(desc); desc = ""; }
			if (name == desc) { desc = ""; }
            if (!!that.opts.createpolygon) {
                that.opts.createpolygon(lines, color, width, opacity, fillcolor, fillOpacity, pbounds, name, desc, idx, visible, fill, outline, kml_id);
            } else {
                that.createPolygon(lines, color, width, opacity, fillcolor, fillOpacity, pbounds, name, desc, idx, visible, fill, outline, kml_id);
            }
        }
    };
GeoXml.prototype.makeIcon = function(currstyle, href, myscale, hotspot){
	var scale = 1;
	var tempstyle;
	var anchorscale = {x:0.5,y:1};
	if(hotspot){
		var xu = hotspot.getAttribute("xunits");
		var x = hotspot.getAttribute("x");
		var thtwox = 32; 
		var thtwoy = 32;
		if(this.opts.baseicon) {
			thtwox = this.opts.baseicon.size.width;
			thtwoy = this.opts.baseicon.size.height;
			}
		if(xu == "fraction"){
			anchorscale.x = parseFloat(x);
			}
		else {
			anchorscale.x = parseFloat(x)/thtwox;
			}
		var yu = hotspot.getAttribute("yunits");
		var y = hotspot.getAttribute("y");
		if(yu == "fraction"){
			anchorscale.y = 1 - parseFloat(y);
			}
		else {
			anchorscale.y = 1 - parseFloat(y)/thtwoy;
			}		
		}
	 
	if(typeof myscale == "number"){
		scale = myscale;
		}
	if (!!href) { }
	else {
		if(!!currstyle){
			if(!!currstyle.url){
				href = currstyle.url; 
				scale = currstyle.scale;
				}
			}
		else {
			href = "http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png";
			tempstyle = new google.maps.MarkerImage(href,new google.maps.Size(16*scale,16*scale));
			tempstyle.origin = new google.maps.Point(0*scale,0*scale);
			tempstyle.anchor = new google.maps.Point(16*scale*anchorscale.x,16*scale*anchorscale.y);
			}
		}
	if (!!href) {
		  if (!!this.opts.baseicon) {
		  var bicon = this.opts.baseicon;
		   tempstyle = new google.maps.MarkerImage(href,this.opts.baseicon.size);
		   tempstyle.origin = this.opts.baseicon.origin;
		   tempstyle.anchor = new google.maps.Point(this.opts.baseicon.size.width*scale*anchorscale.x,this.opts.baseicon.size.height*scale*anchorscale.y);
		   if(this.opts.baseicon.scaledSize){
				tempstyle.scaledSize = this.opts.baseicon.scaledSize;
				}
			else {
				var w = bicon.size.width*scale;
				var h = bicon.size.height*scale;
				tempstyle.scaledSize = new google.maps.Size(w,h);
				}
		   tempstyle.url = href;
		  } else {
			tempstyle = new google.maps.MarkerImage(href,new google.maps.Size(32,32),new google.maps.Point(0,0),new google.maps.Point(32*scale*anchorscale.x,32*scale*anchorscale.y),new google.maps.Size(32*scale,32*scale));
			if (this.opts.printgif) {
			  var bits = href.split("/");
			  var gif = bits[bits.length-1];
			  gif = this.opts.printgifpath + gif.replace(/.png/i,".gif");
			  tempstyle.printImage = gif;
			  tempstyle.mozPrintImage = gif;
				}
			if (!!this.opts.noshadow) { //shadow image code probably needs removed 
			  tempstyle.shadow="";
			} else {
			  // Try to guess the shadow image
			  if (href.indexOf("/red.png")>-1 
			   || href.indexOf("/blue.png")>-1 
			   || href.indexOf("/green.png")>-1 
			   || href.indexOf("/yellow.png")>-1 
			   || href.indexOf("/lightblue.png")>-1 
			   || href.indexOf("/purple.png")>-1
		|| href.indexOf("/orange.png")>-1 
			   || href.indexOf("/pink.png")>-1 
		|| href.indexOf("-dot.png")>-1 ) {
				  tempstyle.shadow="http://maps.google.com/mapfiles/ms/icons/msmarker.shadow.png";
			  }
			  else if (href.indexOf("-pushpin.png")>-1  
		|| href.indexOf("/pause.png")>-1 
		|| href.indexOf("/go.png")>-1    
		|| href.indexOf("/stop.png")>-1     ) {
				  tempstyle.shadow="http://maps.google.com/mapfiles/ms/icons/pushpin_shadow.png";
			  }
			  else {
				var shadow = href.replace(".png",".shadow.png");
		if(shadow.indexOf(".jpg")){ shadow =""; }
				tempstyle.shadow=shadow;
			  }
			}
		  }
		}
	
	if (this.opts.noshadow){
		tempstyle.shadow ="";
		}
	return tempstyle;
	};
	
GeoXml.prototype.handleStyle = function(style,sid,currstyle){
	 var that = this;
      var icons=style.getElementsByTagName("IconStyle");
      var tempstyle,opacity;
      var aa,bb,gg,rr;
      var fill,href,color,colormode, outline;
	  fill = 1;
	  outline = 1;
	  myscale = 1;
	  var strid = "#";
	  if(sid){
		strid = "#"+sid;
		}
	  //tempstyle.url = currstyle.url;
	  
      if (icons.length > 0) {
        href=this.getText(icons[0].getElementsByTagName("href")[0]);
		if(currstyle){
			href = currstyle.url;
			}
		if (href) {
			var scale = parseFloat(this.getText(icons[0].getElementsByTagName("scale")[0]),10);
			if(scale){
				myscale = scale;
				}
			var hs = icons[0].getElementsByTagName("hotSpot");
			tempstyle = this.makeIcon(currstyle,href,myscale,hs[0]);
			tempstyle.scale = myscale;
			that.styles[strid] = tempstyle;
			}
      	}
		
	  var labelstyles =style.getElementsByTagName("LabelStyle");
	  if (labelstyles.length > 0){
		var scale = parseFloat(this.getText(labelstyles[0].getElementsByTagName("scale")[0]),10);
		color = this.getText(labelstyles[0].getElementsByTagName("color")[0]);
        aa = color.substr(0,2);
        bb = color.substr(2,2);
        gg = color.substr(4,2);
        rr = color.substr(6,2);
        color = "#" + rr + gg + bb;
        opacity = parseInt(aa,16)/256;
		if(that.opts.overrideOpacity){
			opacity = that.opts.overrideOpacity;
			}
        if (!!!that.styles[strid]) {
          that.styles[strid] = {};
		  }
		tempstyle = that.styles[strid];
        that.styles[strid].textColor = color;
		if (scale == 0) {
			scale = 1;
			}
        that.styles[strid].scale = scale;
		}
      // is it a LineStyle ?
      var linestyles=style.getElementsByTagName("LineStyle");
      if (linestyles.length > 0) {
        var width = parseInt(this.getText(linestyles[0].getElementsByTagName("width")[0]),10);
        if (width < 1) {width = 1;}
        color = this.getText(linestyles[0].getElementsByTagName("color")[0]);
        aa = color.substr(0,2);
        bb = color.substr(2,2);
        gg = color.substr(4,2);
        rr = color.substr(6,2);
        color = "#" + rr + gg + bb;
        opacity = parseInt(aa,16)/256;
		if(that.opts.overrideOpacity){
			opacity = that.opts.overrideOpacity;
			}
        if (!!!that.styles[strid]) {
          that.styles[strid] = {};

        }
        that.styles[strid].strokeColor=color;
        that.styles[strid].strokeWeight=width;
        that.styles[strid].strokeOpacity=opacity;
      }
      // is it a PolyStyle ?
      var polystyles=style.getElementsByTagName("PolyStyle");
      if (polystyles.length > 0) {
       
        
        color = this.getText(polystyles[0].getElementsByTagName("color")[0]);
        colormode = this.getText(polystyles[0].getElementsByTagName("colorMode")[0]);
        if (polystyles[0].getElementsByTagName("fill").length != 0) {
			fill = parseInt(this.getText(polystyles[0].getElementsByTagName("fill")[0]),10);
			}
        if (polystyles[0].getElementsByTagName("outline").length != 0) {
			outline = parseInt(this.getText(polystyles[0].getElementsByTagName("outline")[0]),10);
			}
        aa = color.substr(0,2);
        bb = color.substr(2,2);
        gg = color.substr(4,2);
        rr = color.substr(6,2);
        color = "#" + rr + gg + bb;
        opacity = parseInt(aa,16)/256;
		if(that.opts.overrideOpacity){
			opacity = that.opts.overrideOpacity;
			}

        if (!!!that.styles[strid]) {
          that.styles[strid] = {};
        }
		that.styles[strid].fill = fill;
		that.styles[strid].outline = outline;
		if(colormode != "random") {
			that.styles[strid].fillColor = color;
			}
		else {
			that.styles[strid].colortint = color;
			}
			that.styles[strid].fillOpacity=opacity;
			if (!fill) { that.styles[strid].fillOpacity = 0; }
			if (!outline) { that.styles[strid].strokeOpacity = 0; }
		  }
	  
	tempstyle = that.styles[strid];

	return tempstyle;
};
GeoXml.prototype.processKML = function(node, marks, title, sbid, depth, paren) {  
	var that = this;
	var thismap = this.map;
	var icon;
	var grouptitle;
	var keepopen = this.forcefoldersopen;
	if (node.nodeName == "kml"){ icon = this.docicon; }
        if (node.nodeName == "Document" ){ 
		icon = this.kmlicon;  
		}
	if (node.nodeName == "Folder"){  
		icon = this.foldericon; 
		grouptitle = title; 
		}
	var pm = [];
	var sf = [];
	var desc= "";
	var snippet ="";
	var i;
	var visible = false;
	if(!this.hideall){visible = true; }
	var boundsmodified = false;
        var networklink = false;
	var url;
	var urllist = [];
	var ground = null;
	var opacity = 1.0;
	var wmsbounds;
	var makewms = false;
	var makeground = false;
	var wmslist = [];
	var groundlist = [];
	var mytitle;
	var color;
	var ol;
	var n,ne,sw,se;
	var html; 
	var kml_id = node.getAttribute("id");
//	console.log("parent ="+node.nodeName);
	for (var ln = 0; ln < node.childNodes.length; ln++) {
		var nextn = node.childNodes.item(ln);
		var nn = nextn.nodeName;
		var nv = nextn.nodeValue;
		switch (nn) {
		 	case "name":  
			case "title": 
				title = this.getText(nextn);
				if(title.length + depth > this.maxtitlewidth){ this.maxtitlewidth = title.length+depth;	}
			 	break;
			case "Folder" :
			case "Document" :
				sf.push(nextn); 
				break;
		 	case "GroundOverlay":
				url=this.getText(nextn.getElementsByTagName("href")[0]);
				var north=parseFloat(this.getText(nextn.getElementsByTagName("north")[0]));
				var south=parseFloat(this.getText(nextn.getElementsByTagName("south")[0]));
				var east=parseFloat(this.getText(nextn.getElementsByTagName("east")[0]));
				var west=parseFloat(this.getText(nextn.getElementsByTagName("west")[0]));
				var attr = this.getText(nextn.getElementsByTagName("attribution")[0]);
				sw = new google.maps.LatLng(south,west);
				ne = new google.maps.LatLng(north,east); 
				this.bounds.extend(sw); 
      			this.bounds.extend(ne);
				color=this.getText(nextn.getElementsByTagName("color")[0]);
				opacity = parseInt(color.substring(1,3),16)/256;
				mytitle = this.getText(nextn.getElementsByTagName("name")[0]);
				var arcims = /arcimsproxy/i; 
				if(url.match(arcims)) {
					url += "&bbox="+west+","+south+","+east+","+north+"&response=img";
					wmsbounds = new google.maps.LatLngBounds(sw,ne);
					makewms = true;
					ol = this.makeWMSTileLayer(url, visible, mytitle, opacity, attr, title, wmsbounds);
					if(ol) {
						ol.bounds = wmsbounds;
						ol.title = mytitle;
						ol.opacity = opacity;
						ol.visible = visible;
						ol.url = url;
						if(!this.quiet){ 
							this.mb.showMess("Adding Tiled ArcIms Overlay "+title,1000); 
							}
						wmslist.push(ol);
						}
					}
				else { 
					var rs = /request=getmap/i;    
					if(url.match(rs)){
						url += "&bbox="+west+","+south+","+east+","+north;
						wmsbounds = new google.maps.LatLngBounds(sw,ne);
						makewms = true;
						ol = this.makeWMSTileLayer(url, visible, mytitle, opacity, attr, title, wmsbounds);
						if(ol){ 
							ol.bounds = wmsbounds;
							ol.title = mytitle;
							ol.opacity = opacity;
							ol.visible = visible;
							ol.url = url;
							if(!this.quiet){ this.mb.showMess("Adding Tiled WMS Overlay "+title,1000);}
							wmslist.push(ol);
							}	
						}
					else {
						wmsbounds = new google.maps.LatLngBounds(sw,ne);
						ground = new google.maps.GroundOverlay(url, wmsbounds);
						ground.url = url;
						ground.title = mytitle;
						ground.visible = visible;
						ground.bounds = wmsbounds;
						ground.getBounds = function(){ return this.bounds;};
						boundsmodified = true;
						makeground = true;
						if(!this.quiet){ this.mb.showMess("Adding GroundOverlay "+title,1000);}
						groundlist.push(ground);
    			 	}
				}
				break;
		 	case "NetworkLink":
			       urllist.push(this.getText(nextn.getElementsByTagName("href")[0]));
				networklink = true;
				break;
			case "description" :
			case "Description":
				desc = GeoXml.getDescription(nextn);
				break;
			case "open":
				if(this.getText(nextn) == "1"){  keepopen = true; }
				if(this.getText(nextn) == "0") { keepopen = this.forcefoldersopen; }
				break;
			case "visibility":
				if(this.getText(nextn) == "0") { visible = false; }
				break;
			case "snippet" :
			case "Snippet" :
				snippet = GeoXml.stripHTML(this.getText(nextn));
				snippet = snippet.replace(/\n/g,'');
				break;
			default:
				for(var k=0;k<marks.length;k++){
					//console.log(marks[k]);
					if(nn == marks[k]){
						//console.log("adding one" + nn)
						pm.push(nextn);
						break;
						}					
					}
				}
			}

  
	var folderid;

	var idx = this.overlayman.folders.length;
	var me = paren;
	if(sf.length >1 || pm.length || ground || makewms ){
        this.overlayman.folders.push([]);
		this.overlayman.subfolders.push([]);
    	this.overlayman.folderhtml.push([]);
    	this.overlayman.folderhtmlast.push(0);
		this.overlayman.folderBounds.push(new google.maps.LatLngBounds());
		//console.log("placemarks found "+pm.length);
  		this.kml.push(new KMLObj(title, desc, false, idx));
		me = this.kml.length - 1;
		var suppressfolder = false; //(pm.length == 2)
		folderid = this.createFolder(idx, title, sbid, icon, desc, snippet, true, visible, suppressfolder );
		} 
	else {
		folderid = sbid;
		}


	if (node.nodeName == "Folder" || node.nodeName == "Document"){  
		this.kml[me].open = keepopen; 
		this.kml[me].folderid = folderid;
		}

	if(ground || makewms){
		this.kml[this.kml.length-1].visibility = visible;
		this.kml[this.kml.length-1].groundOverlays.push({"url":url,"bounds":wmsbounds});
		}
	 

	if(networklink){
		var re = /&amp;/g;
		for (x=0;x<urllist.length;x++) {
			url = urllist[x];
			url = url.replace(re,"&");
			var nl = /\n/g;
			url = url.replace(nl,"");
			var qu = /'/g;
			title = title.replace(qu,"&#39;");
			this.progress++;	
	//		if(!top.standalone){
	//			if(typeof this.proxy!="undefined") { url = this.proxy + escape(url); } 
	//			}
			var comm = this.myvar +".loadXMLUrl('"+url+"','"+title+"',null,null,'"+sbid+"');";
			setTimeout(comm,1000);
		}
	}

	if(makewms && wmslist.length){
		for(var wo=0;wo<wmslist.length;wo++) {
			var ol = wmslist[wo];
			var blob = "";
			if (this.basesidebar) {
    				var n = this.overlayman.markers.length;
				if(!this.nolegend){
					var myurl = ol.url.replace(/height=(\d)+/i,"height=100");
					myurl = myurl.replace(/width=(\d)+/i,"width=100");
					blob = '<img src="'+myurl+'" style="width:100px" />';
					}
				}
			if(this.sidebarsnippet && snippet==""){
				snippet = GeoXml.stripHTML(desc);
				desc2 = desc2.substring(0,40);}
   			parm =  this.myvar+"$$$" +ol.title + "$$$tiledoverlay$$$" + n +"$$$" + blob + "$$$" +ol.visible+"$$$"+(this.baseLayers.length-1)+"$$$"+snippet; 
			var html = ol.desc;
			var thismap = this.map; 
			google.maps.event.addListener(ol,"zoomto", function() { 	
				thismap.fitBounds(this.getBounds());

				});	
	 		this.overlayman.addMarker(ol, title, idx, parm, true, true); 
			}
		}
	
	if(makeground && groundlist.length){
		for(var gro=0;gro<groundlist.length;gro++) {
			if (this.basesidebar) {
				var n = this.overlayman.markers.length;
				var blob = '<span style="background-color:black;border:2px solid brown;">&nbsp;&nbsp;&nbsp;&nbsp;</span> ';
				if(this.sidebarsnippet && snippet==""){
					snippet = GeoXml.stripHTML(desc);
					desc2 = desc2.substring(0,40);}
				parm =  this.myvar+"$$$" +groundlist[gro].title + "$$$polygon$$$" + n +"$$$" + blob + "$$$" +groundlist[gro].visible+"$$$null$$$"+snippet; 
			 
				var html = groundlist[gro].desc;
				var thismap = this.map;
				google.maps.event.addListener(groundlist[gro],"zoomto", function() { 
							this.map.fitBounds(groundlist[gro].getBounds());
							});
				this.overlayman.folderBounds[idx].extend(groundlist[gro].getBounds().getSouthWest());
				this.overlayman.folderBounds[idx].extend(groundlist[gro].getBounds().getNorthEast());
				boundsmodified = true;
				this.overlayman.addMarker(groundlist[gro],title,idx, parm, visible);
				}
			groundlist[gro].setMap(this.map);
		}
	}

	for(i=0;i<pm.length;i++) {
		this.handlePlacemark(pm[i], idx, depth+1);
		}
	var fc = 0;

	for(i=0;i<sf.length;i++) {
	 	 var fid = this.processKML(sf[i], marks, title, folderid, depth+1, me);
		 if(typeof fid =="number" && fid != idx){
			var sub = this.overlayman.folderBounds[fid];
			if(!sub) { 
			       this.overlayman.folderBounds[fid] = new google.maps.LatLngBounds(); 
				}
			 else {
			        var sw = this.overlayman.folderBounds[fid].getSouthWest();
			        var ne = this.overlayman.folderBounds[fid].getNorthEast();
			        this.overlayman.folderBounds[idx].extend(sw);
			        this.overlayman.folderBounds[idx].extend(ne);
			        }
			this.overlayman.subfolders[idx].push(fid);
		    if(fid!=idx){ this.kml[idx].folders.push(fid); }
			fc++;
			}
		}
	 
	if(fc || pm.length || boundsmodified){
		this.bounds.extend(this.overlayman.folderBounds[idx].getSouthWest());
		this.bounds.extend(this.overlayman.folderBounds[idx].getNorthEast());
		}

	if(sf.length == 0 && pm.length == 0){
		this.ParseURL();
		}
	return idx;
	};


GeoXml.prototype.processGPX = function(node,title,sbid,depth) {
	var icon;
	if(node.nodeName == "gpx" ){ icon = this.gmlicon; }
	if(node.nodeName == "rte" || node.nodeName == "trk" || node.nodeName == "trkseg" ){ icon = this.foldericon; }
	var pm = [];
	var sf = [];
	var desc= "";
	var snip ="";
	var i, lon, lat, l;
	var open = this.forcefoldersopen;
	this.showLabels = false;
	var coords = "";
	var visible = true;
	for (var ln = 0; ln < node.childNodes.length; ln++) {
		var nextn = node.childNodes.item(ln);
		var nn = nextn.nodeName;
		if(nn == "name" || nn == "title"){
			title = this.getText(nextn);
			if(title.length + depth > this.maxtitlewidth){
				this.maxtitlewidth = title.length+depth;	
				}
			}
		if(nn == "rte"){
			sf.push(nextn); 
			}
		if(nn == "trk"){
			sf.push(nextn); 
			}
		if(nn == "trkseg"){
			sf.push(nextn); 
			}

		if(nn == "trkpt"){
			pm.push(nextn);
			l = nextn.getAttribute("lat");
     			if(typeof l!="undefined"){lat = l;}
     			l = nextn.getAttribute("lon");
     			if(typeof l!="undefined"){
				lon = l;
				coords += lon+","+lat+" ";
				}
			}

		if(nn == "rtept"){
			pm.push(nextn);
			l = nextn.getAttribute("lat");
     			if(typeof l!="undefined"){lat = l;}
     			l = nextn.getAttribute("lon");
     			if(typeof l!="undefined"){
				lon = l;
				coords += lon+","+lat+" ";
				}
			}
		if(nn == "wpt"){
			pm.push(nextn);
			}
		if(nn == "description" ||  nn == "desc"){
			desc = this.getText(nextn);
			}

		}

	if(coords.length){
		var nc = "<?xml version=\"1.0\"?><Placemark><name>"+title+"</name><description>"+desc+"</description><LineString><coordinates>"+coords+"</coordinates></LineString></Placemark>";
		var pathnode = this.parseXML(nc).documentElement;
		pm.push(pathnode);
		}

	var folderid;
	var idx = this.overlayman.folders.length;
	if(pm.length || node.nodeName == "gpx"){
       	this.overlayman.folders.push([]);
		this.overlayman.subfolders.push([]);
    	this.overlayman.folderhtml.push([]);
    	this.overlayman.folderhtmlast.push(0);
		this.overlayman.folderBounds.push(new google.maps.LatLngBounds());
		this.kml.push(new KMLObj(title,desc,open,idx));
		folderid = this.createFolder(idx, title, sbid, icon, desc, snip, true, visible,(pm.length ==1) );
		} 
 	 else {
		folderid = sbid;
		}
		

	for(i=0;i<pm.length;i++) {
		this.handlePlacemark(pm[i], idx, depth+1);
		}
	
	for(i=0;i<sf.length;i++) {
	 	var fid = this.processGPX(sf[i], title, folderid, depth+1);
		this.overlayman.subfolders[idx].push(fid);
		this.overlayman.folderBounds[idx].extend(this.overlayman.folderBounds[fid].getSouthWest());
		this.overlayman.folderBounds[idx].extend(this.overlayman.folderBounds[fid].getNorthEast());
		}

	if(this.overlayman.folderBounds[idx]){
		this.bounds.extend(this.overlayman.folderBounds[idx].getSouthWest());
		this.bounds.extend(this.overlayman.folderBounds[idx].getNorthEast());
		}

	return idx;
	};

GeoXml.prototype.ParseURL = function (){
		var query = topwin.location.search.substring(1);
		var pairs = query.split("&");
		var marks = this.overlayman.markers;
      		for (var i=0; i<pairs.length; i++) {
		var pos = pairs[i].indexOf("=");
		var argname = pairs[i].substring(0,pos).toLowerCase();
		var val = unescape(pairs[i].substring(pos+1));
		var m = 0;
		var nae;
		if (!val) {
			if (this.overlayman.paren.openbyid) {
				val = this.overlayman.paren.openbyid;
				argname = "openbyid";
			}
			if (this.overlayman.paren.openbyname) {
				val = this.overlayman.paren.openbyname;
				argname = "openbyname";
			}
		}
	 	if(val){
		switch (argname) {
			case "openbyid" :
				for(m = 0;m < marks.length;m++){
				nae = marks[m].id;
				if(nae == val){
						this.overlayman.markers[m].setVisible(true);
						this.overlayman.markers[m].hidden = false; 
						google.maps.event.trigger(this.overlayman.markers[m],"click");
						break;
						}	
					}	
				break;
			case "kml":
			case "url":
			case "src":
			case "geoxml":
				this.urls.push(val);
				this.parse();
			break;
			case "openbyname" :
				for(m = 0;m<marks.length;m++){
					nae = marks[m].title;
					if(nae == val){	
						this.overlayman.markers[m].setVisible(true);
						this.overlayman.markers[m].hidden = false;
					 	google.maps.event.trigger(this.overlayman.markers[m],"click");
				 		break;
						}
			  	 }
			     break;
     			 }
			}
		}
	};		


GeoXml.prototype.processing = function(xmlDoc,title, latlon, desc, sbid) {
    this.overlayman.miStart = new Date();
    if(!desc){desc = title;}
    var that = this;
    if(!sbid){ sbid = 0; }
    var shadow;
    var idx;
    var root = xmlDoc.documentElement;
    if(!root){
			return 0; 
			}
    var placemarks = [];
    var name;
    var pname;
    var styles;
    var basename = root.nodeName;
    var keepopen = that.forcefoldersopen;
    var bases = basename.split(":");
    if(bases.length>1){basename = bases[1];}
    var bar, sid, i;
    that.wfs = false;
    if(basename == "FeatureCollection"){
		bar = Lance$(that.basesidebar);
		if(!title){ title = name; }
		if(typeof title == "undefined"){
			title = "Un-named GML";
			}
		that.isWFS = true;
		if(title.length > that.maxtitlewidth){
				that.maxtitlewidth = title.length;
				}
		if(bar){bar.style.display="";}
		idx = that.overlayman.folders.length;
		that.processGML(root,title,latlon,desc,(that.kml.length-1));
		that.kml[0].folders.push(idx);
		}

    if(basename =="gpx"){
	if(!title){ title = name; }
	if(typeof title == "undefined"){
		title = "Un-named GPX";
		}
        that.title = title;
	if(title.length >that.maxtitlewidth){
		that.maxtitlewidth = title.length;
		}

	bar = Lance$(that.basesidebar);
	if(bar){ bar.style.display=""; }
	idx = that.overlayman.folders.length;
	that.processGPX(root, title, that.basesidebar, sbid);
	that.kml[0].folders.push(idx);
	}
    else {

   if(basename == "kml") {	
        styles = root.getElementsByTagName("Style"); 
   	for (i = 0; i <styles.length; i++) {
    		sid= styles[i].getAttribute("id");
      		if(sid){ 
     	   		that.handleStyle(styles[i],sid);
	    		}
   	 	}
	styles = root.getElementsByTagName("StyleMap");
	for (i = 0; i <styles.length; i++) {
		sid = styles[i].getAttribute("id");
		if(sid){
			var found = false;
			var node = styles[i];
			for(var j=0;(j<node.childNodes.length && !found);j++){ 
				var pair = node.childNodes[j];
				for(var k =0;(k<pair.childNodes.length && !found);k++){
					var pn = pair.childNodes[k].nodeName;
					if(pn == "styleUrl"){
						var pid = this.getText(pair.childNodes[k]);
						that.styles["#"+sid] = that.styles[pid];
						found = true;
						}
					if(pn == "Style"){
						that.handleStyle(pair.childNodes[k],sid);
						found = true;
						}
					}
				}
			}
		}

	if(!title){ title = name; }
	if(typeof title == "undefined"){
		title = "KML Document";
		}
        that.title = title;
	if(title.length >that.maxtitlewidth){
		that.maxtitlewidth = title.length;
		}
	var marknames = ["Placemark"];
	var schema = root.getElementsByTagName("Schema");  
	for(var s=0;s<schema.length;s++){
		pname = schema[s].getAttribute("parent");
		if(pname == "Placemark"){
				pname = schema[s].getAttribute("name");
			 	marknames.push(pname);
				}
			}

	bar = Lance$(that.basesidebar);
	if(bar){ bar.style.display=""; }
	idx = that.overlayman.folders.length;
	var paren = that.kml.length-1;
	var fid = that.processKML(root, marknames, title, that.basesidebar,idx, paren);	
	that.kml[paren].folders.push(idx);
	}
     else { 
	placemarks = root.getElementsByTagName("item");
	if(placemarks.length <1){
		placemarks = root.getElementsByTagName("atom");
		}
	if(placemarks.length <1){
		placemarks = root.getElementsByTagName("entry");
		}
	if(!title){ title = name; }
	if(typeof title == "undefined"){
		title = "News Feed";
		}
        that.title = title;
	if(title.length >that.maxtitlewidth){
		that.maxtitlewidth = title.length;
		}
	var style;
	if(that.opts.baseicon){
		style = that.opts.baseicon;
        shadow = that.rssicon.replace(".png",".shadow.png");
        style.shadow = shadow +"_shadow.png";
		}
	else {
        style = new google.maps.MarkerImage(that.rssicon,new google.maps.Size(32,32)); //_DEFAULT_ICONG_DEFAULT_ICON
		style.origin = new google.maps.Point(0,0);
        style.anchor = new google.maps.Point(16,32);
		style.url = that.rssicon;
        shadow = that.rssicon.replace(".png",".shadow.png");
        style.shadow = shadow +"_shadow.png";
		//alert(style.url);
		}
	style.strokeColor = "#00FFFF";
	style.strokeWeight = "3";
	style.strokeOpacity = 0.50;
	if(!desc){ desc = "RSS feed";}
	that.kml[0].folders.push(that.overlayman.folders.length);
    	if(placemarks.length) {
		bar = Lance$(that.basesidebar);
		if(bar){ bar.style.display=""; }
        that.overlayman.folders.push([]);
       	that.overlayman.folderhtml.push([]);
		that.overlayman.folderhtmlast.push(0);
		that.overlayman.folderBounds.push(new google.maps.LatLngBounds());
        	idx = that.overlayman.folders.length-1;	
		that.kml.push(new KMLObj(title,desc,keepopen,idx));
		that.kml[that.kml.length-1].open = keepopen;
		if(that.basesidebar) { 	
			var visible = true;
    		if(that.hideall){ visible = false;}
			var folderid = that.createFolder(idx,title,that.basesidebar,that.globalicon,desc,null,keepopen,visible); }
    		for (i = 0; i < placemarks.length; i++) {
     			that.handlePlacemark(placemarks[i], idx, sbid, style);
    			}
		}
	}

    }
    that.progress--;
    if(that.progress == 0){
	google.maps.event.trigger(that,"parsed");
	if(!that.opts.sidebarid){
		that.mb.showMess("Finished Parsing",1000);
      			// Shall we zoom to the bounds?
		}
 	if (!that.opts.nozoom && !that.basesidebar) {
        	that.map.fitBounds(that.bounds);
      		}
    	}
};


 
GeoXml.prototype.createFolder = function(idx, title, sbid, icon, desc, snippet, keepopen, visible, suppressIt){ 	      
		var sb = Lance$(sbid);
		keepopen = true;	
	 	var folderid = this.myvar+'_folder'+ idx;
                var checked ="";
		if(visible){ checked = " checked "; }
		this.overlayman.folderhtml[folderid]="";
		var disp="display:block";
		var fw= "font-weight:normal";
 		if(typeof keepopen == "undefined" || !keepopen){
			disp ="display:none";
			fw = "font-weight:bold";
	 		}
		if(!desc || desc ==""){
			desc = title;
			}
		desc = escape(desc);
		if (this.suppressFolders == true || suppressIt){
			htm = '<span onclick="'+this.myvar+'.overlayman.zoomToFolder('+idx+');'+this.myvar+'.mb.showMess(\''+desc+'\',3000);return false;" id=\"'+folderid+'\" style="'+disp+'"></span>';
			}
		else {
			var htm = '<ul><input type="checkbox" id="'+this.myvar+''+idx+'FCB" style="vertical-align:middle" ';
			htm += checked;
			htm += 'onclick="'+this.myvar+'.toggleContents('+idx+',this.checked)">';
			htm += '&nbsp;<span title="'+snippet+'" id="'+this.myvar+'TB'+idx+'" oncontextmenu=\"'+this.myvar+'.saveJSON('+idx+');\" onclick="'+this.myvar+'.toggleFolder('+idx+')" style=\"'+fw+'\">';
			htm += '<img id=\"'+this.myvar+'FB'+idx+'\" style=\"vertical-align:text-top;padding:0;margin:0;height:"+this.sidebariconheight+"px;\" border=\"0\" src="'+icon+'" /></span>&nbsp;';
			htm += '<a href="#" onclick="'+this.myvar+'.overlayman.zoomToFolder('+idx+');'+this.myvar+'.mb.showMess(\''+desc+'\',3000);return false;">' + title + '</a><br><div id=\"'+folderid+'\" style="'+disp+'"></div></ul>';
			}
		if(sb){ sb.innerHTML = sb.innerHTML + htm; }
		
		
		return folderid;
	    };

GeoXml.prototype.processGML = function(root,title, latlon, desc, me) {
    var that = this;
    var isWFS = false;
    var placemarks = [];
    var srsName;
    var isLatLon = false;
    var xmin = 0;
    var ymin = 0;
    var xscale = 1;
    var yscale = 1;
    var points, pt, pts;
    var coor, coorstr;
    var x, y, k, i;
    var name = title;
    var visible = true;
	this.showLabels = false;
    if(this.hideall){visible = false; }
    var keepopen = that.allfoldersopen;
    var pt1, pt2, box;
    	for (var ln = 0; ln < root.childNodes.length; ln++) {
		var kid = root.childNodes.item(ln).nodeName;
		var n = root.childNodes.item(ln);
		if(kid == "gml:boundedBy" || kid  == "boundedBy"){
			 for (var j = 0; j < n.childNodes.length; j++) {
				var nn = n.childNodes.item(j).nodeName;
				var llre = /CRS:84|(4326|4269)$/i;
				if(nn == "Box" || nn == "gml:Box"){
					box =  n.childNodes.item(j);
					srsName = n.childNodes.item(j).getAttribute("srsName");
					if(srsName.match(llre)){
						isLatLon = true;
						} 
					else {
						alert("SRSname ="+srsName+"; attempting to create transform");
						 for (k = 0; k < box.childNodes.length; k++) {
							coor = box.childNodes.item(k);
							if(coor.nodeName =="gml:coordinates" ||coor.nodeName =="coordinates" ){
								coorstr =  this.getText(coor);
								pts = coorstr.split(" ");
								pt1 = pts[0].split(",");
								pt2 = pts[1].split(",");
								xscale = (parseFloat(pt2[0]) - parseFloat(pt1[0]))/(latlon.xmax - latlon.xmin);
								yscale = (parseFloat(pt2[1]) - parseFloat(pt1[1]))/(latlon.ymax - latlon.ymin);
								xmin = pt1[0]/xscale - latlon.xmin;
								ymin = pt1[1]/yscale - latlon.ymin;
								}
							}
						}
					break;
					}
				if(nn == "Envelope" || nn == "gml:Envelope"){
					box =  n.childNodes.item(j);
					srsName = n.childNodes.item(j).getAttribute("srsName");
					if(srsName.match(llre)){
						isLatLon = true;
						} 
					else {
						alert("SRSname ="+srsName+"; attempting to create transform");
						 for (k = 0; k < box.childNodes.length; k++) {
							coor = box.childNodes.item(k);
							if(coor.nodeName =="gml:coordinates" ||coor.nodeName =="coordinates" ) {
								pts = coor.split(" ");
								var b = {"xmin":100000000,"ymin":100000000,"xmax":-100000000,"ymax":-100000000};
								for(var m = 0;m<pts.length-1;m++){
									pt = pts[m].split(",");
									x = parseFloat(pt[0]);
									y = parseFloat(pt[1]);
									if(x<b.xmin){ b.xmin = x; }
									if(y<b.ymin){ b.ymin = y; }
									if(x>b.xmax){ b.xmax = x; }
									if(y>b.ymax){ b.ymax = y; }
									}
								xscale = (b.xmax - b.xmin)/(latlon.xmax - latlon.xmin);
								yscale = (b.ymax - b.ymin)/(latlon.ymax - latlon.ymin);
								xmin = b.xmin/xscale - latlon.xmin;
								ymin = b.ymin/yscale - latlon.ymin;
								}
							}
						}
					
						}
						break;
					}
				}
		if(kid == "gml:featureMember" || kid == "featureMember"){
			placemarks.push(n);
			}
		}
 
     var folderid;
     if(!title){ title = name; }
       this.title = title;
       if(placemarks.length<1){
		alert("No features found in "+title);
		this.mb.showMess("No features found in "+title,3000);
		} 
	else {
	    this.mb.showMess("Adding "+placemarks.length+" features found in "+title);
        this.overlayman.folders.push([]);
         this.overlayman.folderhtml.push([]);
	    this.overlayman.folderhtmlast.push(0);
	    this.overlayman.folderBounds.push(new google.maps.LatLngBounds());
	    var idx = this.overlayman.folders.length-1;
	    if(this.basesidebar) {
	//	alert("before createFolder "+visible);
		folderid = this.createFolder(idx,title,this.basesidebar,this.gmlicon,desc,null,keepopen,visible,(placemarks.length == 1));
	    }
 	    this.kml.push(new KMLObj(title,desc,true,idx));
	    this.kml[me].open = that.opts.allfoldersopen; 
	    this.kml[me].folderid = folderid;


	if(isLatLon){
    		for (i = 0; i < placemarks.length; i++) {
     			this.handlePlacemark(placemarks[i],idx,0);
    			}
		}
	else {
	     var trans = {"xs":xscale,"ys":yscale,"x":xmin, "y":ymin };
	    for (i = 0; i < placemarks.length; i++) {
		        this.handleGeomark(placemarks[i],idx,trans,0);
		        }
	    	}
	}
    // Is this the last file to be processed?
};

google.maps.Polyline.prototype.getBounds = function() {
  if(typeof this.bounds!="undefined") { return this.bounds; }
   else { return (this.computeBounds()); }
  };

google.maps.Polyline.prototype.getPosition = function () { 
	var p = this.getPath();
	return (p.getAt(Math.round(p.getLength()/2))); 
	};
google.maps.Polyline.prototype.computeBounds = function() {
  var bounds = new google.maps.LatLngBounds();
  var p = this.getPath();
  for (var i=0; i < p.getLength() ; i++) {
	var v = p.getAt(i);
	if(v){ 
		bounds.extend(v); 
		}
  	}

  this.bounds = bounds;
  return bounds;
};
/*
GTileLayerOverlay.prototype.getBounds = function(){return this.bounds; };

GTileLayer.prototype.getBounds = function(){
	return this.bounds;
	}; 
*/
google.maps.Polygon.prototype.getPosition = function() { return (this.getBounds().getCenter()); };
google.maps.Polygon.prototype.computeBounds = function() {
  var bounds = new google.maps.LatLngBounds();
  var p = this.getPaths();
  for(var a=0;a < p.getLength();a++) { 
	var s = p.getAt(a);
	for (var i=0; i < s.getLength() ; i++) {
		var v = s.getAt(i);
		if(v){ 
			bounds.extend(v); 
			}
		}
	}
  this.bounds = bounds;
  return bounds;
};
google.maps.Polygon.prototype.getBounds = function() {
  if(typeof this.bounds!="undefined") { return this.bounds; }
   else { return (this.computeBounds()); }
  };
google.maps.Polygon.prototype.getCenter = function() {
	return (this.getBounds().getCenter()); 
  };

OverlayManagerView.prototype = new google.maps.OverlayView();
function OverlayManagerView(map) {
   this.setMap(map);
};

OverlayManagerView.prototype.onAdd = function() {
};
OverlayManagerView.prototype.draw  = function() {
};
OverlayManagerView.prototype.onRemove  = function() {
};

OverlayManager = function ( map , paren, opts) {
    this.myvar = paren.myvar;
    this.paren = paren;
    this.map = map;
    this.markers = [];
    this.byid = [];
    this.byname = [];
    this.groups = [];
    this.timeout = null;
    this.folders = [];
    this.folderBounds = [];
    this.folderhtml = [];
    this.folderhtmlast = [];
    this.subfolders = [];
    this.currentZoomLevel = map.getZoom();
    this.isParsed = false;
	this.overlayview = new OverlayManagerView(map);

	this.defaultMaxVisibleMarkers =  400;
	this.defaultGridSize = 12;
	this.defaultMinMarkersPerCluster = 5;
	this.defaultMaxLinesPerInfoBox = 15;
	this.defaultClusterZoom = 'dblclick';
	this.defaultClusterInfoWindow = 'click';
	this.defaultClusterMarkerZoom = 16;
	this.defaultIcon = new google.maps.MarkerImage('http://maps.google.com/mapfiles/kml/paddle/blu-circle.png',
				new google.maps.Size(32, 32), //size
				new google.maps.Point(0, 0), //origin
				new google.maps.Point(16, 12), //anchor
				new google.maps.Size(32, 32) //scaledSize 
				);

    this.maxVisibleMarkers = opts.maxVisibleMarkers || this.defaultMaxVisibleMarkers;
    this.gridSize = opts.gridSize || this.defaultGridSize;
    this.minMarkersPerCluster = opts.minMarkersPerCluster || this.defaultMinMarkersPerCluster;
    this.maxLinesPerInfoBox = opts.maxLinesPerInfoBox || this.defaultMaxLinesPerInfoBox;
	this.ClusterZoom = opts.ClusterZoom || this.defaultClusterZoom;
	this.ClusterInfoWindow = opts.ClusterInfoWindow || this.defaultClusterInfoWindow;
	this.ClusterMarkerZoom = opts.ClusterMarkerZoom || this.defaultClusterMarkerZoom;
	this.ClusterIconUrl = opts.ClusterIconUrl || 'http://www.dyasdesigns.com/tntmap/images/m';
	this.lang = { txtzoomin:"",txtclustercount1:"...and",txtclustercount2:"more"};
	if (typeof opts.lang != "undefined") { 
		this.lang.txtzoomin = opts.lang.txtzoomin;
		this.lang.txtclustercount1 = opts.lang.txtclustercount1;
		this.lang.txtclustercount2 = opts.lang.txtclustercount2;
		}
	 
    this.icon = opts.Icon|| this.defaultIcon;
	this.optcluster = {};
	this.optcluster.overlayman = this;
	this.optcluster.minimumClusterSize = this.minMarkersPerCluster;
	this.optcluster.gridSize = this.gridSize;
	this.optcluster.ClusterZoom = this.ClusterZoom;
	this.optcluster.ClusterInfoWindow = this.ClusterInfoWindow;
	this.optcluster.imagePath = this.ClusterIconUrl;
	this.cluster = new MarkerClusterer(this.map, {}, this.optcluster,this.paren);
	
	google.maps.event.addListener( this.paren, 'adjusted',OverlayManager.MakeCaller( OverlayManager.Display, this ) );
	google.maps.event.addListener( map, 'idle', OverlayManager.MakeCaller( OverlayManager.Display, this ) );
    //google.maps.event.addListener( map, 'zoomend', OverlayManager.MakeCaller( OverlayManager.Display, this ) );
   // google.maps.event.addListener( map, 'moveend', OverlayManager.MakeCaller( OverlayManager.Display, this ) );
    google.maps.event.addListener( map, 'infowindowclose', OverlayManager.MakeCaller( OverlayManager.PopDown, this ) );
	this.icon.pane = this.paren.markerpane;
    };

// Call this to change the group icon.
OverlayManager.prototype.SetIcon = function ( icon ) {
    this.icon = icon;
    };


// Changes the maximum number of visible markers before clustering kicks in.
OverlayManager.prototype.SetMaxVisibleMarkers = function ( n ){
    this.maxVisibleMarkers = n;
    };


// Sets the minumum number of markers for a group.
OverlayManager.prototype.SetMinMarkersPerCluster = function ( n ){
    this.minMarkersPerCluster = n;
    };


// Sets the maximum number of lines in an info box.
OverlayManager.prototype.SetMaxLinesPerInfoBox = function ( n ){
    this.maxLinesPerInfoBox = n;
    };


// Call this to add a marker.
OverlayManager.prototype.addMarker = function (marker, title, idx, sidebar, visible, forcevisible) { 

    if (marker.setMap != null){
		marker.setMap(this.map);
		}
    marker.hidden = false;
    if(visible != true){marker.hidden = true; }
    if(this.paren.hideall){marker.hidden = true; }
    marker.title = title;
    this.folders[idx].push(this.markers.length);
	
    var bounds = this.map.getBounds();
	var vis = false;
	if(bounds) { //map doesnt have bounds defined?
		if(typeof marker.getBounds =="undefined"){
			if (bounds.contains(marker.getPosition())) { 
				vis = true;  
				}
			}
		else {
			var b = marker.getBounds();
			if(!b.isEmpty()){
				if(bounds.intersects(b)){ 
					vis = true;  
					}
				}
			}
		}
	else {
		vis = true;
		}
     if(forcevisible){ vis = true; }
   // var id = this.markers.length;
    this.markers.push(marker);
    if(vis){ 
		if(marker.hidden){
			marker.setMap(null); 
			marker.onMap = false;
//			if(!!marker.label){ marker.label.hide();} 
			if(!!marker.label){ marker.label.setMap(null);} 
			}
		else {
			marker.setMap(this.map);
			marker.onMap = true;
//			if(!!marker.label){ marker.label.show();} 
			if(!!marker.label){ marker.label.setMap(this.map);} 
			}
		}
	this.cluster.addMarker(marker);
    this.DisplayLater();
    if(sidebar){
	this.folderhtml[idx].push(sidebar);
	}
   // return id;
    };

OverlayManager.prototype.zoomToFolder = function (idx) {
	var bounds = this.folderBounds[idx];
	this.map.fitBounds(bounds);
	};


// Call this to remove a marker.
OverlayManager.prototype.RemoveMarker = function ( marker ) {
    for ( var i = 0; i < this.markers.length; ++i ) {
	if ( this.markers[i] == marker ) {
	    if (marker.onMap){
			marker.setMap(null);
			}
	    if(!!marker.label){
//			marker.label.hide();
			marker.label.setMap(null);
			} 
	    for ( var j = 0; j < this.groups.length; ++j ) {

		var group = this.groups[j];
		if ( group!= null )
		    {
		    for ( var k = 0; k < group.markers.length; ++k ){
			if ( group.markers[k] == marker ) {
			    group.markers[k] = null;
			    --group.markerCount;
			    break;
			    }
		    	}
		    if ( group.markerCount == 0 ) {
			this.ClearGroup( group );
			this.groups[j] = null;
			}
		    else { 
			if ( group == this.poppedUpCluster ){ OverlayManager.RePop( this );}
		    	}
		    }
		}
	    this.markers[i] = null;
	    break;
	    } 
	}
	this.cluster.removeMarker(marker);
    this.DisplayLater();
    };



OverlayManager.prototype.DisplayLater = function (){
    if ( this.timeout!= null ){ 
	clearTimeout( this.timeout ); }
    this.timeout = setTimeout( OverlayManager.MakeCaller( OverlayManager.Display, this ), 50);
    };
	
OverlayManager.Display = function (overlaymanager){
    var i, j, k, marker, group, l;
    clearTimeout( overlaymanager.timeout );
    if(overlaymanager.paren.allRemoved){
		return;
		}

    var update_side = false;
    var count = 0;
    var clon, bits;
    var vis;
    var content;
    if(overlaymanager.paren.basesidebar){
    for(k = 0; k< overlaymanager.folderhtml.length ; k++ ){	
	var curlen = overlaymanager.folderhtml[k].length;
	var con = overlaymanager.folderhtmlast[k];
	if(con < curlen){
		var destid = overlaymanager.paren.myvar+"_folder"+k;
		var dest = Lance$(destid);
		if(dest){
			if(overlaymanager.paren.opts.sortbyname){
			        content = dest.innerHTML;
				clon = overlaymanager.folderhtml[k].sort();
				for(l=0; l<curlen; l++){
 					bits = clon[l].split("$$$",8);
          				content += overlaymanager.paren.sidebarfn(bits[0],bits[1],bits[2],bits[3],bits[4],bits[5],bits[6],bits[7]); 
					}
				}
			else {
	 		       content = dest.innerHTML;
			       clon = overlaymanager.folderhtml[k];
				for(l=con; l<curlen; l++){
 					bits = clon[l].split("$$$",8);
          				content += overlaymanager.paren.sidebarfn(bits[0],bits[1],bits[2],bits[3],bits[4],bits[5],bits[6],bits[7]);  
					}
				}
				
			overlaymanager.folderhtmlast[k] = curlen;
			dest.innerHTML  = content;
		 	if(overlaymanager.paren.forcefoldersopen){
	 			dest.style.display = "block";
	 			}
			update_side = true;
			count = curlen;
			}
		else {
		//	alert("target folder not found "+destid);
			}
		}
		}
	}
	
  // Is this the last file to be processed?
  	
	if(update_side && count>0){
		 if (overlaymanager.paren.progress == 0) {
			overlaymanager.paren.setFolders();
     			google.maps.event.trigger(overlaymanager.paren,"parsed");
      			if(!overlaymanager.paren.opts.sidebarid){	
				overlaymanager.paren.mb.showMess("Finished Parsing",1000);
				}
			var mifinish = new Date();
			var sec = ((mifinish - overlaymanager.miStart)/1000+" seconds");
			overlaymanager.paren.mb.showMess("Loaded "+count+"  GeoXML elements in "+sec,5000);
			overlaymanager.paren.ParseURL();
			if (!overlaymanager.paren.opts.nozoom) {
					overlaymanager.paren.map.fitBounds(overlaymanager.paren.bounds);
      				}
    			}
		}

    if (update_side && typeof resizeKML != "undefined"){
		resizeKML();
		} 

    var bounds;
	var sw;
	var ne;
	var dx;
	var dy;
	var newzoom = false;
    var newZoomLevel = overlaymanager.map.getZoom();
    if ( newZoomLevel != overlaymanager.currentZoomLevel ) {
		newzoom = true;
		// When the zoom level changes, we have to remove all the groups.
		for ( i = 0; i < overlaymanager.groups.length; ++i ){
			if ( overlaymanager.groups[i]!= null ) {
			overlaymanager.ClearGroup( overlaymanager.groups[i] );
			overlaymanager.groups[i] = null;
			}
		}
		overlaymanager.groups.length = 0;
		overlaymanager.currentZoomLevel = newZoomLevel;
	}

    // Get the current bounds of the visible area.
   // bounds = overlaymanager.map.getBounds();
	if(overlaymanager.map.getBounds()) {
		// Expand the bounds a little, so things look smoother when scrolling
		// by small amounts.
		  bounds = overlaymanager.getMapBounds(overlaymanager);
		  //alert(bounds);
		  sw = bounds.getSouthWest();
		  ne = bounds.getNorthEast();
		  dx = ne.lng() - sw.lng();
		  dy = ne.lat() - sw.lat();
//		if ( dx < 300 && dy < 150 ){
//			dx *= 0.05;
//			dy *= 0.05;
//			bounds = new google.maps.LatLngBounds(
//			new google.maps.LatLng( sw.lat() - dy, sw.lng() - dx ),
//			new google.maps.LatLng( ne.lat() + dy, ne.lng() + dx ) );
//			}
		}
	if(!!!bounds && overlaymanager.map){
		//alert("finding bounds");
		bounds = overlaymanager.getMapBounds(overlaymanager);
		if(!!!bounds)return;
		}
    // Partition the markers into visible and non-visible lists.
    var visibleMarkers = [];
    var nonvisibleMarkers = [];
    var viscount = 0;
 
    for ( i = 0; i < overlaymanager.markers.length; ++i ) {
		marker = overlaymanager.markers[i];
		vis = false;
		//alert(marker);
		if (marker!== null ){
			var mid = overlaymanager.paren.myvar+"sb"+i;	
				if(typeof marker.getBounds == "undefined"){
					var pos = marker.getPosition();
					if (bounds.contains(pos) ) {
						vis = true; 
						viscount++;
						}
					}
				else {
					var b = marker.getBounds();
					if(bounds.intersects(b)){
							vis = true;
							}
					}
				if(Lance$(mid)){ 
						if(vis){ Lance$(mid).className = "inView"; }
						 else { Lance$(mid).className = "outView"; }
						}
				//alert(vis);
				if(vis && (marker.hidden == false)){ 
						visibleMarkers.push(i); 
						}
				else { nonvisibleMarkers.push(i); }
			  
			}
		}
 
    if ( newzoom ) {
		if (viscount > overlaymanager.maxVisibleMarkers)
			overlaymanager.cluster.setMinimumClusterSize(overlaymanager.minMarkersPerCluster);	
		else
			overlaymanager.cluster.setMinimumClusterSize(overlaymanager.maxVisibleMarkers);	
			 
		overlaymanager.cluster.repaint();	 
	} 

    OverlayManager.RePop( overlaymanager );
    };


OverlayManager.PopUp = function ( overlaymanager, cClusterIcon )
    {
	for (x =0; x<overlaymanager.cluster.clusters_.length; x++) {
		if (cClusterIcon==overlaymanager.cluster.clusters_[x].clusterIcon_)
			break;
	}
		
	var html = '<table style="font-size:10px" width="300">';
	var n = 0;
	for ( var i = 0; i < cClusterIcon.cluster_.markers_.length; ++i ) {
		var marker = cClusterIcon.cluster_.markers_[i];
		if ( marker!= null ) {
			++n;
			html += '<tr><td><a href="javascript:OverlayManager.ZoomIntoMarker('+overlaymanager.myvar+'.overlayman.cluster.clusters_['+x+'].markers_['+i+'])">';
			if (marker.smallImage != null ) {
				html += '<img src="' + marker.smallImage + '">';
			} else {
				html += '<img src="' + marker.icon.url + '" width="' + ( marker.icon.size.width / 2 ) + '" height="' + ( marker.icon.size.height / 2 ) + '">'; 
			}
			html += '</td><td>' + marker.title + '</a></td></tr>';
			if (n == overlaymanager.maxLinesPerInfoBox - 1 && cClusterIcon.cluster_.markers_.length > overlaymanager.maxLinesPerInfoBox) {
				html += '<tr><td colspan="2">'+overlaymanager.lang.txtclustercount1+' ' + ( cClusterIcon.cluster_.markers_.length - n ) + ' '+overlaymanager.lang.txtclustercount2+'</td></tr>';
				break;
			}
		}
	}
	html += '<tr><td colspan="2"><a href="javascript:OverlayManager.ZoomIntoCluster('+overlaymanager.myvar+'.overlayman)">'+overlaymanager.lang.txtzoomin+'</a></td></tr>';
	html += '</table>';

   // overlaymanager.map.closeInfoWindow(); close Last Marker
    if (overlaymanager.paren.lastMarker&&overlaymanager.paren.lastMarker.infoWindow)
		overlaymanager.paren.lastMarker.infoWindow.close();
	var infoWindowOptions = { 
				content: html,
				pixelOffset: new google.maps.Size(0, 2),
				position: cClusterIcon.cluster_.bounds_.getCenter()
				};
	if(overlaymanager.paren.maxiwwidth){
					infoWindowOptions.maxWidth = overlaymanager.paren.maxiwwidth;
					}
	cClusterIcon.infoWindow = new google.maps.InfoWindow(infoWindowOptions);
	overlaymanager.paren.lastMarker = cClusterIcon;
	overlaymanager.paren.lastMarker.infoWindow.open(overlaymanager.paren.map);
    overlaymanager.poppedUpCluster = cClusterIcon;
    };

OverlayManager.ZoomIntoCluster = function (overlaymanager)
    {
	if (overlaymanager.poppedUpCluster) {
      var mc = overlaymanager.poppedUpCluster.cluster_.getMarkerClusterer();
      /**
       * This event is fired when a cluster marker is clicked.
       * @name MarkerClusterer#click
       * @param {Cluster} c The cluster that was clicked.
       * @event
       */
      google.maps.event.trigger(mc, mc.ClusterZoom_, overlaymanager.poppedUpCluster.cluster_);
      google.maps.event.trigger(mc, "cluster"+mc.ClusterZoom_, overlaymanager.poppedUpCluster.cluster_); // deprecated name

      // The default dblclick handler follows. Disable it by setting

      // the zoomOnClick property to false.
      if (mc.getZoomOnClick()) {
        // Zoom into the cluster.
        mz = mc.getMaxZoom();
        theBounds = overlaymanager.poppedUpCluster.cluster_.getBounds();
        mc.getMap().fitBounds(theBounds);
        // There is a fix for Issue 170 here:
        setTimeout(function () {
          mc.getMap().fitBounds(theBounds);
          // Don't zoom beyond the max zoom level
          if (mz !== null && (mc.getMap().getZoom() > mz)) {
            mc.getMap().setZoom(mz + 1);
          }
        }, 100);
      }
	}
};

OverlayManager.ZoomIntoMarker = function ( marker )
    {
	if (marker) {
		marker.geoxml.map.setZoom(marker.geoxml.overlayman.ClusterMarkerZoom);
		marker.geoxml.map.setCenter(marker.getPosition());
	}
};

OverlayManager.prototype.getMapBounds = function (overlaymanager) {
  var bounds;
  
  if (overlaymanager.map.getZoom() > 1) {
	  var b = overlaymanager.map.getBounds();
	  if (typeof b === "undefined")
	 	bounds = new google.maps.LatLngBounds(new google.maps.LatLng(-85.08136444384544, -178.48388434375), new google.maps.LatLng(85.02070771743472, 178.00048865625));
	  else
		bounds = new google.maps.LatLngBounds(b.getSouthWest(), b.getNorthEast());
  } else {
    bounds = new google.maps.LatLngBounds(new google.maps.LatLng(-85.08136444384544, -178.48388434375), new google.maps.LatLng(85.02070771743472, 178.00048865625));
  }
 
	var projection = overlaymanager.overlayview.getProjection();
	if (projection) {
	  // Turn the bounds into latlng.
		var tr = new google.maps.LatLng(bounds.getNorthEast().lat(), bounds.getNorthEast().lng());
		var bl = new google.maps.LatLng(bounds.getSouthWest().lat(), bounds.getSouthWest().lng());
	
	  // Convert the points to pixels and the extend out by the grid size.
	  var trPix = projection.fromLatLngToDivPixel(tr);
	  trPix.x += overlaymanager.gridSize;
	  trPix.y -= overlaymanager.gridSize;
	
	  var blPix = projection.fromLatLngToDivPixel(bl);
	  blPix.x -= overlaymanager.gridSize;
	  blPix.y += overlaymanager.gridSize;
	
	  // Convert the pixel points back to LatLng
	  var ne = projection.fromDivPixelToLatLng(trPix);
	  var sw = projection.fromDivPixelToLatLng(blPix);
	
	  // Extend the bounds to contain the new bounds.
	  bounds.extend(ne);
	  bounds.extend(sw);
  }
  
  return bounds;

};

OverlayManager.RePop = function ( overlaymanager )
    {
//    if ( overlaymanager.poppedUpCluster!= null ){ 
//	OverlayManager.PopUp( overlaymanager.poppedUpCluster ); }
    };


OverlayManager.PopDown = function ( overlaymanager )
    {
    overlaymanager.poppedUpCluster = null;
	overlaymanager.paren.lastMarker=null;
    };


OverlayManager.prototype.ClearGroup = function ( group )
    {
    var i, marker;

    for ( i = 0; i < group.markers.length; ++i ) {
	if ( group.markers[i]!= null ) {
	    group.markers[i].inCluster = false;
	    group.markers[i] = null;
	    }
    	}
    group.markers.length = 0;
    group.markerCount = 0;
    if ( group == this.poppedUpCluster ) {
	this.map.closeInfoWindow(); }
    if (group.onMap) {
		group.marker.setMap(null); 
		group.onMap = false;
		}
    };


// This returns a function closure that calls the given routine with the
// specified arg.
OverlayManager.MakeCaller = function ( func, arg )
    {
    return function () { func( arg ); };
    };

MessageBox = function(map,paren,myvar,mb){
	this.map = map;
	this.paren = paren;
	this.myvar = paren.myvar+"."+myvar;
	this.eraseMess = null;
	this.centerMe = null;
	this.mb = null;
	if(mb){ this.mb = mb; }
	this.id = this.myvar + "_message";
	};

MessageBox.prototype.hideMess = function(){
	if(this.paren.quiet){
		return;
		}
  	this.mb.style.visiblity ="hidden"; 
	this.mb.style.left = "-1200px";
	this.mb.style.top = "-1200px";
	};

MessageBox.prototype.centerThis = function(){
	var c ={};
	c.x = this.map.getDiv().offsetWidth/2;
	c.y = this.map.getDiv().offsetHeight/2;
	//alert(c.x);
	if(!this.mb){ 
		this.mb = Lance$(this.id); 
		}
	if(this.centerMe){ clearTimeout(this.centerMe);}
	if(this.mb){
		var nw = this.mb.offsetWidth;
		if(nw > this.map.getDiv().offsetWidth){
			nw = parseInt(2*c.x/3,10);
			this.mb.style.width = nw +"px";
			this.centerMe = setTimeout(this.myvar+".centerThis()",5);
			return; 
			}
		this.mb.style.left = (c.x - (nw/2)) +"px";
		this.mb.style.top = (c.y - 20 - (this.mb.offsetHeight/2))+ "px";
		}
	else {
		this.centerMe = setTimeout(this.myvar+".centerThis()",10);
		}
	};


MessageBox.prototype.showMess = function (val,temp){
	if(this.paren.quiet){
		if(console){
			console.log(val);
			}
		return;
		}
	val = unescape(val);
	if(this.eraseMess){ clearTimeout(this.eraseMess); }
	if(!this.mb){ this.mb = Lance$(this.id); }
	if(this.mb){

		this.mb.innerHTML = "<span>"+val+"</span>";

	    if(temp){
				this.eraseMess = setTimeout(this.myvar+".hideMess();",temp);
				}
		var d = this.map.getDiv();
		var w = this.mb.offsetWidth/2;
		var h = this.mb.offsetHeight/2;
		this.mb.style.left = parseInt(d.offsetWidth/2 - w) + "px";
		this.mb.style.top = parseInt(d.offsetHeight/2 - h) + "px";
		this.mb.style.width = "";
		this.mb.style.height = "";
		this.centerMe = setTimeout(this.myvar+".centerThis()",5);
		this.mb.style.visibility = "visible"; 
		}

	else {  
		var d = document.createElement("div");
		d.innerHTML = val;
		d.id = this.myvar + "_message";
		d.style.position = "absolute";
		d.style.backgroundColor = this.style.backgroundColor || "silver";
		d.style.opacity = this.style.opacity || 0.80;
		if(document.all) {
			d.style.filter = "alpha(opacity="+parseInt(d.style.opacity*100,10)+")";
			}
		d.style.color = this.style.color || "black";
		d.style.padding = this.style.padding || "6px";
 		d.style.borderWidth = this.style.borderWidth || "3px";
		d.style.borderColor = this.style.borderColor || "";
		d.style.backgroundImage = this.style.backgroundImage || "";
		d.style.borderStyle = this.style.borderStyle || "outset";
		d.style.visibility = "visible";
		d.style.left = "-1200px";
		d.style.top = "-1200px";
		//alert(this.myvar);
	 	this.centerMe = setTimeout(this.myvar+".centerThis()",5);
	
		d.style.zIndex = 1000;
		document.body.appendChild(d);
		}
	}; 

GeoXml.prototype.loadJSONUrl = function (url, title, latlon, desc, idx) {
  var that = this;
  GDownloadUrl(url, function(doc) {
    	that.parseJSON(doc,title, latlon, desc, idx);
  	});
};

GeoXml.prototype.loadXMLUrl = function(url, title, latlon, desc, idx) {
    var that = this;
    that.DownloadURL(url, function(doc) {
        var xmlDoc = that.parseXML(doc);
        that.processing(xmlDoc, title, latlon, desc, idx);
    }, title, true);
};

GeoXml.prototype.upgradeLayer = function(n) {
	var mt = this.map.getMapTypes();
	var found = false;
	for(var i=0;i<mt.length;i++){
		if(mt[i] == this.baseLayers[n]){
			found = true;
			this.map.removeMapType(this.baseLayers[n]);
			}
		}
	if(!found){ this.map.addMapType(this.baseLayers[n]); }
	};

GeoXml.prototype.makeWMSTileLayer = function(getmapstring, on, title, opac, attr, grouptitle, wmsbounds) { //not yet working.
	var that = this;
	gmapstring = new String(getmapstring);
	getmapstring = gmapstring.replace("&amp;","&");
 	var args = getmapstring.split("?");
	var baseurl = args[0]+"?";
	baseurl = baseurl.replace(/&request=getmap/i,"");
	baseurl = baseurl.replace(/&service=wms/i,"");
	//alert("base"+baseurl);
	var version = "1.1.0";
	var format = "image/png";
	var styles = "";
	var layers = "";
	var queryable = false;
	var opacity = 0.5;
	if(typeof opac!="undefined"){ opacity = opac; }
	var bbox = "-180,-90,180,90";
	var pairs = args[1].split("&");
	var sld ="";
	var servicename="";
	var atlasname="";
	var gmcrs = "";
	var epsg;
	for(var i=0;i < pairs.length; i++){
		var dstr = pairs[i];
		var duo = pairs[i].split("=");
		var dl = duo[0].toLowerCase();
		switch(dl) {
			case "version" : version = duo[1];break;
			case "bbox": bbox = duo[1]; break;
			case "width":
			case "height":break;
			case "service":break;
			case "servicename": servicename = duo[1]; break;
			case "atlasname":atlasname = duo[1];break;
			case "styles": styles = duo[1]; break;
			case "layers": layers = duo[1]; break;
			case "format": format = duo[1]; break;
			case "opacity":opacity = parseFloat(duo[1]); break;
			case "crs":
			case "srs":epsg = duo[1]; break;
			case "gmcrs":gmcrs = duo[1];break;
			case "queryable":queryable = duo[1];break;
			case "getmap":break;
			case "service":break;
			default : if(duo[0]){ baseurl += "&"+pairs[i]; } break;
			}
		}

	if(gmcrs) { 
		epsg = gmcrs; 
		}
	var bbn = bbox.split(",");
	var bb = {"w":parseFloat(bbn[0]),"s":parseFloat(bbn[1]),"e":parseFloat(bbn[2]),"n":parseFloat(bbn[3])};
	var lon = (bb.n - bb.s);
	var z = 0; 
	var ex = 180;

 	while(ex >= lon){
		ex = ex/2;
		z++;
		}
	z--;
	if(z<1){ z=1; }

 	if(!attr) { attr = "Base Map from OGC WMS"; }
	//var cr0 = new GCopyright(1, new google.maps.LatLngBounds(new google.maps.LatLng(bb.s,bb.w),new google.maps.LatLng(bb.n,bb.e)),0,attr);
   // 	var cc0 = new GCopyrightCollection("");
   //  	cc0.addCopyright(cr0);
   /*
 	var twms = new IMGTileSet({baseUrl:baseurl}); //GTileLayer(cc0,z,19);
	twms.s = bb.s; twms.n = bb.n; twms.e = bb.e; twms.w = bb.w;
	twms.myBaseURL = baseurl;
	if(servicename){
		twms.servicename = servicename;
		}
	if(atlasname){
		twms.atlasname = atlasname;
		}
	twms.publishdirectory = this.publishdirectory;
	twms.epsg = epsg;
	twms.getPath = function(cords,c) {
		a,b
		if (typeof(this.myStyles)=="undefined") {
			this.myStyles=""; 
			}
		var lULP = new google.maps.Point(a.x*256,(a.y+1)*256);
		var lLRP = new google.maps.Point((a.x+1)*256,a.y*256);
		var lUL = G_NORMAL_MAP.getProjection().fromPixelToLatLng(lULP,b,c);
		var lLR = G_NORMAL_MAP.getProjection().fromPixelToLatLng(lLRP,b,c);
		var west = lUL.x;
		var east = lLR.x;
		var north = lUL.y;
		var south = lLR.y;
		var ge = east;
		var gw = west;
		var gs = south;
		var gn = north;
		if(gn < gs){ gs = gn; gn = south; }
		if(this.epsg != "EPSG:4326" && this.epsg != "CRS:84" && this.epsg!= "4326") {
			west = GeoXml.merc2Lon(west);
			north = GeoXml.merc2Lat(north);
			east = GeoXml.merc2Lon(east);
			south = GeoXml.merc2Lat(south);
			}
		var w = Math.abs(east - west);
		var h = Math.abs(north - south);
		var s = h/w;
 		h = Math.round((256.0 * s) + 0.5);
 
		w = 256;
		var sud = south; 
		if(north < south){
			south = north; north = sud; 
			}

		  if(gs>(this.n) || ge < (this.w) || gn < (this.s) || gw > (this.e)  ){
			var retstr = this.publishdirectory +"black.gif";
		 	}

    		var lBbox=west+","+south+","+east+","+north;
		var lSRS="EPSG:41001";
		if(typeof this.epsg != "undefined" || this.srs == "4326"){
    			lSRS=this.epsg;
			}


		var lURL=this.myBaseURL;	
		if(typeof this.myVersion == "undefined"){ this.myVersion = "1.1.1"; }

		var ver = parseFloat(this.myVersion);
		var arcims = /arcimsproxy/i; 
		if(!this.myBaseURL.match(arcims)) {
			lURL+="&SERVICE=WMS";
			if(this.myVersion !="1.0.0"){
				var gmap = /request=getmap/i;
				if(!lURL.match(gmap)){
					lURL+="&REQUEST=GetMap";
					}
				}
			else {
				lURL+="&REQUEST=Map";
				}
			}
		if(this.servicename){
			lURL += "?ServiceName="+this.servicename;
			}
		if(this.atlasname){
			lURL += "&AtlasName="+this.servicename;
			}
		lURL+="&VERSION="+this.myVersion;
		if(this.myLayers) {
			lURL+="&LAYERS="+this.myLayers;
			lURL+="&STYLES="+this.myStyles; 
			}
		if(this.mySLD){
			lURL+="&SLD="+this.mySLD; 
			}
  		lURL+="&FORMAT="+this.myFormat;
		lURL+="&BGCOLOR=0x000000";
		lURL+="&TRANSPARENT=TRUE";
		if(this.myVersion == "1.1.1" || ver<1.3 ){
			lURL += "&SRS=" + lSRS;
			}

		else {
			lURL += "&CRS=" + lSRS;

			}
		lURL+="&WIDTH="+w;
		lURL+="&HEIGHT="+h;
		lURL+="&BBOX="+lBbox;
		this.requestCount++;
		return lURL;
		};
	twms.myFormat = format;
	twms.myVersion = version;
	twms.myExtents = bbox;
	twms.queryable = queryable;
	twms.opacity = opacity;
	twms.getOpacity = function() { return this.opacity; };
	if(sld){
		twms.mySLD = sld;
		}
	else {
		twms.myLayers = layers;
		twms.myStyles = styles;
		}

	var ol = new IMGTileSet(twms);
	

	ol.myBounds = new google.maps.LatLngBounds();
	ol.myBounds.extend(new google.maps.LatLng(bb.n,bb.e));
	ol.myBounds.extend(new google.maps.LatLng(bb.s,bb.w));

	this.wmscount++;
 	if(this.opts.doMapTypes){
		 
 		var twms2 = new GTileLayer(cc0,z,19);
		twms2.s = bb.s; 
		twms2.n = bb.n;
		twms2.e = bb.e;
		twms2.w = bb.w;
		twms2.myBaseURL = baseurl;
		twms2.servicename = servicename;
		twms2.publishdirectory = this.publishdirectory;
		twms2.getTileUrl = twms.getTileUrl;
		twms2.myFormat =  twms.myFormat;
		twms2.myVersion = version;
		twms2.opacity = 1.0;
		twms2.title = title;
		if(attr) {
			twms2.attribution = attr;
			}
		twms2.getOpacity = function() { return this.opacity; };
		if(sld){
			twms2.mySLD = sld;
			}
		else {
			twms2.myLayers = layers;
			twms2.myStyles = styles;
			}
		twms2.epsg = epsg;
		var base = new GTileLayer(cc0,z,19);
		base.s = bb.s; 
		base.n = bb.n;
		base.e = bb.e;
		base.w = bb.w;  
		base.dir = this.publishdirectory;
		base.getTileUrl = function () {
			return (this.dir +"black.gif");
			};
		base.opacity = 1.0;
		base.title = title;
		if(attr) {
			base.attribution = attr;
			}
		base.getOpacity = function() { return this.opacity; };
		//base,
		var layer = [twms2, G_HYBRID_MAP.getTileLayers()[1]];
		var cmap = new GMapType(layer, G_HYBRID_MAP.getProjection(), ""+title+"", G_HYBRID_MAP);
		cmap.bounds = new google.maps.LatLngBounds(new google.maps.LatLng(bb.s,bb.w),new google.maps.LatLng(bb.n,bb.e));
		if(grouptitle) { cmap.grouptitle = grouptitle; }
		that.baseLayers.push(cmap);
		that.map.addMapType(cmap);
		 
		return null;
		}
	else { return ol; }
	*/
	};


GeoXml.SEMI_MAJOR_AXIS = 6378137.0;
GeoXml.ECCENTRICITY = 0.0818191913108718138;
GeoXml.DEG2RAD = 180.0/(Math.PI);
GeoXml.merc2Lon = function(lon) {
	return (lon*GeoXml.DEG2RAD)*GeoXml.SEMI_MAJOR_AXIS;
	};

GeoXml.merc2Lat = function(lat) {
	var rad = lat * GeoXml.DEG2RAD;
	var sinrad = Math.sin(rad);
	return (GeoXml.SEMI_MAJOR_AXIS * Math.log(Math.tan((rad + Math.PI/2) / 2) * Math.pow(((1 - GeoXml.ECCENTRICITY * sinrad) / (1 + GeoXml.ECCENTRICITY * sinrad)), (GeoXml.ECCENTRICITY/2))));
	};

GeoXml.prototype.toggleLabels = function(on) {
	if(!on) {this.removeLabels();
		}
	else { 
	  	this.addLabels();
		}
	};
GeoXml.prototype.addLabels = function() {
	this.labels.onMap = true;
	this.labels.setMap(this.map); 
	};
 
GeoXml.prototype.removeLabels = function() {
	this.labels.onMap = false;
	this.labels.setMap(null); 
	};

var useLegacyLocalLoad = true;

GeoXml.prototype.DownloadURL = function (fpath,callback,title, xmlcheck){
	if(!fpath){ return; }
	var xmlDoc;
	var that=this;
	var cmlurl = fpath;
	
    if (!topwin.standalone && this.proxy) {
		// Remove http:// because of protection on servers
		cmlurl = cmlurl.replace("http://","");
		cmlurl = cmlurl.replace("http%3A//","");
		cmlurl = cmlurl.replace("https://","");
		cmlurl = cmlurl.replace("https%3A//","");
        cmlurl = this.proxy + "url=" + escape(cmlurl)+this.token;
        }


    if (topwin.standalone || useLegacyLocalLoad) {
        if (cmlurl.substring(2, 3) == ":") {
            xmlDoc = new ActiveXObject("Msxml2.DOMDocument.4.0");
            xmlDoc.validateOnParse = false;
            xmlDoc.async = true;
            xmlDoc.load(cmlurl);
            if (xmlDoc.parseError.errorCode != 0) {
                var myErr = xmlDoc.parseError;
                alert ("GeoXml file appears incorrect\n" + myErr.reason + " at line:" + myErr.line );
                }
            else {
		callback(xmlDoc.doc);
                }
            return;
            }
        }
    var cmlreq;
    /*@cc_on @*/
    /*@if(@_jscript_version>=5)
    try{
    cmlreq=new ActiveXObject("Msxml2.XMLHTTP");
    }catch(e){
    try{
    cmlreq=new ActiveXObject("Microsoft.XMLHTTP");
    }catch(E){
    alert("attempting xmlhttp");
    cmlreq=false;
    }
    }
    @end @*/
    if (! cmlreq && typeof XMLHttpRequest != 'undefined') {
        cmlreq = new XMLHttpRequest();
        }
    else {
        if (typeof ActiveXObject != "undefined") {
            cmlreq = new ActiveXObject("Microsoft.XMLHTTP");
            }
        }

    var here = cmlurl;
    if(cmlreq.overrideMimeType) { cmlreq.overrideMimeType("text/xml"); }
    cmlreq.open("GET", here, true);
    cmlreq.onreadystatechange = function () {
        switch (cmlreq.readyState) {
            case 4:
                that.mb.showMess(title+" received", 2000);
                if (typeof ActiveXObject != "undefined") {
                    xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
                    xmlDoc.async = "false";
                    var response = cmlreq.responseText;
		    callback(response);
                    }
                else {
                    if (cmlreq.responseXML) {
                       that.mb.showMess(title+" received", 2000);
                        callback(cmlreq.responseText);
                        }
                    else {
					  if (cmlreq.status == 200) {
							var resp = cmlreq.responseText;
							var sresp = resp.substring(0, 400);
							var isXML = resp.substring(0, 5);
							if (!xmlcheck||(isXML == "<?xml" && sresp.indexOf("kml")!=-1)) {
								that.mb.showMess(title+" response received", 2000);
								callback(resp);
								}
							else {
								that.mb.showMess("File does not appear to be a valid GeoData"+resp,6000);
								}
						   }
						}
                    }
                break;
            case 3:
                that.mb.showMess("Receiving "+title+"...",2000);
                break;
            case 2:
                that.mb.showMess("Waiting for "+title,2000);
                break;
            case 1:
                that.mb.showMess("Sent request for "+title,2000);
                break;
            }
        };

    try {
        cmlreq.send(null);
        }
    catch (err) {
        if (cmlurl.substring(2, 3) == ":" && ! useLegacyLocalLoad) {
            useLegacyLocalLoad = true;
            this.DownloadURL(cmlurl,callback,title, xmlcheck);
            }
        }

};

GeoXml.Label = function(pos, txt, cls, map, style){
	this.pos = pos;
	this.txt_ = txt;
	this.cls_ = cls;
	this.map_ = map;
	this.style_ = style;
	this.div_ = null;

	// Explicitly call setMap() on this overlay
	this.setMap(map);
	}

GeoXml.Label.prototype = new google.maps.OverlayView();

GeoXml.Label.prototype.onAdd = function(){
	var div = document.createElement('DIV');
	div.innerHTML = this.txt_;
	// Set the overlay's div_ property to this DIV
	this.div_ = div;
	this.div_.style.position = "absolute";
	var overlayProjection = this.getProjection();
	var position = overlayProjection.fromLatLngToDivPixel(this.pos);
//	alert(this.pos);
	this.div_.style.color = this.style_.color;
	this.div_.style.fontSize = this.style_.fontSize;
	this.div_.style.fontFamily = this.style_.fontFamily;
	div.style.left = position.x + 'px';
	div.style.top = position.y + 'px';
	// We add an overlay to a map via one of the map's panes.
	var panes = this.getPanes();
	panes.floatPane.appendChild(div);
}
GeoXml.Label.prototype.getPosition = function () {
	return this.pos;
	}
GeoXml.Label.prototype.draw = function(){


	var overlayProjection = this.getProjection();
	var position = overlayProjection.fromLatLngToDivPixel(this.pos);
	var div = this.div_;
	div.style.left = position.x + 'px';
	div.style.top = position.y + 'px';
	}

GeoXml.Label.prototype.onRemove = function(){
	//console.log("label is being removed");
	this.div_.parentNode.removeChild(this.div_);
	this.div_ = null;
}
GeoXml.Label.prototype.hide = function(){
	if (this.div_) {
		this.div_.style.visibility = "hidden";
		//console.log("label is being hidden");
		}
	}

GeoXml.Label.prototype.show = function(){
	if (this.div_) {
		this.div_.style.visibility = "visible";
	}
}

GeoXml.Label.prototype.toggle = function(){
	if (this.div_) {
		if (this.div_.style.visibility == "hidden") {
			this.show();
		}
		else {
			this.hide();
		}
	}
}

GeoXml.Label.prototype.toggleDOM = function(){
	if (this.getMap()) {
		this.setMap(null);
	}
	else {
		this.setMap(this.map_);
	}
}


GeoXml.tooltip = function(){
	var id = 'tooltip';
	var top = 3;
	var left = 3;
	var maxw = 300;
	var speed = 10;
	var timer = 20;
	var endalpha = 95;
	var alpha = 0;
	var tt,t,c,b,h;
	var ie = document.all ? true : false;
	return{
		show:function(v,w){
			if(tt == null){
				tt = document.createElement('div');
				tt.style.backgroundColor = "white";
				tt.style.padding = "3px";
				tt.style.position = "absolute";
				tt.style.zIndex = 60000;
				tt.style.fontFamily = "Arial,sans-serif";
				tt.style.fontSize = "10px";
				tt.setAttribute('id',id);
				t = document.createElement('div');
				t.setAttribute('id',id + 'top');
				c = document.createElement('div');
				c.setAttribute('id',id + 'cont');
				b = document.createElement('div');
				b.setAttribute('id',id + 'bot');
				tt.appendChild(t);
				tt.appendChild(c);
				tt.appendChild(b);
				document.body.appendChild(tt);
				tt.style.opacity = 0;
				tt.style.filter = 'alpha(opacity=0)';
				document.onmousemove = this.pos;
			}
			tt.style.display = 'block';
			c.innerHTML = v;
			tt.style.width = w ? w + 'px' : 'auto';
			if(!w && ie){
				t.style.display = 'none';
				b.style.display = 'none';
				tt.style.width = tt.offsetWidth;
				t.style.display = 'block';
				b.style.display = 'block';
			}
			if(tt.offsetWidth > maxw){tt.style.width = maxw + 'px';}
			h = parseInt(tt.offsetHeight) + top;
			clearInterval(tt.timer);
			tt.timer = setInterval(function(){GeoXml.tooltip.fade(1);},timer);
		},
		pos:function(e){
			var u = ie ? event.clientY + document.documentElement.scrollTop : e.pageY;
			var l = ie ? event.clientX + document.documentElement.scrollLeft : e.pageX;
			tt.style.top = (u - h) + 'px';
			tt.style.left = (l + left) + 'px';
		},
		fade:function(d){
			var a = alpha;
			if((a != endalpha && d == 1) || (a != 0 && d == -1)){
				var i = speed;
				if(endalpha - a < speed && d == 1){
					i = endalpha - a;
				}else if(alpha < speed && d == -1){
					i = a;
				}
				alpha = a + (i * d);
				tt.style.opacity = alpha * 0.01;
				tt.style.filter = 'alpha(opacity=' + alpha + ')';
			}else{
				clearInterval(tt.timer);
				if(d == -1){tt.style.display = 'none';}
			}
			//console.log(tt.style.opacity);
		},
		hide:function(){
			if(typeof tt != "undefined"){
				if(tt.timer){ clearInterval(tt.timer); }
				tt.timer = setInterval(function(){GeoXml.tooltip.fade(-1);},timer);	
				}
			}
	};
}();
/*jslint browser: true, confusion: true, sloppy: true, vars: true, nomen: false, plusplus: false, indent: 2 */
/*global window,google */

/**
 * @name MarkerClustererPlus for Google Maps V3
 * @version 2.0.15 [October 18, 2012]
 * @author Gary Little
 * @fileoverview
 * The library creates and manages per-zoom-level clusters for large amounts of markers.
 * <p>
 * This is an enhanced V3 implementation of the
 * <a href="http://gmaps-utility-library-dev.googlecode.com/svn/tags/markerclusterer/"
 * >V2 MarkerClusterer</a> by Xiaoxi Wu. It is based on the

 * <a href="http://google-maps-utility-library-v3.googlecode.com/svn/tags/markerclusterer/"
 * >V3 MarkerClusterer</a> port by Luke Mahe. MarkerClustererPlus was created by Gary Little.
 * <p>
 * v2.0 release: MarkerClustererPlus v2.0 is backward compatible with MarkerClusterer v1.0. It
 *  adds support for the <code>ignoreHidden</code>, <code>title</code>, <code>printable</code>,
 *  <code>batchSizeIE</code>, and <code>calculator</code> properties as well as support for
 *  four more events. It also allows greater control over the styling of the text that appears
 *  on the cluster marker. The documentation has been significantly improved and the overall
 *  code has been simplified and polished. Very large numbers of markers can now be managed
 *  without causing Javascript timeout errors on Internet Explorer. Note that the name of the
 *  <code>clusterclick</code> event has been deprecated. The new name is <code>click</code>,
 *  so please change your application code now.
 */

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * @name ClusterIconStyle
 * @class This class represents the object for values in the <code>styles</code> array passed
 *  to the {@link MarkerClusterer} constructor. The element in this array that is used to
 *  style the cluster icon is determined by calling the <code>calculator</code> function.
 *
 * @property {string} url The URL of the cluster icon image file. Required.
 * @property {number} height The height (in pixels) of the cluster icon. Required.
 * @property {number} width The width (in pixels) of the cluster icon. Required.
 * @property {Array} [anchor] The anchor position (in pixels) of the label text to be shown on
 *  the cluster icon, relative to the top left corner of the icon.
 *  The format is <code>[yoffset, xoffset]</code>. The <code>yoffset</code> must be positive
 *  and less than <code>height</code> and the <code>xoffset</code> must be positive and less
 *  than <code>width</code>. The default is to anchor the label text so that it is centered
 *  on the icon.
 * @property {Array} [anchorIcon] The anchor position (in pixels) of the cluster icon. This is the
 *  spot on the cluster icon that is to be aligned with the cluster position. The format is
 *  <code>[yoffset, xoffset]</code> where <code>yoffset</code> increases as you go down and
 *  <code>xoffset</code> increases to the right. The default anchor position is the center of the
 *  cluster icon.
 * @property {string} [textColor="black"] The color of the label text shown on the
 *  cluster icon.
 * @property {number} [textSize=11] The size (in pixels) of the label text shown on the
 *  cluster icon.
 * @property {number} [textDecoration="none"] The value of the CSS <code>text-decoration</code>
 *  property for the label text shown on the cluster icon.
 * @property {number} [fontWeight="bold"] The value of the CSS <code>font-weight</code>
 *  property for the label text shown on the cluster icon.
 * @property {number} [fontStyle="normal"] The value of the CSS <code>font-style</code>
 *  property for the label text shown on the cluster icon.
 * @property {number} [fontFamily="Arial,sans-serif"] The value of the CSS <code>font-family</code>
 *  property for the label text shown on the cluster icon.
 * @property {string} [backgroundPosition="0 0"] The position of the cluster icon image
 *  within the image defined by <code>url</code>. The format is <code>"xpos ypos"</code>
 *  (the same format as for the CSS <code>background-position</code> property). You must set
 *  this property appropriately when the image defined by <code>url</code> represents a sprite
 *  containing multiple images.
 */
/**
 * @name ClusterIconInfo
 * @class This class is an object containing general information about a cluster icon. This is
 *  the object that a <code>calculator</code> function returns.
 *
 * @property {string} text The text of the label to be shown on the cluster icon.
 * @property {number} index The index plus 1 of the element in the <code>styles</code>
 *  array to be used to style the cluster icon.
 * @property {string} title The tooltip to display when the mouse moves over the cluster icon.
 *  If this value is <code>undefined</code> or <code>""</code>, <code>title</code> is set to the
 *  value of the <code>title</code> property passed to the MarkerClusterer.
 */
/**
 * A cluster icon.
 *
 * @constructor
 * @extends google.maps.OverlayView
 * @param {Cluster} cluster The cluster with which the icon is to be associated.
 * @param {Array} [styles] An array of {@link ClusterIconStyle} defining the cluster icons
 *  to use for various cluster sizes.
 * @private
 */
function ClusterIcon(cluster, styles) {
  cluster.getMarkerClusterer().extend(ClusterIcon, google.maps.OverlayView);

  this.cluster_ = cluster;
  this.className_ = cluster.getMarkerClusterer().getClusterClass();
  this.styles_ = styles;
  this.center_ = null;
  this.div_ = null;
  this.sums_ = null;
  this.visible_ = false;

  this.setMap(cluster.getMap()); // Note: this causes onAdd to be called
  this.onMap = true;
}


/**
 * Adds the icon to the DOM.
 */
ClusterIcon.prototype.onAdd = function () {
  var cClusterIcon = this;
  var cMouseDownInCluster;
  var cDraggingMapByCluster;
  var mc = cClusterIcon.cluster_.getMarkerClusterer();
  this.div_ = document.createElement("div");
  this.div_.className = this.className_;
  if (this.visible_) {
    this.show();
  }

  this.getPanes().overlayMouseTarget.appendChild(this.div_);

  // Fix for Issue 157
  google.maps.event.addListener(this.getMap(), "bounds_changed", function () {
    cDraggingMapByCluster = cMouseDownInCluster;
  });

  google.maps.event.addDomListener(this.div_, "mousedown", function () {
    cMouseDownInCluster = true;
    cDraggingMapByCluster = false;
  });
   
  google.maps.event.addDomListener(this.div_, mc.ClusterZoom_, function (e) {
    cMouseDownInCluster = false;
    if (!cDraggingMapByCluster) {
      var theBounds;
      var mz;
      var mc = cClusterIcon.cluster_.getMarkerClusterer();
      /**
       * This event is fired when a cluster marker is clicked.
       * @name MarkerClusterer#click
       * @param {Cluster} c The cluster that was clicked.
       * @event
       */
      google.maps.event.trigger(mc, mc.ClusterZoom_, cClusterIcon.cluster_);
      google.maps.event.trigger(mc, "cluster"+mc.ClusterZoom_, cClusterIcon.cluster_); // deprecated name

      // The default dblclick handler follows. Disable it by setting
      // the zoomOnClick property to false.
      if (mc.getZoomOnClick()) {
        // Zoom into the cluster.
        mz = mc.getMaxZoom();
        theBounds = cClusterIcon.cluster_.getBounds();
        mc.getMap().fitBounds(theBounds);
        // There is a fix for Issue 170 here:
        setTimeout(function () {
          mc.getMap().fitBounds(theBounds);
          // Don't zoom beyond the max zoom level
          if (mz !== null && (mc.getMap().getZoom() > mz)) {
            mc.getMap().setZoom(mz + 1);
          }
        }, 100);
      }

      // Prevent event propagation to the map:
      e.cancelBubble = true;
      if (e.stopPropagation) {
        e.stopPropagation();
      }
    }
  });

  google.maps.event.addDomListener(this.div_, mc.ClusterInfoWindow_, function (e) {
    cMouseDownInCluster = false;
    if (!cDraggingMapByCluster) {
      var theBounds;
      var mz;
      var mc = cClusterIcon.cluster_.getMarkerClusterer();
      /**
       * This event is fired when a cluster marker is clicked.
       * @name MarkerClusterer#click
       * @param {Cluster} c The cluster that was clicked.
       * @event
       */
      google.maps.event.trigger(mc, mc.ClusterInfoWindow_, cClusterIcon.cluster_);
      google.maps.event.trigger(mc, "cluster"+mc.ClusterInfoWindow_, cClusterIcon.cluster_); // deprecated name

      // The default click handler follows. Disable it by setting
	  OverlayManager.PopUp(mc.overlayman, cClusterIcon);

      // Prevent event propagation to the map:
      e.cancelBubble = true;
      if (e.stopPropagation) {
        e.stopPropagation();
      }
    }
  });

  google.maps.event.addDomListener(this.div_, "mouseover", function () {
    var mc = cClusterIcon.cluster_.getMarkerClusterer();
    /**
     * This event is fired when the mouse moves over a cluster marker.
     * @name MarkerClusterer#mouseover
     * @param {Cluster} c The cluster that the mouse moved over.
     * @event
     */
    google.maps.event.trigger(mc, "mouseover", cClusterIcon.cluster_);
  });

  google.maps.event.addDomListener(this.div_, "mouseout", function () {
    var mc = cClusterIcon.cluster_.getMarkerClusterer();
    /**
     * This event is fired when the mouse moves out of a cluster marker.
     * @name MarkerClusterer#mouseout
     * @param {Cluster} c The cluster that the mouse moved out of.
     * @event
     */
    google.maps.event.trigger(mc, "mouseout", cClusterIcon.cluster_);
  });
};


/**
 * Removes the icon from the DOM.
 */
ClusterIcon.prototype.onRemove = function () {
  if (this.div_ && this.div_.parentNode) {
    this.hide();
    google.maps.event.clearInstanceListeners(this.div_);
    this.div_.parentNode.removeChild(this.div_);
    this.div_ = null;
  }
};


/**
 * Draws the icon.
 */
ClusterIcon.prototype.draw = function () {
  if (this.visible_) {
    var pos = this.getPosFromLatLng_(this.center_);
    this.div_.style.top = pos.y + "px";
    this.div_.style.left = pos.x + "px";
  }
};


/**
 * Hides the icon.
 */
ClusterIcon.prototype.hide = function () {
  if (this.div_) {
    this.div_.style.display = "none";
  }
  this.visible_ = false;
};


/**
 * Positions and shows the icon.
 */
ClusterIcon.prototype.show = function () {
  if (this.div_) {
    var pos = this.getPosFromLatLng_(this.center_);
    this.div_.style.cssText = this.createCss(pos);
    if (this.cluster_.printable_) {
      // (Would like to use "width: inherit;" below, but doesn't work with MSIE)
      this.div_.innerHTML = "<img src='" + this.url_ + "'><div style='position: absolute; top: 0px; left: 0px; width: " + this.width_ + "px;'>" + this.sums_.text + "</div>";
    } else {
      this.div_.innerHTML = this.sums_.text;
    }
    if (typeof this.sums_.title === "undefined" || this.sums_.title === "") {
      this.div_.title = this.cluster_.getMarkerClusterer().getTitle();
    } else {
      this.div_.title = this.sums_.title;
    }
    this.div_.style.display = "";
  }
  this.visible_ = true;
};


/**
 * Sets the icon styles to the appropriate element in the styles array.
 *
 * @param {ClusterIconInfo} sums The icon label text and styles index.
 */
ClusterIcon.prototype.useStyle = function (sums) {
  this.sums_ = sums;
  var index = Math.max(0, sums.index - 1);
  index = Math.min(this.styles_.length - 1, index);
  var style = this.styles_[index];
  this.url_ = style.url;
  this.height_ = style.height;
  this.width_ = style.width;
  this.anchor_ = style.anchor;
  this.anchorIcon_ = style.anchorIcon || [parseInt(this.height_ / 2, 10), parseInt(this.width_ / 2, 10)];
  this.textColor_ = style.textColor || "black";
  this.textSize_ = style.textSize || 11;
  this.textDecoration_ = style.textDecoration || "none";
  this.fontWeight_ = style.fontWeight || "bold";
  this.fontStyle_ = style.fontStyle || "normal";
  this.fontFamily_ = style.fontFamily || "Arial,sans-serif";
  this.backgroundPosition_ = style.backgroundPosition || "0 0";
};


/**
 * Sets the position at which to center the icon.
 *
 * @param {google.maps.LatLng} center The latlng to set as the center.
 */
ClusterIcon.prototype.setCenter = function (center) {
  this.center_ = center;
};


/**
 * Creates the cssText style parameter based on the position of the icon.
 *
 * @param {google.maps.Point} pos The position of the icon.
 * @return {string} The CSS style text.
 */
ClusterIcon.prototype.createCss = function (pos) {
  var style = [];
  if (!this.cluster_.printable_) {
    style.push('background-image:url(' + this.url_ + ');');
    style.push('background-position:' + this.backgroundPosition_ + ';');
  }

  if (typeof this.anchor_ === 'object') {
    if (typeof this.anchor_[0] === 'number' && this.anchor_[0] > 0 &&
        this.anchor_[0] < this.height_) {
      style.push('height:' + (this.height_ - this.anchor_[0]) +
          'px; padding-top:' + this.anchor_[0] + 'px;');
    } else {
      style.push('height:' + this.height_ + 'px; line-height:' + this.height_ +
          'px;');
    }
    if (typeof this.anchor_[1] === 'number' && this.anchor_[1] > 0 &&
        this.anchor_[1] < this.width_) {
      style.push('width:' + (this.width_ - this.anchor_[1]) +
          'px; padding-left:' + this.anchor_[1] + 'px;');
    } else {
      style.push('width:' + this.width_ + 'px; text-align:center;');
    }
  } else {
    style.push('height:' + this.height_ + 'px; line-height:' +
        this.height_ + 'px; width:' + this.width_ + 'px; text-align:center;');
  }

  style.push('cursor:pointer; top:' + pos.y + 'px; left:' +
      pos.x + 'px; color:' + this.textColor_ + '; position:absolute; font-size:' +
      this.textSize_ + 'px; font-family:' + this.fontFamily_ + '; font-weight:' +
      this.fontWeight_ + '; font-style:' + this.fontStyle_ + '; text-decoration:' +
      this.textDecoration_ + ';');

  return style.join("");
};


/**
 * Returns the position at which to place the DIV depending on the latlng.
 *
 * @param {google.maps.LatLng} latlng The position in latlng.
 * @return {google.maps.Point} The position in pixels.
 */
ClusterIcon.prototype.getPosFromLatLng_ = function (latlng) {
  var pos = this.getProjection().fromLatLngToDivPixel(latlng);
  pos.x -= this.anchorIcon_[1];
  pos.y -= this.anchorIcon_[0];
  return pos;
};


/**
 * Creates a single cluster that manages a group of proximate markers.
 *  Used internally, do not call this constructor directly.
 * @constructor
 * @param {MarkerClusterer} mc The <code>MarkerClusterer</code> object with which this
 *  cluster is associated.
 */
function Cluster(mc) {
  this.markerClusterer_ = mc;
  this.map_ = mc.getMap();
  this.gridSize_ = mc.getGridSize();
  this.minClusterSize_ = mc.getMinimumClusterSize();
  this.averageCenter_ = mc.getAverageCenter();
  this.printable_ = mc.getPrintable();
  this.markers_ = [];
  this.center_ = null;
  this.bounds_ = null;
  this.clusterIcon_ = new ClusterIcon(this, mc.getStyles());
}


/**
 * Returns the number of markers managed by the cluster. You can call this from
 * a <code>click</code>, <code>mouseover</code>, or <code>mouseout</code> event handler
 * for the <code>MarkerClusterer</code> object.
 *
 * @return {number} The number of markers in the cluster.
 */
Cluster.prototype.getSize = function () {
  return this.markers_.length;
};


/**
 * Returns the array of markers managed by the cluster. You can call this from
 * a <code>click</code>, <code>mouseover</code>, or <code>mouseout</code> event handler
 * for the <code>MarkerClusterer</code> object.
 *
 * @return {Array} The array of markers in the cluster.
 */
Cluster.prototype.getMarkers = function () {
  return this.markers_;
};


/**
 * Returns the center of the cluster. You can call this from
 * a <code>click</code>, <code>mouseover</code>, or <code>mouseout</code> event handler
 * for the <code>MarkerClusterer</code> object.
 *
 * @return {google.maps.LatLng} The center of the cluster.
 */
Cluster.prototype.getCenter = function () {
  return this.center_;
};


/**
 * Returns the map with which the cluster is associated.
 *
 * @return {google.maps.Map} The map.
 * @ignore
 */
Cluster.prototype.getMap = function () {
  return this.map_;
};


/**
 * Returns the <code>MarkerClusterer</code> object with which the cluster is associated.
 *
 * @return {MarkerClusterer} The associated marker clusterer.
 * @ignore
 */
Cluster.prototype.getMarkerClusterer = function () {
  return this.markerClusterer_;
};


/**
 * Returns the bounds of the cluster.
 *
 * @return {google.maps.LatLngBounds} the cluster bounds.
 * @ignore
 */
Cluster.prototype.getBounds = function () {
  var i;
  var bounds = new google.maps.LatLngBounds(this.center_, this.center_);
  var markers = this.getMarkers();
  for (i = 0; i < markers.length; i++) {
    bounds.extend(markers[i].getPosition());
  }
  return bounds;
};


/**
 * Removes the cluster from the map.
 *
 * @ignore
 */
Cluster.prototype.remove = function () {
  if (this.clusterIcon_.infoWindow)
	  this.clusterIcon_.infoWindow.close();
  this.clusterIcon_.setMap(null);
  this.clusterIcon_.onMap = false;
  this.markers_ = [];
  delete this.markers_;
};


/**
 * Adds a marker to the cluster.
 *
 * @param {google.maps.Marker} marker The marker to be added.
 * @return {boolean} True if the marker was added.
 * @ignore
 */
Cluster.prototype.addMarker = function (marker) {
  var i;
  var mCount;
  var mz;

  if (this.isMarkerAlreadyAdded_(marker)) {
    return false;
  }

  if (!this.center_) {
    this.center_ = marker.getPosition();
    this.calculateBounds_();
  } else {
    if (this.averageCenter_) {
      var l = this.markers_.length + 1;
      var lat = (this.center_.lat() * (l - 1) + marker.getPosition().lat()) / l;
      var lng = (this.center_.lng() * (l - 1) + marker.getPosition().lng()) / l;
      this.center_ = new google.maps.LatLng(lat, lng);
      this.calculateBounds_();
    }
  }

  marker.isAdded = true;
  this.markers_.push(marker);

  mCount = this.markers_.length;
  mz = this.markerClusterer_.getMaxZoom();
  if (mz !== null && this.map_.getZoom() > mz) {
    // Zoomed in past max zoom, so show the marker.
    if (marker.getMap() !== this.map_ && !marker.hidden) {
      marker.setMap(this.map_);
	  if(!!marker.label){ 
	  	marker.label.setMap(this.map_);
	  } 
    }
  } else if (mCount < this.minClusterSize_) {
    // Min cluster size not reached so show the marker.
    if (marker.getMap() !== this.map_&& !marker.hidden) {
      marker.setMap(this.map_);
	  if(!!marker.label){ 
	  	marker.label.setMap(this.map_);
	  } 
    }
  } else if (mCount === this.minClusterSize_) {
    // Hide the markers that were showing.
    for (i = 0; i < mCount; i++) {
      this.markers_[i].setMap(null);
	  if(!!marker.label){ 
	  	marker.label.setMap(null);
	  } 
    }
  } else {
    marker.setMap(null);
	if(!!marker.label){ 
	  	marker.label.setMap(null);
	} 
  }

  this.updateIcon_();
  return true;
};


/**
 * Determines if a marker lies within the cluster's bounds.
 *
 * @param {google.maps.Marker} marker The marker to check.
 * @return {boolean} True if the marker lies in the bounds.
 * @ignore
 */
Cluster.prototype.isMarkerInClusterBounds = function (marker) {
  return this.bounds_.contains(marker.getPosition());
};


/**
 * Calculates the extended bounds of the cluster with the grid.
 */
Cluster.prototype.calculateBounds_ = function () {
  var bounds = new google.maps.LatLngBounds(this.center_, this.center_);
  this.bounds_ = this.markerClusterer_.getExtendedBounds(bounds);
};


/**
 * Updates the cluster icon.
 */
Cluster.prototype.updateIcon_ = function () {
  var mCount = this.markers_.length;
  var mz = this.markerClusterer_.getMaxZoom();

  if (mz !== null && this.map_.getZoom() > mz) {
    this.clusterIcon_.hide();
    return;
  }

  if (mCount < this.minClusterSize_) {
    // Min cluster size not yet reached.
    this.clusterIcon_.hide();
    return;
  }

  var numStyles = this.markerClusterer_.getStyles().length;
  var sums = this.markerClusterer_.getCalculator()(this.markers_, numStyles);
  this.clusterIcon_.setCenter(this.center_);
  this.clusterIcon_.useStyle(sums);
  this.clusterIcon_.show();
};


/**
 * Determines if a marker has already been added to the cluster.
 *
 * @param {google.maps.Marker} marker The marker to check.
 * @return {boolean} True if the marker has already been added.
 */
Cluster.prototype.isMarkerAlreadyAdded_ = function (marker) {
  var i;
  if (this.markers_.indexOf) {
    return this.markers_.indexOf(marker) !== -1;
  } else {
    for (i = 0; i < this.markers_.length; i++) {
      if (marker === this.markers_[i]) {
        return true;
      }
    }
  }
  return false;
};


/**
 * @name MarkerClustererOptions
 * @class This class represents the optional parameter passed to
 *  the {@link MarkerClusterer} constructor.

 * @property {number} [gridSize=60] The grid size of a cluster in pixels. The grid is a square.
 * @property {number} [maxZoom=null] The maximum zoom level at which clustering is enabled or
 *  <code>null</code> if clustering is to be enabled at all zoom levels.
 * @property {boolean} [zoomOnClick=true] Whether to zoom the map when a cluster marker is
 *  clicked. You may want to set this to <code>false</code> if you have installed a handler
 *  for the <code>click</code> event and it deals with zooming on its own.
 * @property {boolean} [averageCenter=false] Whether the position of a cluster marker should be
 *  the average position of all markers in the cluster. If set to <code>false</code>, the
 *  cluster marker is positioned at the location of the first marker added to the cluster.
 * @property {number} [minimumClusterSize=2] The minimum number of markers needed in a cluster
 *  before the markers are hidden and a cluster marker appears.
 * @property {boolean} [ignoreHidden=false] Whether to ignore hidden markers in clusters. You
 *  may want to set this to <code>true</code> to ensure that hidden markers are not included
 *  in the marker count that appears on a cluster marker (this count is the value of the
 *  <code>text</code> property of the result returned by the default <code>calculator</code>).
 *  If set to <code>true</code> and you change the visibility of a marker being clustered, be
 *  sure to also call <code>MarkerClusterer.repaint()</code>.
 * @property {boolean} [printable=false] Whether to make the cluster icons printable. Do not
 *  set to <code>true</code> if the <code>url</code> fields in the <code>styles</code> array
 *  refer to image sprite files.
 * @property {string} [title=""] The tooltip to display when the mouse moves over a cluster
 *  marker. (Alternatively, you can use a custom <code>calculator</code> function to specify a
 *  different tooltip for each cluster marker.)
 * @property {function} [calculator=MarkerClusterer.CALCULATOR] The function used to determine
 *  the text to be displayed on a cluster marker and the index indicating which style to use
 *  for the cluster marker. The input parameters for the function are (1) the array of markers
 *  represented by a cluster marker and (2) the number of cluster icon styles. It returns a
 *  {@link ClusterIconInfo} object. The default <code>calculator</code> returns a
 *  <code>text</code> property which is the number of markers in the cluster and an
 *  <code>index</code> property which is one higher than the lowest integer such that
 *  <code>10^i</code> exceeds the number of markers in the cluster, or the size of the styles
 *  array, whichever is less. The <code>styles</code> array element used has an index of
 *  <code>index</code> minus 1. For example, the default <code>calculator</code> returns a
 *  <code>text</code> value of <code>"125"</code> and an <code>index</code> of <code>3</code>
 *  for a cluster icon representing 125 markers so the element used in the <code>styles</code>
 *  array is <code>2</code>. A <code>calculator</code> may also return a <code>title</code>
 *  property that contains the text of the tooltip to be used for the cluster marker. If
 *   <code>title</code> is not defined, the tooltip is set to the value of the <code>title</code>
 *   property for the MarkerClusterer.
 * @property {string} [clusterClass="cluster"] The name of the CSS class defining general styles
 *  for the cluster markers. Use this class to define CSS styles that are not set up by the code
 *  that processes the <code>styles</code> array.
 * @property {Array} [styles] An array of {@link ClusterIconStyle} elements defining the styles
 *  of the cluster markers to be used. The element to be used to style a given cluster marker
 *  is determined by the function defined by the <code>calculator</code> property.
 *  The default is an array of {@link ClusterIconStyle} elements whose properties are derived
 *  from the values for <code>imagePath</code>, <code>imageExtension</code>, and
 *  <code>imageSizes</code>.
 * @property {number} [batchSize=MarkerClusterer.BATCH_SIZE] Set this property to the
 *  number of markers to be processed in a single batch when using a browser other than
 *  Internet Explorer (for Internet Explorer, use the batchSizeIE property instead).
 * @property {number} [batchSizeIE=MarkerClusterer.BATCH_SIZE_IE] When Internet Explorer is
 *  being used, markers are processed in several batches with a small delay inserted between
 *  each batch in an attempt to avoid Javascript timeout errors. Set this property to the
 *  number of markers to be processed in a single batch; select as high a number as you can
 *  without causing a timeout error in the browser. This number might need to be as low as 100
 *  if 15,000 markers are being managed, for example.
 * @property {string} [imagePath=MarkerClusterer.IMAGE_PATH]
 *  The full URL of the root name of the group of image files to use for cluster icons.
 *  The complete file name is of the form <code>imagePath</code>n.<code>imageExtension</code>
 *  where n is the image file number (1, 2, etc.).
 * @property {string} [imageExtension=MarkerClusterer.IMAGE_EXTENSION]
 *  The extension name for the cluster icon image files (e.g., <code>"png"</code> or
 *  <code>"jpg"</code>).
 * @property {Array} [imageSizes=MarkerClusterer.IMAGE_SIZES]
 *  An array of numbers containing the widths of the group of
 *  <code>imagePath</code>n.<code>imageExtension</code> image files.
 *  (The images are assumed to be square.)
 */
/**
 * Creates a MarkerClusterer object with the options specified in {@link MarkerClustererOptions}.
 * @constructor
 * @extends google.maps.OverlayView
 * @param {google.maps.Map} map The Google map to attach to.
 * @param {Array.<google.maps.Marker>} [opt_markers] The markers to be added to the cluster.
 * @param {MarkerClustererOptions} [opt_options] The optional parameters.
 */
function MarkerClusterer(map, opt_markers, opt_options, paren) {
  // MarkerClusterer implements google.maps.OverlayView interface. We use the
  // extend function to extend MarkerClusterer with google.maps.OverlayView
  // because it might not always be available when the code is defined so we
  // look for it at the last possible moment. If it doesn't exist now then
  // there is no point going ahead :)
  this.extend(MarkerClusterer, google.maps.OverlayView);

  opt_markers = opt_markers || [];
  opt_options = opt_options || {};
  this.paren = this.paren; 
  this.markers_ = [];
  this.clusters_ = [];
  this.listeners_ = [];
  this.activeMap_ = null;
  this.ready_ = false;

  this.overlayman = opt_options.overlayman;

  this.gridSize_ = opt_options.gridSize || 60;
  this.minClusterSize_ = opt_options.minimumClusterSize || 2;
  this.maxZoom_ = opt_options.maxZoom || null;
  this.ClusterZoom_ = opt_options.ClusterZoom || "dblclick";
  this.ClusterInfoWindow_ = opt_options.ClusterInfoWindow || "click";
  
  this.styles_ = opt_options.styles || [];
  this.title_ = opt_options.title || "";
  this.zoomOnClick_ = true;
  if (opt_options.zoomOnClick !== undefined) {
    this.zoomOnClick_ = opt_options.zoomOnClick;
  }
  this.averageCenter_ = false;
  if (opt_options.averageCenter !== undefined) {
    this.averageCenter_ = opt_options.averageCenter;
  }
  this.ignoreHidden_ = false;
  if (opt_options.ignoreHidden !== undefined) {
    this.ignoreHidden_ = opt_options.ignoreHidden;
  }
  this.printable_ = false;
  if (opt_options.printable !== undefined) {
    this.printable_ = opt_options.printable;
  }
  this.imagePath_ = opt_options.imagePath || MarkerClusterer.IMAGE_PATH;
  this.imageExtension_ = opt_options.imageExtension || MarkerClusterer.IMAGE_EXTENSION;
  this.imageSizes_ = opt_options.imageSizes || MarkerClusterer.IMAGE_SIZES;
  this.calculator_ = opt_options.calculator || MarkerClusterer.CALCULATOR;
  this.batchSize_ = opt_options.batchSize || MarkerClusterer.BATCH_SIZE;
  this.batchSizeIE_ = opt_options.batchSizeIE || MarkerClusterer.BATCH_SIZE_IE;
  this.clusterClass_ = opt_options.clusterClass || "cluster";

  if (navigator.userAgent.toLowerCase().indexOf("msie") !== -1) {
    // Try to avoid IE timeout when processing a huge number of markers:
    this.batchSize_ = this.batchSizeIE_;
  }

  this.setupStyles_();

  this.addMarkers(opt_markers, true);
  this.setMap(map); // Note: this causes onAdd to be called
}


/**
 * Implementation of the onAdd interface method.
 * @ignore
 */
MarkerClusterer.prototype.onAdd = function () {
  var cMarkerClusterer = this;

  this.activeMap_ = this.getMap();
  this.ready_ = true;

  this.repaint();

  // Add the map event listeners
  // Mike: For integration leave the zoom_changed to geoxml handleing
//  this.listeners_ = [
//    google.maps.event.addListener(this.getMap(), "zoom_changed", function () {
//      cMarkerClusterer.resetViewport_(false);
//      // Workaround for this Google bug: when map is at level 0 and "-" of
//      // zoom slider is clicked, a "zoom_changed" event is fired even though
//      // the map doesn't zoom out any further. In this situation, no "idle"
//      // event is triggered so the cluster markers that have been removed
//      // do not get redrawn. Same goes for a zoom in at maxZoom.
//      if (this.getZoom() === (this.get("minZoom") || 0) || this.getZoom() === this.get("maxZoom")) {
//        google.maps.event.trigger(this, "idle");
//      }
//    }),
//    google.maps.event.addListener(this.getMap(), "idle", function () {
//      cMarkerClusterer.redraw_();
//    })
//  ];
  this.listeners_ = [
    google.maps.event.addListener(this.getMap(), "idle", function () {
      cMarkerClusterer.redraw_(); 
    })
  ];
};


/**
 * Implementation of the onRemove interface method.
 * Removes map event listeners and all cluster icons from the DOM.
 * All managed markers are also put back on the map.
 * @ignore
 */
MarkerClusterer.prototype.onRemove = function () {
  var i;

  // Put all the managed markers back on the map:
  for (i = 0; i < this.markers_.length; i++) {
    if (this.markers_[i].getMap() !== this.activeMap_) {
      this.markers_[i].setMap(this.activeMap_);
  	  if(!!this.markers_[i].label){ 
	  	this.markers_[i].label.setMap(this.activeMap_);
	  } 
    }
  }

  // Remove all clusters:
  for (i = 0; i < this.clusters_.length; i++) {
    this.clusters_[i].remove();
  }
  this.clusters_ = [];

  // Remove map event listeners:
  for (i = 0; i < this.listeners_.length; i++) {
    google.maps.event.removeListener(this.listeners_[i]);
  }
  this.listeners_ = [];

  this.activeMap_ = null;
  this.ready_ = false;
};


/**
 * Implementation of the draw interface method.
 * @ignore
 */
MarkerClusterer.prototype.draw = function () {};


/**
 * Sets up the styles object.
 */
MarkerClusterer.prototype.setupStyles_ = function () {
  var i, size;
  if (this.styles_.length > 0) {
    return;
  }

  for (i = 0; i < this.imageSizes_.length; i++) {
    size = this.imageSizes_[i];
    this.styles_.push({
      url: this.imagePath_ + (i + 1) + "." + this.imageExtension_,
      height: size,
      width: size
    });
  }
};


/**
 *  Fits the map to the bounds of the markers managed by the clusterer.
 */
MarkerClusterer.prototype.fitMapToMarkers = function () {
  var i;
  var markers = this.getMarkers();
  var bounds = new google.maps.LatLngBounds();
  for (i = 0; i < markers.length; i++) {
    bounds.extend(markers[i].getPosition());
  }

  this.getMap().fitBounds(bounds);
};


/**
 * Returns the value of the <code>gridSize</code> property.
 *
 * @return {number} The grid size.
 */
MarkerClusterer.prototype.getGridSize = function () {
  return this.gridSize_;
};



/**
 * Sets the value of the <code>gridSize</code> property.
 *
 * @param {number} gridSize The grid size.
 */
MarkerClusterer.prototype.setGridSize = function (gridSize) {
  this.gridSize_ = gridSize;
};


/**
 * Returns the value of the <code>minimumClusterSize</code> property.
 *
 * @return {number} The minimum cluster size.
 */
MarkerClusterer.prototype.getMinimumClusterSize = function () {
  return this.minClusterSize_;
};

/**
 * Sets the value of the <code>minimumClusterSize</code> property.
 *
 * @param {number} minimumClusterSize The minimum cluster size.
 */
MarkerClusterer.prototype.setMinimumClusterSize = function (minimumClusterSize) {
  this.minClusterSize_ = minimumClusterSize;
};


/**
 *  Returns the value of the <code>maxZoom</code> property.
 *
 *  @return {number} The maximum zoom level.
 */
MarkerClusterer.prototype.getMaxZoom = function () {
  return this.maxZoom_;
};


/**
 *  Sets the value of the <code>maxZoom</code> property.
 *
 *  @param {number} maxZoom The maximum zoom level.
 */
MarkerClusterer.prototype.setMaxZoom = function (maxZoom) {
  this.maxZoom_ = maxZoom;
};


/**
 *  Returns the value of the <code>styles</code> property.
 *
 *  @return {Array} The array of styles defining the cluster markers to be used.
 */
MarkerClusterer.prototype.getStyles = function () {
  return this.styles_;
};


/**
 *  Sets the value of the <code>styles</code> property.
 *
 *  @param {Array.<ClusterIconStyle>} styles The array of styles to use.
 */
MarkerClusterer.prototype.setStyles = function (styles) {
  this.styles_ = styles;
};


/**
 * Returns the value of the <code>title</code> property.
 *
 * @return {string} The content of the title text.
 */
MarkerClusterer.prototype.getTitle = function () {
  return this.title_;
};


/**
 *  Sets the value of the <code>title</code> property.
 *
 *  @param {string} title The value of the title property.
 */
MarkerClusterer.prototype.setTitle = function (title) {
  this.title_ = title;
};


/**
 * Returns the value of the <code>zoomOnClick</code> property.
 *
 * @return {boolean} True if zoomOnClick property is set.
 */
MarkerClusterer.prototype.getZoomOnClick = function () {
  return this.zoomOnClick_;
};


/**
 *  Sets the value of the <code>zoomOnClick</code> property.
 *
 *  @param {boolean} zoomOnClick The value of the zoomOnClick property.
 */
MarkerClusterer.prototype.setZoomOnClick = function (zoomOnClick) {
  this.zoomOnClick_ = zoomOnClick;
};


/**
 * Returns the value of the <code>averageCenter</code> property.
 *
 * @return {boolean} True if averageCenter property is set.
 */
MarkerClusterer.prototype.getAverageCenter = function () {
  return this.averageCenter_;
};


/**
 *  Sets the value of the <code>averageCenter</code> property.
 *
 *  @param {boolean} averageCenter The value of the averageCenter property.
 */
MarkerClusterer.prototype.setAverageCenter = function (averageCenter) {
  this.averageCenter_ = averageCenter;
};


/**
 * Returns the value of the <code>ignoreHidden</code> property.
 *
 * @return {boolean} True if ignoreHidden property is set.
 */
MarkerClusterer.prototype.getIgnoreHidden = function () {
  return this.ignoreHidden_;
};


/**
 *  Sets the value of the <code>ignoreHidden</code> property.
 *
 *  @param {boolean} ignoreHidden The value of the ignoreHidden property.
 */
MarkerClusterer.prototype.setIgnoreHidden = function (ignoreHidden) {
  this.ignoreHidden_ = ignoreHidden;
};


/**
 * Returns the value of the <code>imageExtension</code> property.
 *
 * @return {string} The value of the imageExtension property.
 */
MarkerClusterer.prototype.getImageExtension = function () {
  return this.imageExtension_;
};


/**
 *  Sets the value of the <code>imageExtension</code> property.
 *
 *  @param {string} imageExtension The value of the imageExtension property.
 */
MarkerClusterer.prototype.setImageExtension = function (imageExtension) {
  this.imageExtension_ = imageExtension;
};


/**
 * Returns the value of the <code>imagePath</code> property.
 *
 * @return {string} The value of the imagePath property.
 */
MarkerClusterer.prototype.getImagePath = function () {
  return this.imagePath_;
};


/**
 *  Sets the value of the <code>imagePath</code> property.
 *
 *  @param {string} imagePath The value of the imagePath property.
 */
MarkerClusterer.prototype.setImagePath = function (imagePath) {
  this.imagePath_ = imagePath;
};


/**
 * Returns the value of the <code>imageSizes</code> property.
 *
 * @return {Array} The value of the imageSizes property.
 */
MarkerClusterer.prototype.getImageSizes = function () {
  return this.imageSizes_;
};

/**
 *  Sets the value of the <code>imageSizes</code> property.

 *
 *  @param {Array} imageSizes The value of the imageSizes property.
 */
MarkerClusterer.prototype.setImageSizes = function (imageSizes) {
  this.imageSizes_ = imageSizes;
};


/**
 * Returns the value of the <code>calculator</code> property.
 *
 * @return {function} the value of the calculator property.
 */
MarkerClusterer.prototype.getCalculator = function () {
  return this.calculator_;
};


/**
 * Sets the value of the <code>calculator</code> property.
 *
 * @param {function(Array.<google.maps.Marker>, number)} calculator The value
 *  of the calculator property.
 */
MarkerClusterer.prototype.setCalculator = function (calculator) {
  this.calculator_ = calculator;
};


/**
 * Returns the value of the <code>printable</code> property.
 *
 * @return {boolean} the value of the printable property.
 */
MarkerClusterer.prototype.getPrintable = function () {
  return this.printable_;
};


/**
 * Sets the value of the <code>printable</code> property.
 *
 *  @param {boolean} printable The value of the printable property.
 */
MarkerClusterer.prototype.setPrintable = function (printable) {
  this.printable_ = printable;
};


/**
 * Returns the value of the <code>batchSizeIE</code> property.
 *
 * @return {number} the value of the batchSizeIE property.
 */
MarkerClusterer.prototype.getBatchSizeIE = function () {
  return this.batchSizeIE_;
};


/**
 * Sets the value of the <code>batchSizeIE</code> property.
 *
 *  @param {number} batchSizeIE The value of the batchSizeIE property.
 */
MarkerClusterer.prototype.setBatchSizeIE = function (batchSizeIE) {
  this.batchSizeIE_ = batchSizeIE;
};


/**
 * Returns the value of the <code>clusterClass</code> property.
 *
 * @return {string} the value of the clusterClass property.
 */
MarkerClusterer.prototype.getClusterClass = function () {
  return this.clusterClass_;
};


/**
 * Sets the value of the <code>clusterClass</code> property.
 *
 *  @param {string} clusterClass The value of the clusterClass property.
 */
MarkerClusterer.prototype.setClusterClass = function (clusterClass) {
  this.clusterClass_ = clusterClass;
};


/**
 *  Returns the array of markers managed by the clusterer.
 *
 *  @return {Array} The array of markers managed by the clusterer.
 */
MarkerClusterer.prototype.getMarkers = function () {
  return this.markers_;
};


/**
 *  Returns the number of markers managed by the clusterer.
 *
 *  @return {number} The number of markers.
 */
MarkerClusterer.prototype.getTotalMarkers = function () {
  return this.markers_.length;
};


/**
 * Returns the current array of clusters formed by the clusterer.
 *
 * @return {Array} The array of clusters formed by the clusterer.
 */
MarkerClusterer.prototype.getClusters = function () {
  return this.clusters_;
};


/**
 * Returns the number of clusters formed by the clusterer.
 *
 * @return {number} The number of clusters formed by the clusterer.
 */
MarkerClusterer.prototype.getTotalClusters = function () {
  return this.clusters_.length;
};


/**
 * Adds a marker to the clusterer. The clusters are redrawn unless
 *  <code>opt_nodraw</code> is set to <code>true</code>.
 *
 * @param {google.maps.Marker} marker The marker to add.
 * @param {boolean} [opt_nodraw] Set to <code>true</code> to prevent redrawing.
 */
MarkerClusterer.prototype.addMarker = function (marker, opt_nodraw) {
  this.pushMarkerTo_(marker);
  if (!opt_nodraw) {
    this.redraw_();
  }
};


/**
 * Adds an array of markers to the clusterer. The clusters are redrawn unless
 *  <code>opt_nodraw</code> is set to <code>true</code>.
 *
 * @param {Array.<google.maps.Marker>} markers The markers to add.
 * @param {boolean} [opt_nodraw] Set to <code>true</code> to prevent redrawing.
 */
MarkerClusterer.prototype.addMarkers = function (markers, opt_nodraw) {
  var i;
  for (i = 0; i < markers.length; i++) {
    this.pushMarkerTo_(markers[i]);
  }
  if (!opt_nodraw) {
    this.redraw_();
  }
};


/**
 * Pushes a marker to the clusterer.
 *
 * @param {google.maps.Marker} marker The marker to add.
 */
MarkerClusterer.prototype.pushMarkerTo_ = function (marker) {
  // If the marker is draggable add a listener so we can update the clusters on the dragend:
  if (typeof marker.getDraggable == 'function'&&marker.getDraggable()) {
    var cMarkerClusterer = this;
    google.maps.event.addListener(marker, "dragend", function () {
      if (cMarkerClusterer.ready_) {
        this.isAdded = false;
        cMarkerClusterer.repaint();
      }
    });
  }
  marker.isAdded = false;
  this.markers_.push(marker);
};


/**
 * Removes a marker from the cluster.  The clusters are redrawn unless
 *  <code>opt_nodraw</code> is set to <code>true</code>. Returns <code>true</code> if the
 *  marker was removed from the clusterer.
 *
 * @param {google.maps.Marker} marker The marker to remove.
 * @param {boolean} [opt_nodraw] Set to <code>true</code> to prevent redrawing.
 * @return {boolean} True if the marker was removed from the clusterer.
 */
MarkerClusterer.prototype.removeMarker = function (marker, opt_nodraw) {
  var removed = this.removeMarker_(marker);

  if (!opt_nodraw && removed) {
    this.repaint();
  }

  return removed;
};


/**
 * Removes an array of markers from the cluster. The clusters are redrawn unless
 *  <code>opt_nodraw</code> is set to <code>true</code>. Returns <code>true</code> if markers
 *  were removed from the clusterer.
 *
 * @param {Array.<google.maps.Marker>} markers The markers to remove.
 * @param {boolean} [opt_nodraw] Set to <code>true</code> to prevent redrawing.
 * @return {boolean} True if markers were removed from the clusterer.
 */
MarkerClusterer.prototype.removeMarkers = function (markers, opt_nodraw) {
  var i, r;
  var removed = false;

  for (i = 0; i < markers.length; i++) {
    r = this.removeMarker_(markers[i]);
    removed = removed || r;
  }

  if (!opt_nodraw && removed) {
    this.repaint();
  }

  return removed;
};


/**
 * Removes a marker and returns true if removed, false if not.
 *
 * @param {google.maps.Marker} marker The marker to remove
 * @return {boolean} Whether the marker was removed or not
 */
MarkerClusterer.prototype.removeMarker_ = function (marker) {
  var i;
  var index = -1;
  if (this.markers_.indexOf) {
    index = this.markers_.indexOf(marker);
  } else {
    for (i = 0; i < this.markers_.length; i++) {
      if (marker === this.markers_[i]) {
        index = i;
        break;
      }
    }
  }

  if (index === -1) {
    // Marker is not in our list of markers, so do nothing:
    return false;
  }

  marker.setMap(null);
  if(!!marker.label){ 
	  	marker.label.setMap(null);
  } 

  this.markers_.splice(index, 1); // Remove the marker from the list of managed markers
  return true;
};


/**
 * Removes all clusters and markers from the map and also removes all markers
 *  managed by the clusterer.
 */
MarkerClusterer.prototype.clearMarkers = function () {
  this.resetViewport_(true);
  this.markers_ = [];
};


/**
 * Recalculates and redraws all the marker clusters from scratch.
 *  Call this after changing any properties.
 */
MarkerClusterer.prototype.repaint = function () {
  var oldClusters = this.clusters_.slice();
  this.clusters_ = [];
  this.resetViewport_(false);
  this.redraw_();

  // Remove the old clusters.
  // Do it in a timeout to prevent blinking effect.
  setTimeout(function () {
    var i;
    for (i = 0; i < oldClusters.length; i++) {
      oldClusters[i].remove();
    }
  }, 0);
};


/**
 * Returns the current bounds extended by the grid size.
 *
 * @param {google.maps.LatLngBounds} bounds The bounds to extend.
 * @return {google.maps.LatLngBounds} The extended bounds.
 * @ignore
 */
MarkerClusterer.prototype.getExtendedBounds = function (bounds) {
  var projection = this.getProjection();

  // Turn the bounds into latlng.
  var tr = new google.maps.LatLng(bounds.getNorthEast().lat(),
      bounds.getNorthEast().lng());
  var bl = new google.maps.LatLng(bounds.getSouthWest().lat(),
      bounds.getSouthWest().lng());

  // Convert the points to pixels and the extend out by the grid size.
  var trPix = projection.fromLatLngToDivPixel(tr);
  trPix.x += this.gridSize_;
  trPix.y -= this.gridSize_;

  var blPix = projection.fromLatLngToDivPixel(bl);
  blPix.x -= this.gridSize_;
  blPix.y += this.gridSize_;

  // Convert the pixel points back to LatLng
  var ne = projection.fromDivPixelToLatLng(trPix);
  var sw = projection.fromDivPixelToLatLng(blPix);

  // Extend the bounds to contain the new bounds.
  bounds.extend(ne);
  bounds.extend(sw);

  return bounds;
};


/**
 * Redraws all the clusters.
 */
MarkerClusterer.prototype.redraw_ = function () {
  this.createClusters_(0);
};


/**
 * Removes all clusters from the map. The markers are also removed from the map
 *  if <code>opt_hide</code> is set to <code>true</code>.
 *
 * @param {boolean} [opt_hide] Set to <code>true</code> to also remove the markers
 *  from the map.
 */
MarkerClusterer.prototype.resetViewport_ = function (opt_hide) {
  var i, marker;
  // Remove all the clusters
  for (i = 0; i < this.clusters_.length; i++) {
    this.clusters_[i].remove();
  }
  this.clusters_ = [];

  // Reset the markers to not be added and to be removed from the map.
  for (i = 0; i < this.markers_.length; i++) {
    marker = this.markers_[i];
    marker.isAdded = false;
    if (opt_hide) {
      marker.setMap(null);
	  if(!!marker.label){ 
			marker.label.setMap(null);
	  } 
    }
  }
};


/**
 * Calculates the distance between two latlng locations in km.
 *
 * @param {google.maps.LatLng} p1 The first lat lng point.
 * @param {google.maps.LatLng} p2 The second lat lng point.
 * @return {number} The distance between the two points in km.
 * @see http://www.movable-type.co.uk/scripts/latlong.html
*/
MarkerClusterer.prototype.distanceBetweenPoints_ = function (p1, p2) {
  var R = 6371; // Radius of the Earth in km
  var dLat = (p2.lat() - p1.lat()) * Math.PI / 180;
  var dLon = (p2.lng() - p1.lng()) * Math.PI / 180;
  var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(p1.lat() * Math.PI / 180) * Math.cos(p2.lat() * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  var d = R * c;
  return d;
};


/**
 * Determines if a marker is contained in a bounds.
 *
 * @param {google.maps.Marker} marker The marker to check.
 * @param {google.maps.LatLngBounds} bounds The bounds to check against.
 * @return {boolean} True if the marker is in the bounds.
 */
MarkerClusterer.prototype.isMarkerInBounds_ = function (marker, bounds) {
  return bounds.contains(marker.getPosition());
};


/**
 * Adds a marker to a cluster, or creates a new cluster.
 *
 * @param {google.maps.Marker} marker The marker to add.
 */
MarkerClusterer.prototype.addToClosestCluster_ = function (marker) {
  var i, d, cluster, center;
  var distance = 40000; // Some large number
  var clusterToAddTo = null;
  for (i = 0; i < this.clusters_.length; i++) {
    cluster = this.clusters_[i];
    center = cluster.getCenter();
    if (center) {
      d = this.distanceBetweenPoints_(center, marker.getPosition());
      if (d < distance) {
        distance = d;
        clusterToAddTo = cluster;
      }
    }
  }

  if (clusterToAddTo && clusterToAddTo.isMarkerInClusterBounds(marker)) {
    clusterToAddTo.addMarker(marker);
  } else {
    cluster = new Cluster(this);
    cluster.addMarker(marker);
    this.clusters_.push(cluster);
  }
};


/**
 * Creates the clusters. This is done in batches to avoid timeout errors
 *  in some browsers when there is a huge number of markers.
 *
 * @param {number} iFirst The index of the first marker in the batch of
 *  markers to be added to clusters.
 */
MarkerClusterer.prototype.createClusters_ = function (iFirst) {
  var i, marker;
  var mapBounds;
  var cMarkerClusterer = this;
  if (!this.ready_) {
    return;
  }

  // Cancel previous batch processing if we're working on the first batch:
  if (iFirst === 0) {
    /**
     * This event is fired when the <code>MarkerClusterer</code> begins
     *  clustering markers.
     * @name MarkerClusterer#clusteringbegin
     * @param {MarkerClusterer} mc The MarkerClusterer whose markers are being clustered.
     * @event
     */
    google.maps.event.trigger(this, "clusteringbegin", this);

    if (typeof this.timerRefStatic !== "undefined") {
      clearTimeout(this.timerRefStatic);
      delete this.timerRefStatic;
    }
  }

  // Get our current map view bounds.
  // Create a new bounds object so we don't affect the map.
  //
  // See Comments 9 & 11 on Issue 3651 relating to this workaround for a Google Maps bug:
  if (this.getMap().getZoom() > 3) {
    mapBounds = new google.maps.LatLngBounds(this.getMap().getBounds().getSouthWest(),
      this.getMap().getBounds().getNorthEast());
  } else {
    mapBounds = new google.maps.LatLngBounds(new google.maps.LatLng(85.02070771743472, -178.48388434375), new google.maps.LatLng(-85.08136444384544, 178.00048865625));
  }
  var bounds = this.getExtendedBounds(mapBounds);

  var iLast = Math.min(iFirst + this.batchSize_, this.markers_.length);

  for (i = iFirst; i < iLast; i++) {
    marker = this.markers_[i];
    if (!marker.isAdded && this.isMarkerInBounds_(marker, bounds)) {
      if (!this.ignoreHidden_ || (this.ignoreHidden_ && marker.getVisible())) {
        this.addToClosestCluster_(marker);
      }
    }
  }

  if (iLast < this.markers_.length) {
    this.timerRefStatic = setTimeout(function () {
      cMarkerClusterer.createClusters_(iLast);
    }, 0);
  } else {
    delete this.timerRefStatic;

    /**
     * This event is fired when the <code>MarkerClusterer</code> stops
     *  clustering markers.
     * @name MarkerClusterer#clusteringend
     * @param {MarkerClusterer} mc The MarkerClusterer whose markers are being clustered.
     * @event
     */
    google.maps.event.trigger(this, "clusteringend", this);
  }
};


/**
 * Extends an object's prototype by another's.
 *
 * @param {Object} obj1 The object to be extended.
 * @param {Object} obj2 The object to extend with.
 * @return {Object} The new extended object.
 * @ignore
 */
MarkerClusterer.prototype.extend = function (obj1, obj2) {
  return (function (object) {
    var property;
    for (property in object.prototype) {
      this.prototype[property] = object.prototype[property];
    }
    return this;
  }).apply(obj1, [obj2]);
};


/**
 * The default function for determining the label text and style
 * for a cluster icon.
 *
 * @param {Array.<google.maps.Marker>} markers The array of markers represented by the cluster.
 * @param {number} numStyles The number of marker styles available.
 * @return {ClusterIconInfo} The information resource for the cluster.
 * @constant
 * @ignore
 */
MarkerClusterer.CALCULATOR = function (markers, numStyles) {
  var index = 0;
  var title = "";
  var count = markers.length.toString();

  var dv = count;
  while (dv !== 0) {
    dv = parseInt(dv / 10, 10);
    index++;
  }

  index = Math.min(index, numStyles);
  return {
    text: count,
    index: index,
    title: title
  };
};


/**
 * The number of markers to process in one batch.
 *
 * @type {number}
 * @constant
 */
MarkerClusterer.BATCH_SIZE = 2000;


/**
 * The number of markers to process in one batch (IE only).
 *
 * @type {number}
 * @constant
 */
MarkerClusterer.BATCH_SIZE_IE = 500;


/**
 * The default root name for the marker cluster images.
 *
 * @type {string}
 * @constant
 */
MarkerClusterer.IMAGE_PATH = "http://www.dyasdesigns.com/tntmap/images/m";


/**
 * The default extension name for the marker cluster images.
 *
 * @type {string}
 * @constant
 */
MarkerClusterer.IMAGE_EXTENSION = "png";


/**
 * The default array of sizes for the marker cluster images.
 *
 * @type {Array.<number>}
 * @constant
 */
MarkerClusterer.IMAGE_SIZES = [53, 56, 66, 78, 90];

//MarkerClusterer.IMAGE_SIZES = [48, 54, 64, 74, 85];

