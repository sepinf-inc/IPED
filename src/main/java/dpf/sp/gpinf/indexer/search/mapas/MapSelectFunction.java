package dpf.sp.gpinf.indexer.search.mapas;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

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
			Object[] o = (Object[]) arguments[0];
			Marker[] ms = new Marker[o.length];
			
			for (int i = 0; i < o.length; i++) {
				Marker m = new Marker();
				m.setId(Integer.parseInt((String)o[i]));
				ms[i]=m;
			}
			
			l.OnSelect(ms);
		}
		
		return super.function(arguments);
	}

}
