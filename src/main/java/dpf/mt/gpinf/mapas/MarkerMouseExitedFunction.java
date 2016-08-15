package dpf.mt.gpinf.mapas;

import java.awt.event.MouseEvent;
import java.util.Date;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

public class MarkerMouseExitedFunction extends BrowserFunction {
	MapaCanvas map;

	public MarkerMouseExitedFunction(MapaCanvas map, Browser browser, String name) {
		super(browser, name);
		this.map = map;
	}

	@Override
	public Object function(Object[] arguments) {
		
		MarkerEventListener l = map.getMarkerEventListener();
		if(l!=null){
			MouseEvent e = new MouseEvent(map.getParent(),
					  1,
	                  (new Date()).getTime(),
	                  0,
	                  0,//x
	                  0,//y
	                  0,//clickcount
	                  false,//popupTrigger
	                  0 //button
					);
			l.onMouseExited(Integer.parseInt((String)arguments[0]), e);
		}
		
		return super.function(arguments);
	}


}
