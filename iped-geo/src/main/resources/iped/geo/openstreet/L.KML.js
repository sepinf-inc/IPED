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
    resolveFullyLoaded: null,
    fullyLoaded: null,
    afterFullyLoadedEvent: null,
    onFullyLoaded: null,
    nextlinestyle: {color: 'blue', weight: 3},
    trackLineStyle: {color: 'green', weight:3, smoothFactor:4},
    previouslinestyle: {color: 'red', weight: 3},
    geoJSONLayers: [],
    initialize: function (kml, kmlOptions) {
        L.MarkerClusterGroup.prototype.initialize.call(this,kmlOptions);
        this._kml = kml;
        this._layers = {};
        this._kmlOptions = kmlOptions;
        this.fullyLoaded = new Promise((resolve, reject) => {this.resolveFullyLoaded = resolve});
        this.afterFullyLoadedEvent = this.fullyLoaded.then((track)=>{
            if(track.onFullyLoaded){
                track.onFullyLoaded();
            }
        });
        if (kml) {
            this.addKML(kml, kmlOptions);
        }
    },
    drawFeature(mid, json){
        if(this.markers[mid].selected == 'true'){
            let l = L.geoJSON(json);
            this.geoJSONLayers.push(l);
            this.addLayer(l);

            this._map.fitBounds(l.getBounds());
        }        
    },
    hideLastFeature(json){
        while(this.geoJSONLayers.length>0){
            if(this!=this.geoJSONLayers[0]){
        this.removeLayer(this.geoJSONLayers[0]);
            }
            this.geoJSONLayers.shift();//remove first item from array
        }
    },
    markersCount: function(){
        try{
            return this.visibleMarkerCoords.length;
        }catch(e){
            alert(e);
        }
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
        window.app.selectMarkerBF(mids);
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
        window.app.selectMarkerBF(mids);
    },
    selecionaMarcador: function (id, b){
        selecionaMarcador(id, b, true);
    },
    selecionaMarcador: function (id, b, notify){
        for(i=0;i<id.length;i++){
            mark=this.markers[id[i]];
            if(b=='true'){
                mark.selected='true';
                //mark.showDirectionLines(); 
                this.selectedPlacemarks.push(mark);
            }else{
                mark.selected='false';
                mark.hideDirectionLines();
                let i = this.selectedPlacemarks.indexOf(mark);
                if(i>-1){
                    this.selectedPlacemarks.splice(i,1);
                }
            }
            mark.atualizaIcone();
            if(notify){
                mark.onClick();
            }
        }
    },
    checkMarcador: function (id, b, notify){
            for(i=0;i<id.length;i++){
                mark=this.markers[id[i]];
                if(b=='true'){
                    mark.checked='true';
                }else{
                    mark.checked='false';
                }
                mark.atualizaIcone();
            }
    },
    marca: function (id, b){
        try{
            let marker_checkbox = document.getElementById('marker_checkbox_'+this.id)
            if(b=='true'){
                this.markers[id].checked='true';
                if(marker_checkbox){
                    marker_checkbox.checked=true;
                }
            }else{
                this.markers[id].checked='false';
                if(marker_checkbox){
                    marker_checkbox.checked=false;
                }
            }
            this.markers[id].atualizaIcone();
        }catch(e){
            alert(e);
        }
    },
    minlat:0, 
    minlongit:0, 
    maxlat:0, 
    maxlingit:0,
    orderPromise:null,
    
    viewAll: function(){
        var corner1 = L.latLng(this.minlat, this.minlongit);
        var corner2 = L.latLng(this.maxlat, this.maxlongit);
        var bounds = L.latLngBounds(corner1, corner2);
        var target = this._map._getBoundsCenterZoom(bounds, map.options);
        var zoom = this._map._zoom;
        zoom = target.zoom;
        this._map.setView(target.center, zoom, map.option);
    },

    setAllRange: function(minlongit, minlat, maxlongit, maxlat){
        this.minlongit = minlongit;
        this.minlat = minlat;
        this.maxlongit = maxlongit;
        this.maxlat=maxlat;
        this.viewAll();        
    },
    centralizaMarcador: function(m){
        this._map.setView(m.getLatLng());
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
            if(ms.length>1){
                map.fitBounds(fg.getBounds());
            }else{
                this.centralizaMarcador(ms[0]);
            }
        }
    },
    centralizaSelecao: function(){
        this.centralizaMarcadores(this.selectedPlacemarks);
    },

    styles:[],
    markers:[],
    markerCoords:[],
    visibleMarkerCoords:[],
    layers:[],
    pathsVisible:false,
    lastVisibleTrack:null,

    createPaths: function () {
        if(this.paths){
            return new Promise((resolve, reject)=>{resolve();});
        }else{
            return this.fullyLoaded.then((track)=>{
                try{
                    this.paths = new L.Polyline(this.visibleMarkerCoords, {
                        color: 'red',
                        weight: 3,
                        opacity: 1,
                        smoothFactor: 1
                    });
                    this.paths.arrowheads({
                          size: '18px',
                          fill: true,
                          yawn: 25,
                          frequency: 'allvertices'
                    });
                }catch(e){
                    alert(e);                
                }
           });
        }
    },
    
    tooglePaths: function (){
        if(this.pathsVisible){
            this.removeLayer(this.paths);
            this.pathsVisible=false;
        }else{
            this.createPaths().then(()=>{
                this.addLayer(this.paths);
                this.pathsVisible=true;
            });
        }
    },
    
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

    parseStylesFromXmlString: function (xmlString, kmlOptions) {
        let parser = new DOMParser();
        let xmldoc = parser.parseFromString(xmlString, 'text/xml');
        this.styles = this.parseStyles(xmldoc, kmlOptions);
    },
    
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
        if(el){
            for (var j = 0; j < el.length; j++) {
                if (!this._check_folder(el[j])) { continue; }
                l = this.parsePlacemark(el[j], xml, style);
                if (l) { layers.push(l); }
            }
        }
        el = xml.getElementsByTagName('gx:Tour');
        if(el.length>0){
            this.tourOrder=el[0].getElementsByTagName('name')[0].childNodes[0].nodeValue;
        }

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
                anchorType: {x: ioptions.xunits, y: ioptions.yunits}
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
    
    msAddPlacemark:[],
    selectedPlacemarks:[],
    lastAddedPlacemark: null,
    addpromises:[],
    visibleLayer:null,
    orderedVisiblePlacemarks:[],
    placemarks:[],
    placemarkIndexes:[],

    deselectAll:function (){
        while(this.selectedPlacemarks.length>0){
            if(this!=this.selectedPlacemarks[0]){
                this.selectedPlacemarks[0].selected=false;
                this.selectedPlacemarks[0].hideDirectionLines();
                this.selectedPlacemarks[0].hideTrack();
                this.selectedPlacemarks[0].atualizaIcone();
            }
            this.selectedPlacemarks.shift();//remove first item from array
        }
    },
    updateLeadMarker(marker){
        if(this.curMark){
            this.curMark.hideDirectionLines();
        }
        if(this.curMark!=this.markers[marker]){
            //this.markers[marker].showDirectionLines();
        }
        this.curMark = this.markers[marker];
    },
    
    highlight: function(mark){
        mark.selected='true';
        this.selectedPlacemarks.push(mark);        
    },
    
    unhighlight: function(mark){
        mark.selected='false';
        mark.hideDirectionLines();
        let i = this.selectedPlacemarks.indexOf(mark);
        if(i>-1){
            this.selectedPlacemarks.splice(i,1);
        }
    },
    
    addPlacemark: function (id, name, descr, lat, long, checked, selected, options) {
        var m = new L.KMLMarker(new L.LatLng(lat, long), options);
        m.id=id;
        m.styles = this.styles;
        m.checked=checked;
        m.selected=selected;
        m.name = name;
        m.descr = descr;
        m.bindPopup('<input type="checkbox" id="marker_checkbox_'+id+'" value=""  onclick="L.checkMarker(window.clickedMark.id)"/><h2>' + m.name + '</h2>' + m.descr, { className: 'kml-popup'});
        this.popupOpened=false;
        m.styleUrl='#item';
        m.parent=this;
        m.atualizaIcone();

        this.markers[id]=m;
        this.markerCoords.push(m.getLatLng());
        this.msAddPlacemark.push(m);
        if(this.msAddPlacemark.length>=10000000){
            var placemarks=this.msAddPlacemark;
            new Promise((resolve)=>{
                this.flushAddPlacemarkArray(placemarks);
                resolve();
            });
            this.msAddPlacemark=[];
        }
        return m;
    },
    
    flushAddPlacemarkArray: function (placemarks){
        if(placemarks.length>0){
            this.visibleLayer = new L.FeatureGroup(placemarks);
            this.fire('addlayer', {layer: this.visibleLayer});
            this.addLayer(this.visibleLayer);
        }
    },
    curMark:null,
    getNextMarker(a){
        if(this.curMark.next){
            this.curMark=this.curMark.next;
            return this.curMark;
        }else{
            return null;
        }
    },
    getPreviousMarker(a){
        if(this.curMark.previous){
            this.curMark=this.curMark.previous;
            return this.curMark;
        }else{
            return null;
        }
    },
    getLastMarker(a){
        this.curMark = this.orderedVisiblePlacemarks[this.orderedVisiblePlacemarks.length-1];
        return this.curMark;
    },
    getFirstMarker(a){
        this.curMark = this.placemarks[this.placemarkIndexes[0]];
        return this.curMark;
    },
    clearVisibleMarkers(){
        this.hideLastFeature();
        this.deselectAll();
        if(this.curMark){
            let mark=this.curMark;
            mark.hideDirectionLines();
            let i = this.selectedPlacemarks.indexOf(mark);
            if(i>-1){
                this.selectedPlacemarks.splice(i,1);
            }
            if(mark.track){
                if(this.hasLayer(mark.track)){
                    this.removeLayer(mark.track);
                }
            }
        }
        if(this.lastVisibleTrack){
            if(this.hasLayer(this.lastVisibleTrack)){
                this.removeLayer(this.lastVisibleTrack);
            }
        }
        this.orderedVisiblePlacemarks=[];
        this.visibleMarkerCoords=[];
        if(this.pathsVisible){
            this.removeLayer(this.paths);
        }
        this.pathsVisible=false;
        
        this.paths=null;
        
        this.fullyLoaded = new Promise((resolve, reject) => {this.resolveFullyLoaded = resolve});
        this.afterFullyLoadedEvent = this.fullyLoaded.then((track)=>{
            if(track.onFullyLoaded){
                track.onFullyLoaded();
            }
        });
        

        this.placemarks=[];
        this.placemarkIndexes=[];
        if(this.visibleLayer) this.removeLayer(this.visibleLayer);
        this.visibleLayer=null;
    },
    orderVisibleMarkers(){
            if(this.placemarks.length>0){
                if(this.visibleLayer) this.removeLayer(this.visibleLayer);
                this.visibleLayer = new L.FeatureGroup(this.placemarks);
                this.fire('addlayer', {layer: this.visibleLayer});
                this.addLayer(this.visibleLayer);
            }
            var that = this;
            if(that.placemarks.length>0){
                this.orderPromise = new Promise((resolve, reject) => {
                    try{
                        for(let i=0; i<this.placemarkIndexes.length; i++){
                            let j = that.placemarkIndexes[i];
                            that.orderedVisiblePlacemarks[j]=that.placemarks[i];
                            that.visibleMarkerCoords[j]=that.placemarks[i].getLatLng();
                        }
                        let m=null;
                        let lastPlacemark=null;
                        for(let i=0; i<that.orderedVisiblePlacemarks.length; i++){
                            m=that.orderedVisiblePlacemarks[i];
                            if(m){
                                if(m.nextLine && that.hasLayer(m.nextLine)){
                                    that.removeLayer(m.nextLine);
                                }
                                m.nextLine=null;                                
                                if(m.previousLine && that.hasLayer(m.previousLine)){
                                    that.removeLayer(m.previousLine);
                                }
                                m.previousLine=null;
                                if(lastPlacemark){
                                    lastPlacemark.next = m;
                                    m.previous = lastPlacemark;
                                }else{
                                    m.previous = null;
                                }
                                lastPlacemark=m;
                            }
                        }
                        if(m){
                            m.next=null;
                        }
                        resolve();
                    }catch(e){
                        alert(e);
                        reject();                    
                    }
                });
                let self = this;
                this.orderPromise.then(()=>{
                    if(self.resolveFullyLoaded){
                        self.resolveFullyLoaded(self);
                    }
                });
            }

        /*
        Promise.all(this.addpromises).then(()=>{
        });*/        
    },
    drawPolyline(a){
        try{
            if(a){
                let m=this.markers[a[0]];
                
                if(m.track){
                    poly=m.track;
                }else{
                    let latLngs = [];
                    for(let i=0; i<a.length; i++){
                        m=this.markers[a[i]];
                        latLngs.push(m.getLatLng());
                    }
                    poly=L.polyline(latLngs, this.trackLineStyle);
                    for(let i=0; i<a.length; i++){
                        m=this.markers[a[i]];
                        m.track=poly;
                    }
                }
                poly=poly.arrowheads({
                      size: '18px',
                      fill: true,
                      yawn: 25,
                      frequency: 'allvertices'
                });
                this.lastVisibleTrack=poly;
                this.addLayer(poly);
            }
        }catch(e){
            alert(e);
        }
    },
    showMarkers(a){
        try{
            if(a){
                for(let i=0; i<a.length; i++){
                    let m=this.markers[a[i][0]];
                    if(m){
                        this.placemarks.push(m);
                        this.placemarkIndexes.push(a[i][1]);
                        if(a[i][2]){
                            if(m.checked != 'true'){
                                m.checked = 'true';
                                m.atualizaIcone();
                            }
                        }else{
                            if(m.checked != 'false'){
                                m.checked = 'false';
                                m.atualizaIcone();
                            }
                        }
                        this.orderedVisiblePlacemarks.push(null);
                        this.visibleMarkerCoords.push(null);
                    }
                }
            }
        }catch(e){
            alert(e);
        }
    },
    itemMarkers:{},
    createMarkers(a){
        try{
            if(a){
                    for(let i=0; i<a.length; i++){
                        m = this.addPlacemark(a[i][0], a[i][2], a[i][3], a[i][4], a[i][5], a[i][6], a[i][7], {});
                        this.markers.push(m);
                        this.placemarks.push(m);
                        this.placemarkIndexes.push(a[i][1]);
                        this.orderedVisiblePlacemarks.push(null);
                        this.visibleMarkerCoords.push(null);
                        if(a[i][8]){
                            m.bgid = a[i][8]; 
                            if(this.itemMarkers[a[i][8]]){
                                this.itemMarkers[a[i][8]].push(m);
                            }else{
                                this.itemMarkers[a[i][8]]=[m];
                            }
                            m.itemMarkers=this.itemMarkers;
                        }
                    }
                    /*
                this.addpromises.push(new Promise((resolve)=>{
                    resolve();
                }));*/
            }
        }catch(e){
            alert(e);
        }
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
          layer.name=name;
        }
        el = place.getElementsByTagName('description');
        for (i = 0; i < el.length; i++) {
          for (j = 0; j < el[i].childNodes.length; j++) {
            descr = descr + el[i].childNodes[j].nodeValue;
          }
        }
        layer.descr=descr;
    
        if (name) {
          layer.bindPopup('<input type="checkbox" id="marker_checkbox" value=""/><h2>' + name + '</h2>' + descr, { className: 'kml-popup'});
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
/*      m.bindPopup("<b>"
                +line.parentElement.getElementsByTagName('name')[0].nodeValue
                +"</b>"
                +line.parentElement.getElementsByTagName('id')[0].nodeValue
                );
*/      return m;
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


L.checkMarker = function(id){
        var marker=null;
        for (var ind in track.markers){
            if(ind==id)
                marker=track.markers[ind];
        }

        if(marker.checked=='true'){
            marker.checked='false';
        }else{
            marker.checked='true';
        }
        marker.atualizaIcone();
        window.app.checkMarkerBF(marker.id, marker.checked=='true');
};

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
                this.setIcon(this.styles['#itemSelecionadoMarcado'].icon);
            }else{
                this.setIcon(this.styles['#itemSelecionado'].icon);
            }
        }else{
            if(this.checked=='true'){
                this.setIcon(this.styles['#itemMarcado'].icon);
            }else{
                this.setIcon(this.styles['#item'].icon);
            }
        }
    },
    next:null,
    previous:null,
    nextLine:null,
    previousLine:null,
    directionLinesVisible:false,
    createNextDirectionLine: function(){        
        if(this.nextLine){
        }else{
            if(this.next){
                let lagLng = null;
                let nextLagLng = null;
                if(this._spiderLeg){
                    latLng = this._spiderLeg.getLatLngs()[0];
                }else{
                    latLng = this.getLatLng();
                }
                if(this.next._spiderLeg){
                    nextLatLng = this.next._spiderLeg.getLatLngs()[0];                    
                }else{
                    nextLatLng = this.next.getLatLng();
                }
                this.nextLine = L.polyline([latLng, nextLatLng], this.parent.nextlinestyle);
                this.nextLine.arrowheads({
                      size: "18px",
                      fill: true,
                      yawn: 30,
                      frequency: 'endonly'
                    });
            }
        }
    },
    createDirectionLines: function(){
        this.createNextDirectionLine();
        if(this.previousLine){
        }else{
            if(this.previous){                
                this.previous.createNextDirectionLine();
                this.previousLine = this.previous.nextLine;
            }
        }
    },
    
    hideTrack(){
        if(this.track){
            this.parent.removeLayer(this.track);
        }        
    },

    showDirectionLines: function(){
        this.createDirectionLines();
        if(this.nextLine){
            this.nextLine.setStyle(this.parent.nextlinestyle);
            this.parent.addLayer(this.nextLine);            
        }
        if(this.previousLine) {
            this.previousLine.setStyle(this.parent.previouslinestyle);
            this.parent.addLayer(this.previousLine);
        }
        this.directionLinesVisible=true;
    },

    hideDirectionLines: function(){
        this.createDirectionLines();
        if(this.nextLine) this.parent.removeLayer(this.nextLine);
        if(this.previousLine) this.parent.removeLayer(this.previousLine);
        this.directionLinesVisible=false;
    },
    toogleCheckedAllItems: function(){
        checked = this.checked;
        if(checked == 'true'){
           checked='false'; 
        }else{
           checked='true'; 
        }
        if(this.bgid){
            subitems = this.itemMarkers[this.bgid];
            for(var i=0; i<subitems.length; i++){
                subitems[i].checked = checked;
                subitems[i].atualizaIcone();
            }
        }else{
            this.checked = checked;
            this.atualizaIcone();
        }
    },
    toogleHighlightedAllItems: function(){
        highlighted = this.selected;
        if(this.bgid){
            subitems = this.itemMarkers[this.bgid];
            for(var i=0; i<subitems.length; i++){
                if(highlighted == 'true'){
                    this.parent.unhighlight(subitems[i]);
                    alert('unhighlight:'+subitems[i].id);
                }else{
                    this.parent.highlight(subitems[i]);
                    alert('highlight:'+subitems[i].id);
                }
                subitems[i].atualizaIcone();
            }
        }else{
            if(highlighted == 'true'){
                this.parent.unhighlight(this);
            }else{
                this.parent.highlight(this);
        }
            this.atualizaIcone();
        }
    },
    onClick: function(e){
        try{
            window.clickedMark = this;
            var modf = '';

            //workaround to skip leaflet behaviour that invokes onClick twice, the one programatically invoked is skipped;
            if(!e.originalEvent.isTrusted) {
                return;
            }
            
            if(!e.originalEvent.ctrlKey && !e.originalEvent.shiftKey && this.selected == 'true'){
              return; //does nothing as the item is already selected
            }
            
    
            if(this.parent){
                if(!e.originalEvent.ctrlKey && !e.originalEvent.shiftKey){
                    this.parent.deselectAll();
                }
            }

            if(e.originalEvent.ctrlKey){
                modf=modf+'|ctrl';
                this.toogleCheckedAllItems();
            }else{
                this.toogleHighlightedAllItems();
            }

            if(this.checked && this.checked=='true'){                
                document.getElementById('marker_checkbox_'+this.id).checked=true;
            }else{
                document.getElementById('marker_checkbox_'+this.id).checked=false;
            }

            var button = (typeof e.originalEvent.which != "undefined") ? e.originalEvent.which : e.originalEvent.button;
            
            if(e.originalEvent.shiftKey){
                modf=modf+'|shift';

                window.app.markerMouseClickedBF(this.id, button, modf);
                if(e.originalEvent.ctrlKey){
                    window.app.checkMarkerBF(this.id, this.checked=='true');
                }
            }else{
                if(e.originalEvent.ctrlKey){
                    window.app.checkMarkerBF(this.id, this.checked=='true');
                }           
                window.app.markerMouseClickedBF(this.id, button, modf);
            }
            
            this.parent.hideLastFeature();
            this.parent.curMark = this;
            
            
            var that = this;
        }catch(e){
            alert(e);
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
