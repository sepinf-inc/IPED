
/*
	Copyright (c) 2021, Patrick Dalla Bernardina - MIT licence
	
	This work was almost all based on code downloaded in 30/07/2021 from https://github.com/windycom/leaflet-kml/blob/master/L.KML.js 
	which has the Copyright (c) 2011-2015, Pavel Shramov, Bruno Bergot - MIT licence
	 
*/


L.TileLayer = L.TileLayer.include({
	getTileUrl: function(xy) {
		if(this._url.includes('{quad}')){
			digits='';
			i=xy.z-1;
			for (; i > 0; --i) {
				digit = '0';
				mask = 1 << (i - 1);
				if ((xy.x & mask) != 0) {
					digit++;
				}
				if ((xy.y & mask) != 0) {
					digit++;
					digit++;
				}
				digits=digits+digit;
			}
			return this._url.replace('{s}',1).replace('{quad}', digits);
		}else{
			var data = {
				r: L.Browser.retina ? '@2x' : '',
				s: this._getSubdomain(xy),
				x: xy.x,
				y: xy.y,
				z: this._getZoomForUrl()
			};
			if (this._map && !this._map.options.crs.infinite) {
				var invertedY = this._globalTileRange.max.y - xy.y;
				if (this.options.tms) {
					data['y'] = invertedY;
				}
				data['-y'] = invertedY;
			}

			result=L.Util.template(this._url, L.Util.extend(data, this.options));
			return result; 
		}
	}
});

L.Control.TilesSrcSelect = L.Control.extend({
	img:null,
	onAdd: function(map){
        return this.img;
	},
	OnRemove: function(map){
		
	}
	
});


L.control.tilesSrcSelect = function(img, opts) {
    ctrl = new L.Control.TilesSrcSelect(opts);
	ctrl.img = img;
	return ctrl;
}


L.IPEDMap = L.Map.extend({
	boxSelect: null,
	initialize:function(id, options){
		L.Map.prototype.initialize.apply(this, [id, options]);
		this.boxSelect = new BoxSelect(this);
	},
	_draggableMoved: function (obj) {
		obj = obj.dragging && obj.dragging.enabled() ? obj : this;
		return (obj.dragging && obj.dragging.moved()) || (this.boxZoom && this.boxZoom.moved()) || (this.boxSelect && this.boxSelect.moved());
	},
	activateAreaDrag:function(){
		this.boxSelect.setType('box');
		this.boxSelect.enable();
	},
	activateRadiusDrag:function(){
		this.boxSelect.setType('circle');
		this.boxSelect.enable();
	},
	panBy:function(offset, options){
		if(!this.boxSelect._enabled){
			L.Map.prototype.panBy.apply(this, [offset, options]);
		}
	},
	_onPanTransitionEnd:function(){
		L.Map.prototype._onPanTransitionEnd.apply(this);
	}
});


BoxSelect = L.Handler.extend({
	initialize: function (map) {
		this._map = map;
		this._container = map._container;
		this._pane = map._panes.overlayPane;
		this._resetStateTimeout = 0;
		this.type='box';
		map.on('unload', this._destroy, this);
	},
	setType:function (t){
		this.type=t;		
	},
	addHooks: function () {
		L.DomUtil.disableTextSelection();
		L.DomUtil.disableImageDrag();
		L.DomUtil.addClass(this._container, 'leaflet-crosshair');
		L.DomEvent.on(this._container, 'click', this._onClick, this);
	},

	removeHooks: function () {
		L.DomEvent.off(this._container, 'click', this._onClick, this);
	},

	moved: function () {
		return this._moved;
	},

	_destroy: function () {
		L.DomUtil.remove(this._pane);
		delete this._pane;
	},

	_resetState: function () {
		this._resetStateTimeout = 0;
		this._moved = false;
	},

	_clearDeferredResetState: function () {
		if (this._resetStateTimeout !== 0) {
			clearTimeout(this._resetStateTimeout);
			this._resetStateTimeout = 0;
		}
	},
	
	moving:false,

	_onClick:function(e){
		if(!this.moving){
			this.moving=true;
			e.preventDefault();
			e.stopPropagation();
			this._clearDeferredResetState();
			this._resetState();

			this._startPoint = this._map.mouseEventToContainerPoint(e);

			L.DomEvent.on(document, {
				contextmenu: L.DomEvent.stop,
				mousemove: this._onMouseMove,
				mouseup: this._onMouseUp,
				keydown: this._onKeyDown
			}, this);
		}
	},
	_onMouseMove: function (e) {
		if(this.moving){
			if (!this._moved) {
				this._moved = true;
				
				this._box = L.DomUtil.create('div', 'leaflet-zoom-box', this._container);
				if(this.type=='circle'){
					var att = document.createAttribute("style");
					att.value='border-radius: 50%;';
					this._box.setAttributeNode(att);
				}
			}

			this._point = this._map.mouseEventToContainerPoint(e);


			if(this.type=='box'){
				var bounds = new L.Bounds(this._point, this._startPoint),
			    	size = bounds.getSize();

				L.DomUtil.setPosition(this._box, bounds.min);

				this._box.style.width  = size.x + 'px';
				this._box.style.height = size.y + 'px';
			}else{
				var bounds = new L.Bounds(this._point, this._startPoint),
			    	size = bounds.getSize();

				this.distance = Math.sqrt(Math.pow(size.x,2)+Math.pow(size.y,2));
				
				bounds = new L.Bounds([this._startPoint.x-this.distance, this._startPoint.y-this.distance ], [this._startPoint.x+this.distance, this._startPoint.y+this.distance ]);
				
				L.DomUtil.setPosition(this._box, bounds.min);

				this._box.style.width  = this.distance*2 + 'px';
				this._box.style.height = this.distance*2 + 'px';
			}
		}
	},

	_finish: function () {
		if (this._moved) {
			L.DomUtil.remove(this._box);
			L.DomUtil.removeClass(this._container, 'leaflet-crosshair');
		}

		L.DomUtil.enableTextSelection();
		L.DomUtil.enableImageDrag();

		L.DomEvent.off(document, {
			contextmenu: L.DomEvent.stop,
			mousemove: this._onMouseMove,
			mouseup: this._onMouseUp,
			keydown: this._onKeyDown
		}, this);
		
		this.disable();
	},

	_onMouseUp: function (e) {
		this.moving=false;
		this._finish();

		if (!this._moved) { return; }
		// Postpone to next JS tick so internal click event handling
		// still see it as "moved".
		this._clearDeferredResetState();
		this._resetStateTimeout = setTimeout(L.Util.bind(this._resetState, this), 0);

		var bounds = new L.LatLngBounds(
		        this._map.containerPointToLatLng(this._startPoint),
		        this._map.containerPointToLatLng(this._point));

		if(this.type=='box'){
			track.selecionaMarcadorBounds(bounds);
		}else{
			track.selecionaMarcadorCircle(this._startPoint, this.distance);
		}

	},

	_onKeyDown: function (e) {
		if (e.keyCode === 27) {
			this._finish();
			this._clearDeferredResetState();
			this._resetState();
		}
	}

});


L.KML = L.MarkerClusterGroup.extend({
	tourOrder:'none',
	initialize: function (kml, kmlOptions) {
		L.MarkerClusterGroup.prototype.initialize.call(this,kmlOptions);
		this._kml = kml;
		this._layers = {};
		this._kmlOptions = kmlOptions;
		
		if (kml) {
			this.addKML(kml, kmlOptions);
		}
	},
	markersCount: function(){
		var i=0;
       	for (var ind in this.markers){
			i++;
		}
		return i;
	},
	selecionaMarcadorBounds: function (bounds){
		mids=[];
    	for (var ind in this.markers){
			var m = this.markers[ind];
			if(bounds.contains(m.getLatLng())){
   				mids.push(m.id);
				m.selected='true';
				m.atualizaIcone();
				
			}
    	}
		window.app.selecionaMarcadorBF(mids);
	},
	selecionaMarcadorCircle: function (p,distance){
		mids=[];
    	for (var ind in this.markers){
			var m = this.markers[ind];

			var bounds = new L.Bounds(p, this._map.latLngToContainerPoint(m.getLatLng())),
		    	size = bounds.getSize();

			mdistance = Math.sqrt(Math.pow(size.x,2)+Math.pow(size.y,2));

			if(mdistance < distance){
   				mids.push(m.id);
				m.selected='true';
				m.atualizaIcone();				
			}
    	}
		window.app.selecionaMarcadorBF(mids);
	},
	selecionaMarcador: function (id, b){
		for(i=0;i<id.length;i++){
			if(b=='true'){
				this.markers[id[i]].selected='true';
			}else{
				this.markers[id[i]].selected='false';
			}
			this.markers[id[i]].atualizaIcone();
		}
	},
    viewAll: function(){
    	ms=[];
    	for (var ind in this.markers){
			var m = this.markers[ind];
   			ms.push(m);
    	}
		this.centralizaMarcadores(ms);
	},
	centralizaMarcadores: function(ms){
    	if(ms.length>0){
    		var fg = new L.featureGroup(ms);
			bounds = L.latLngBounds(fg.getBounds());
			var target = this._map._getBoundsCenterZoom(bounds, map.options);
			var zoom = this._map._zoom;
			if(target.zoom < zoom){
				zoom = target.zoom; 	
			}
			this._map.setView(target.center, zoom, map.option);
	    	//map.fitBounds(fg.getBounds());
    	}
	},
    centralizaSelecao: function(){
    	ms=[];
    	for (var ind in this.markers){
			var m = this.markers[ind];
    		if(m.selected=='true'){
    			ms.push(m);
    		}
    	}
		this.centralizaMarcadores(ms);
    },
	
	styles:[],
	markers:[],
	layers:[],
	
	addKML: function (xml, kmlOptions) {
		layers = this.parseKML(xml, kmlOptions);
		if (!layers || !layers.length) return;
		for (var i = 0; i < layers.length; i++) {
			this.fire('addlayer', {
				layer: layers[i]
			});
			this.addLayer(layers[i]);
		}
		this.latLngs = this.getLatLngs(xml);
		this.fire('loaded');
	},

	latLngs: [],

	parseKML: function (xml, kmlOptions) {
		styles = this.parseStyles(xml, kmlOptions);
		style = styles;
		this.parseStyleMap(xml, style);
		var el = xml.getElementsByTagName('Folder');
		var layers = [], l;
		for (var i = 0; i < el.length; i++) {
			if (!this._check_folder(el[i])) { continue; }
			l = this.parseFolder(el[i], style);
			if (l) { 
				layers.push(l); 
			}
		}
		el = xml.getElementsByTagName('Placemark');
		for (var j = 0; j < el.length; j++) {
			if (!this._check_folder(el[j])) { continue; }
			l = this.parsePlacemark(el[j], xml, style);
			if (l) { layers.push(l); }
		}
		el = xml.getElementsByTagName('gx:Tour');
		this.tourOrder=el[0].getElementsByTagName('name')[0].childNodes[0].nodeValue;

		el = xml.getElementsByTagName('GroundOverlay');
		for (var k = 0; k < el.length; k++) {
			l = this.parseGroundOverlay(el[k]);
			if (l) { layers.push(l); }
		}
		return layers;
	},

	// Return false if e's first parent Folder is not [folder]
	// - returns true if no parent Folders
	_check_folder: function (e, folder) {
		e = e.parentNode;
		while (e && e.tagName !== 'Folder')
		{
			e = e.parentNode;
		}
		return !e || e === folder;
	},

	parseStyles: function (xml, kmlOptions) {
		var stylesTmp = {};
		var sl = xml.getElementsByTagName('Style');
		for (var i=0, len=sl.length; i<len; i++) {
			var style = this.parseStyle(sl[i], kmlOptions);
			if (style) {
				var styleName = '#' + style.id;
				stylesTmp[styleName] = style;
			}
		}
		return stylesTmp;
	},

	parseStyle: function (xml, kmlOptions) {
		var style = {}, poptions = {}, ioptions = {}, el, id;

		var attributes = {color: true, width: true, Icon: true, href: true, hotSpot: true};

		function _parse (xml) {
			var options = {};
			for (var i = 0; i < xml.childNodes.length; i++) {
				var e = xml.childNodes[i];
				var key = e.tagName;
				if (!attributes[key]) { continue; }
				if (key === 'hotSpot')
				{
					for (var j = 0; j < e.attributes.length; j++) {
						options[e.attributes[j].name] = e.attributes[j].nodeValue;
					}
				} else {
					var value = e.childNodes[0].nodeValue;
					if (key === 'color') {
						options.opacity = parseInt(value.substring(0, 2), 16) / 255.0;
						options.color = '#' + value.substring(6, 8) + value.substring(4, 6) + value.substring(2, 4);
					} else if (key === 'width') {
						options.weight = parseInt(value);
					} else if (key === 'Icon') {
						ioptions = _parse(e);
						if (ioptions.href) { options.href = ioptions.href; }
					} else if (key === 'href') {
						options.href = value;
					}
				}
			}
			return options;
		}

		el = xml.getElementsByTagName('LineStyle');
		if (el && el[0]) { style = _parse(el[0]); }
		el = xml.getElementsByTagName('PolyStyle');
		if (el && el[0]) { poptions = _parse(el[0]); }
		if (poptions.color) { style.fillColor = poptions.color; }
		if (poptions.opacity) { style.fillOpacity = poptions.opacity; }
		el = xml.getElementsByTagName('IconStyle');
		if (el && el[0]) { ioptions = _parse(el[0]); }
		if (ioptions.href) {
			var iconOptions = {
				iconUrl: ioptions.href,
				shadowUrl: null,
				anchorRef: {x: ioptions.x, y: ioptions.y},
				anchorType:	{x: ioptions.xunits, y: ioptions.yunits}
			};

			if (typeof kmlOptions === "object" && typeof kmlOptions.iconOptions === "object") {
				L.Util.extend(iconOptions, kmlOptions.iconOptions);
			}

			style.icon = new L.KMLIcon(iconOptions);
		}

		id = xml.getAttribute('id');
		if (id && style) {
			style.id = id;
		}

		return style;
	},

	parseStyleMap: function (xml, existingStyles) {
		var sl = xml.getElementsByTagName('StyleMap');

		for (var i = 0; i < sl.length; i++) {
			var e = sl[i], el;
			var smKey, smStyleUrl;

			el = e.getElementsByTagName('key');
			if (el && el[0]) { smKey = el[0].textContent; }
			el = e.getElementsByTagName('styleUrl');
			if (el && el[0]) { smStyleUrl = el[0].textContent; }

			if (smKey === 'normal')
			{
				existingStyles['#' + e.getAttribute('id')] = existingStyles[smStyleUrl];
			}
		}

		return;
	},

	parseFolder: function (xml, style) {
		var el, layers = [], l;
		el = xml.getElementsByTagName('Folder');
		for (var i = 0; i < el.length; i++) {
			if (!this._check_folder(el[i], xml)) { continue; }
			l = this.parseFolder(el[i], style);
			if (l) { layers.push(l); }
		}
		el = xml.getElementsByTagName('Placemark');
		for (var j = 0; j < el.length; j++) {
			if (!this._check_folder(el[j], xml)) { continue; }
			l = this.parsePlacemark(el[j], xml, style);
			if (l) { layers.push(l); }
		}
		el = xml.getElementsByTagName('GroundOverlay');
		for (var k = 0; k < el.length; k++) {
			if (!this._check_folder(el[k], xml)) { continue; }
			l = this.parseGroundOverlay(el[k]);
			if (l) { layers.push(l); }
		}
		if (!layers.length) { return; }
		if (layers.length === 1) {
			l = layers[0];
		} else {
			l = new L.FeatureGroup(layers);
		}
		el = xml.getElementsByTagName('name');
		if (el.length && el[0].childNodes.length) {
			l.options.name = el[0].childNodes[0].nodeValue;
		}
		return l;
	},

	parsePlacemark: function (place, xml, style, options) {
		var h, i, j, k, el, il, opts = options || {};

		el = place.getElementsByTagName('styleUrl');
		for (i = 0; i < el.length; i++) {
			var url = el[i].childNodes[0].nodeValue;
			for (var a in style[url]) {
				opts[a] = style[url][a];
			}
		}

		il = place.getElementsByTagName('Style')[0];
		if (il) {
			var inlineStyle = this.parseStyle(place);
			if (inlineStyle) {
				for (k in inlineStyle) {
					opts[k] = inlineStyle[k];
				}
			}
		}

		var multi = ['MultiGeometry', 'MultiTrack', 'gx:MultiTrack'];
		for (h in multi) {
			el = place.getElementsByTagName(multi[h]);
			for (i = 0; i < el.length; i++) {
				var layer = this.parsePlacemark(el[i], xml, style, opts);
				if (layer === undefined)
					continue;
				this.addPlacePopup(place, layer);
				return layer;
			}
		}

		var layers = [];

		var parse = ['LineString', 'Polygon', 'Point', 'Track', 'gx:Track'];
		for (j in parse) {
			var tag = parse[j];
			el = place.getElementsByTagName(tag);
			for (i = 0; i < el.length; i++) {
				var l = this['parse' + tag.replace(/gx:/, '')](el[i], xml, opts);
				if (l) { 
					el2 = place.getElementsByTagName('ExtendedData');
					if(el2){
						el2 = el2[0].getElementsByTagName('Data');
						for (j = 0; j < el2.length; j++) {
							l[el2[j].getAttribute('name')]=el2[j].childNodes[0].childNodes[0].nodeValue;
						}
						this.markers[l.id]=l;
						l.atualizaIcone();
						layers.push(l); 
					}
				}
			}
		}

		if (!layers.length) {
			return;
		}
		var layer = layers[0];
		if (layers.length > 1) {
			layer = new L.FeatureGroup(layers);
		}

		this.addPlacePopup(place, layer);
		return layer;
	},

  addPlacePopup: function(place, layer) {
    var el, i, j, name, descr = '';
    el = place.getElementsByTagName('name');
    if (el.length && el[0].childNodes.length) {
      name = el[0].childNodes[0].nodeValue;
    }
    el = place.getElementsByTagName('description');
    for (i = 0; i < el.length; i++) {
      for (j = 0; j < el[i].childNodes.length; j++) {
        descr = descr + el[i].childNodes[j].nodeValue;
      }
    }

    if (name) {
      layer.bindPopup('<h2>' + name + '</h2>' + descr, { className: 'kml-popup'});
    }
  },

	parseCoords: function (xml) {
		var el = xml.getElementsByTagName('coordinates');
		return this._read_coords(el[0]);
	},

	parseLineString: function (line, xml, options) {
		var coords = this.parseCoords(line);
		if (!coords.length) { return; }
		return new L.Polyline(coords, options);
	},

	parseTrack: function (line, xml, options) {
		var el = xml.getElementsByTagName('gx:coord');
		if (el.length === 0) { el = xml.getElementsByTagName('coord'); }
		var coords = [];
		for (var j = 0; j < el.length; j++) {
			coords = coords.concat(this._read_gxcoords(el[j]));
		}
		if (!coords.length) { return; }
		return new L.Polyline(coords, options);
	},

	parsePoint: function (line, xml, options) {
		var el = line.getElementsByTagName('coordinates');
		if (!el.length) {
			return;
		}
		var ll = el[0].childNodes[0].nodeValue.split(',');
		var m = new L.KMLMarker(new L.LatLng(ll[1], ll[0]), options);
/*		m.bindPopup("<b>"
				+line.parentElement.getElementsByTagName('name')[0].nodeValue
				+"</b>"
				+line.parentElement.getElementsByTagName('id')[0].nodeValue
				);
*/		return m;
	},

	parsePolygon: function (line, xml, options) {
		var el, polys = [], inner = [], i, coords;
		el = line.getElementsByTagName('outerBoundaryIs');
		for (i = 0; i < el.length; i++) {
			coords = this.parseCoords(el[i]);
			if (coords) {
				polys.push(coords);
			}
		}
		el = line.getElementsByTagName('innerBoundaryIs');
		for (i = 0; i < el.length; i++) {
			coords = this.parseCoords(el[i]);
			if (coords) {
				inner.push(coords);
			}
		}
		if (!polys.length) {
			return;
		}
		if (options.fillColor) {
			options.fill = true;
		}
		if (polys.length === 1) {
			return new L.Polygon(polys.concat(inner), options);
		}
		return new L.MultiPolygon(polys, options);
	},

	getLatLngs: function (xml) {
		var el = xml.getElementsByTagName('coordinates');
		var coords = [];
		for (var j = 0; j < el.length; j++) {
			// text might span many childNodes
			coords = coords.concat(this._read_coords(el[j]));
		}
		return coords;
	},

	_read_coords: function (el) {
		var text = '', coords = [], i;
		for (i = 0; i < el.childNodes.length; i++) {
			text = text + el.childNodes[i].nodeValue;
		}
		text = text.split(/[\s\n]+/);
		for (i = 0; i < text.length; i++) {
			var ll = text[i].split(',');
			if (ll.length < 2) {
				continue;
			}
			coords.push(new L.LatLng(ll[1], ll[0]));
		}
		return coords;
	},

	_read_gxcoords: function (el) {
		var text = '', coords = [];
		text = el.firstChild.nodeValue.split(' ');
		coords.push(new L.LatLng(text[1], text[0]));
		return coords;
	},

	parseGroundOverlay: function (xml) {
		var latlonbox = xml.getElementsByTagName('LatLonBox')[0];
		var bounds = new L.LatLngBounds(
			[
				latlonbox.getElementsByTagName('south')[0].childNodes[0].nodeValue,
				latlonbox.getElementsByTagName('west')[0].childNodes[0].nodeValue
			],
			[
				latlonbox.getElementsByTagName('north')[0].childNodes[0].nodeValue,
				latlonbox.getElementsByTagName('east')[0].childNodes[0].nodeValue
			]
		);
		var attributes = {Icon: true, href: true, color: true};
		function _parse (xml) {
			var options = {}, ioptions = {};
			for (var i = 0; i < xml.childNodes.length; i++) {
				var e = xml.childNodes[i];
				var key = e.tagName;
				if (!attributes[key]) { continue; }
				var value = e.childNodes[0].nodeValue;
				if (key === 'Icon') {
					ioptions = _parse(e);
					if (ioptions.href) { options.href = ioptions.href; }
				} else if (key === 'href') {
					options.href = value;
				} else if (key === 'color') {
					options.opacity = parseInt(value.substring(0, 2), 16) / 255.0;
					options.color = '#' + value.substring(6, 8) + value.substring(4, 6) + value.substring(2, 4);
				}
			}
			return options;
		}
		var options = {};
		options = _parse(xml);
		if (latlonbox.getElementsByTagName('rotation')[0] !== undefined) {
			var rotation = latlonbox.getElementsByTagName('rotation')[0].childNodes[0].nodeValue;
			options.rotation = parseFloat(rotation);
		}
		return new L.RotatedImageOverlay(options.href, bounds, {opacity: options.opacity, angle: options.rotation});
	}

});

L.KMLIcon = L.Icon.extend({
	options: {
		iconSize: [32, 32],
		iconAnchor: [16, 16],
	},
	_setIconStyles: function (img, name) {
		L.Icon.prototype._setIconStyles.apply(this, [img, name]);
	},
	_createImg: function (src, el) {
		el = el || document.createElement('img');
		el.onload = this.applyCustomStyles.bind(this,el)
		el.src = src;
		return el;
	},
	applyCustomStyles: function(img) {
		var options = this.options;
		var width = options.iconSize[0];
		var height = options.iconSize[1];

		this.options.popupAnchor = [0,(-0.83*height)];
		if (options.anchorType.x === 'fraction')
			img.style.marginLeft = (-options.anchorRef.x * width) + 'px';
		if (options.anchorType.y === 'fraction')
			img.style.marginTop  = ((-(1 - options.anchorRef.y) * height) + 1) + 'px';
		if (options.anchorType.x === 'pixels')
			img.style.marginLeft = (-options.anchorRef.x) + 'px';
		if (options.anchorType.y === 'pixels')
			img.style.marginTop  = (options.anchorRef.y - height + 1) + 'px';
	}
});


L.KMLMarker = L.Marker.extend({
	options: {
		icon: new L.KMLIcon.Default()
	},
	lastClickTimestamp : 0,
	CLICK_TOLERANCE : 200,
	onAdd: function (map) {
		L.Marker.prototype.onAdd.call(this, map);
		this.on('click', this.onClick);
	},
	onRemove: function (map) {
		this.off('click', this.onClick);
		L.Marker.prototype.onRemove.call(this, map);
	},
	atualizaIcone: function(){
		if(this.selected=='true'){
			if(this.checked=='true'){
				this.setIcon(styles['#itemSelecionadoMarcado'].icon);
			}else{
				this.setIcon(styles['#itemSelecionado'].icon);
			}
		}else{
			if(this.checked=='true'){
				this.setIcon(styles['#itemMarcado'].icon);
			}else{
				this.setIcon(styles['#item'].icon);
			}
		}
	},
	onClick: function(e){
		//workaround to skip leaflet behaviour that invokes onClick twice, the one programatically invoked is skipped;
		if(!e.originalEvent.isTrusted) {
			return;
		}
		
		if(!this.isPopupOpen()){
			this.togglePopup();
		}

		if(e.originalEvent.shiftKey){
			if(this.selected=='true'){
				this.selected='false';
			}else{
				this.selected='true';
			}
			this.atualizaIcone();
		}
		if(e.originalEvent.ctrlKey){
			if(this.checked=='true'){
				this.checked='false';
			}else{
				this.checked='true';
			}
			this.atualizaIcone();
		}
		if(e.originalEvent.shiftKey){
			var button = (typeof e.originalEvent.which != "undefined") ? e.originalEvent.which : e.originalEvent.button;
			window.app.markerMouseClickedBF(this.id, button, 'shift');
		}else{
			if(e.originalEvent.ctrlKey){
				window.app.marcaMarcadorBF(this.id, this.checked=='true');
			}else{
				var button = (typeof e.originalEvent.which != "undefined") ? e.originalEvent.which : e.originalEvent.button;
				window.app.markerMouseClickedBF(this.id, button, '');
			}			
		}
	},
	selected:false,
	checked:false
});

// Inspired by https://github.com/bbecquet/Leaflet.PolylineDecorator/tree/master/src
L.RotatedImageOverlay = L.ImageOverlay.extend({
	options: {
		angle: 0
	},
	_reset: function () {
		L.ImageOverlay.prototype._reset.call(this);
		this._rotate();
	},
	_animateZoom: function (e) {
		L.ImageOverlay.prototype._animateZoom.call(this, e);
		this._rotate();
	},
	_rotate: function () {
        if (L.DomUtil.TRANSFORM) {
            // use the CSS transform rule if available
            this._image.style[L.DomUtil.TRANSFORM] += ' rotate(' + this.options.angle + 'deg)';
        } else if (L.Browser.ie) {
            // fallback for IE6, IE7, IE8
            var rad = this.options.angle * (Math.PI / 180),
                costheta = Math.cos(rad),
                sintheta = Math.sin(rad);
            this._image.style.filter += ' progid:DXImageTransform.Microsoft.Matrix(sizingMethod=\'auto expand\', M11=' +
                costheta + ', M12=' + (-sintheta) + ', M21=' + sintheta + ', M22=' + costheta + ')';
        }
	},
	getBounds: function () {
		return this._bounds;
	}
});
