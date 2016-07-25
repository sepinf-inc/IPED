
(function () { 
	
  /* desenvolvido por Patrick */ 
  DragSelect.prototype = new DragZoom();
  DragSelect.prototype.constructor=DragSelect;
  
  function DragSelect(map, opt_zoomOpts) {
      opt_zoomOpts = opt_zoomOpts || {};
      opt_zoomOpts.key = "shift,ctrl";
	  var me = this;

	  DragZoom.call(this, map, opt_zoomOpts);
	  google.maps.event.addListener(map, 'idle', function () {
          prj = ov.getProjection();
      });
	  
	  google.maps.event.addDomListener(document, "mousedown", function (e) {
		  me.defineClasseVisual(e);
	  });
	  
	  google.maps.event.addDomListener(document, "keydown", function (e) {
	        me.lastKeyDown = e;
	      });


	  google.maps.event.addDomListener(document, "mousemove", function (e) {
		  me.onMouseMoveDistancia(e);
	  });
	  
	  this.poly = new google.maps.Polyline({
		    strokeColor: '#FF0000',
		    strokeOpacity: 1.0,
		    strokeWeight: 3,
		    map: map
		  });

	  this.geodesicPoly = new google.maps.Polyline({
		    strokeColor: '#CC0099',
		    strokeOpacity: 1.0,
		    strokeWeight: 3,
		    geodesic: true,
		    map: map
		  });
  }
  
  DragSelect.prototype.onMouseMoveDistancia = function (e){
	  if (this.dragging_) {
		  var proj = this.prjov_.getProjection();
		  var p1 = proj.fromContainerPixelToLatLng(this.startPt_);
		  this.endPt_ = this.getMousePoint_(e);
		  var p2 = proj.fromContainerPixelToLatLng(this.endPt_);
		  var path = [p1, p2];
		  this.poly.setPath(path);
		  this.geodesicPoly.setPath(path);
		  this.poly.setVisible(true);
		  this.geodesicPoly.setVisible(true);
		  
		  var distance = google.maps.geometry.spherical.computeDistanceBetween(path[0], path[1]);
		  document.getElementById('distancia_calc').innerHTML = distance.toFixed(0) + " metros";
	  }
  }
  
  DragSelect.prototype.defineClasseVisual = function (e){
	  var att = document.createAttribute("class");
	  if(this.lastKeyDown.shiftKey){
		  att.value = "rect_class";
	  }else{
		  att.value = "circle_class";
	  }
	  this.boxDiv_.setAttributeNode(att);
  }
  
  
  DragSelect.prototype.getOverlay = function () { 
	  return this.prjov_;	  
  }  
  
  DragSelect.prototype.onMouseUp_ = function (e) {
	    this.mouseDown_ = false;
	    
	    this.poly.setVisible(false);
		this.geodesicPoly.setVisible(false);
		document.getElementById('distancia_calc').innerHTML = "";
	    
	    if (this.dragging_) {
	      //this.map_.fitBounds(bnds); 
	      this.dragging_ = false;
	      this.boxDiv_.style.display = 'none';


		  if(this.lastKeyDown.shiftKey){
		      var left = Math.min(this.startPt_.x, this.endPt_.x);
		      var top = Math.min(this.startPt_.y, this.endPt_.y);
		      var width = Math.abs(this.startPt_.x - this.endPt_.x);
		      var height = Math.abs(this.startPt_.y - this.endPt_.y);
		      var rect={
		        top: top,
		        left: left,
		        bottom: top + height,
		        right: left + width
		       };
		      google.maps.event.trigger(this, "dragend", rect, this.prjov_.getProjection());
			  google.maps.event.trigger(this.map_, "dragend_rect", rect, this.prjov_.getProjection());
		  }else{
			  google.maps.event.trigger(this.map_, "dragend_circle", this.startPt_, this.endPt_, this.prjov_.getProjection());
		  }

	    }	  
  }
  
  //metodo redefinido para sempre aceitar as teclas shift e ctrl.
  DragSelect.prototype.isHotKeyDown_ = function (e) {
	    var isHot;
	    e = e || window.event;
	    isHot = (e.shiftKey && this.key_.indexOf("shift")>-1) || (e.ctrlKey && this.key_.indexOf("ctrl")>-1);
	    if (!isHot) {
	      // Need to look at keyCode for Opera because it
	      // doesn't set the shiftKey, altKey, ctrlKey properties
	      // unless a non-modifier event is being reported.
	      //
	      // See http://cross-browser.com/x/examples/shift_mode.php
	      // Also see http://unixpapa.com/js/key.html
	      switch (e.keyCode) {
	      case 16:
	        if (this.key_.indexOf("shift")>-1) {
	          isHot = true;
	        }
	        break;
	      case 17:
	        if (this.key_.indexOf("ctrl")>-1) {
	          isHot = true;
	        }
	        break;
	      }
	    }
	    return isHot;
	  };

  DragSelect.prototype.onMouseMove_ = function (e) {
	  if(this.lastKeyDown.shiftKey){
		  DragZoom.prototype.onMouseMove_.call(this, e); 
	  }else{
		  this.onMouseMoveCircle_(e);
	  }
  }
	  
  DragSelect.prototype.onMouseMoveCircle_ = function (e) {
	    this.mousePosn_ = getMousePosition(e);
	    if (this.dragging_) {
	      this.endPt_ = this.getMousePoint_(e);
	      
	      var raio = Math.sqrt( Math.pow( (this.startPt_.x - this.endPt_.x),2 ) + Math.pow( (this.startPt_.y - this.endPt_.y),2 ));
	      
	      var left = this.startPt_.x - raio;
	      var top = this.startPt_.y - raio;
	      var width = raio*2;
	      var height = raio*2;
	      
	      // For benefit of MSIE 7/8 ensure following values are not negative:
	      var boxWidth =  width;//Math.max(0, width - (this.boxBorderWidths_.left + this.boxBorderWidths_.right));
	      var boxHeight = height;//Math.max(0, height - (this.boxBorderWidths_.top + this.boxBorderWidths_.bottom));
	      
	      // Left veil rectangle:
	      this.veilDiv_[0].style.top = "0px";
	      this.veilDiv_[0].style.left = "0px";
	      this.veilDiv_[0].style.width = left + "px";
	      this.veilDiv_[0].style.height = this.mapHeight_ + "px";
	      // Right veil rectangle:
	      this.veilDiv_[1].style.top = "0px";
	      this.veilDiv_[1].style.left = (left + 2*raio) + "px";
	      this.veilDiv_[1].style.width = (this.mapWidth_ - (left + 2*raio)) + "px";
	      this.veilDiv_[1].style.height = this.mapHeight_ + "px";
	      // Top veil rectangle:
	      this.veilDiv_[2].style.top = "0px";
	      this.veilDiv_[2].style.left = left + "px";
	      this.veilDiv_[2].style.width = width + "px";
	      this.veilDiv_[2].style.height = top + "px";
	      // Bottom veil rectangle:
	      this.veilDiv_[3].style.top = (top + 2*raio) + "px";
	      this.veilDiv_[3].style.left = left + "px";
	      this.veilDiv_[3].style.width = width + "px";
	      this.veilDiv_[3].style.height = (this.mapHeight_ - (top + 2*raio)) + "px";
	      // Selection rectangle:
	      this.boxDiv_.style.top = top + "px";
	      this.boxDiv_.style.left = left + "px";
	      this.boxDiv_.style.width = boxWidth + "px";
	      this.boxDiv_.style.height = boxHeight + "px";
	      this.boxDiv_.style.display = "block";
	      /**
	       * This event is fired repeatedly while the user drags a box across the area of interest.
	       * The southwest and northeast point are passed as parameters of type <code>google.maps.Point</code>
	       * (for performance reasons), relative to the map container. Also passed is the projection object
	       * so that the event listener, if necessary, can convert the pixel positions to geographic
	       * coordinates using <code>google.maps.MapCanvasProjection.fromContainerPixelToLatLng</code>.
	       * @name DragZoom#drag
	       * @param {Point} southwestPixel The southwest point of the selection area.
	       * @param {Point} northeastPixel The northeast point of the selection area.
	       * @param {MapCanvasProjection} prj The projection object.
	       * @event
	       */
	      google.maps.event.trigger(this, "drag", new google.maps.Point(left, top + height), new google.maps.Point(left + width, top), this.prjov_.getProjection());
	    } else if (!this.mouseDown_) {
	      this.mapPosn_ = getElementPosition(this.map_.getDiv());
	      this.setVeilVisibility_();
	    }
  }
  
  google.maps.Map.prototype.getDragOverlay = function () {
	  return this.dragSelect.getOverlay();
  }
  
  google.maps.Map.prototype.enableKeyDragSelect = function (opt_zoomOpts) {
	    this.dragSelect = new DragSelect(this, opt_zoomOpts);
	  };
	  
})();
