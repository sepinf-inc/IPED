package dpf.mt.gpinf.mapas.swt;

import java.awt.event.MouseEvent;
import java.util.Date;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import dpf.mt.gpinf.mapas.MarkerEventListener;

public class MarkerMouseClickedFunction extends BrowserFunction {
	MapaCanvas map;
	
	public MarkerMouseClickedFunction(MapaCanvas map, Browser browser, String name) {
		super(browser, name);
		this.map = map;
	}

	@Override
	public Object function(Object[] arguments) {
		
		MarkerEventListener l = map.getMarkerEventListener();
		if(l!=null){
			int clickcount = 0;
			if(getName().equals("markerMouseDblClickedBF")){
				clickcount = 2;
			}
			if(getName().equals("markerMouseClickedBF")){
				clickcount = 1;
			}
			int button = 0;
			if(arguments[1] instanceof Double){
				button = ((Double)arguments[1]).intValue();
			}else if(arguments[1] instanceof String){
				button = Integer.parseInt((String)arguments[1]);
			}
			int modf = 0;
			if(arguments.length > 2){
				if(arguments[2] instanceof String){
					if(((String)arguments[2]).equals("shift")){
						modf = MouseEvent.SHIFT_DOWN_MASK;
					}
				}
			}

			MouseEvent e = new MouseEvent(map.getParent(),
					  1,
	                  (new Date()).getTime(),
	                  modf, //modifiers
	                  0,//x
	                  0,//y
	                  clickcount,//clickcount
	                  false,//popupTrigger
	                  button //button
					);
			l.onClicked(Integer.parseInt((String)arguments[0]), e);
		}
		
		return super.function(arguments);
	}


}
