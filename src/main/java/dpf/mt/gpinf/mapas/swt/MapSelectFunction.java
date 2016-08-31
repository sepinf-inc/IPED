package dpf.mt.gpinf.mapas.swt;

import java.util.Arrays;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import dpf.mt.gpinf.mapas.MapSelectionListener;

public class MapSelectFunction extends BrowserFunction {
	
	MapaCanvas map = null;
	
	public MapSelectFunction(MapaCanvas map, Browser browser, String name) {
		super(browser, name);
		this.map = map;
	}

	@Override
	public Object function(Object[] arguments) {
		
		MapSelectionListener l = map.getMapSelectionListener();
		if(l!=null){
			String[] o = Arrays.copyOf((Object[])arguments[0], ((Object[])arguments[0]).length, String[].class);

			l.OnSelect(o);
		}
		
		return super.function(arguments);
	}

}
