package dpf.mt.gpinf.mapas.swt;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import dpf.mt.gpinf.mapas.MarkerCheckBoxListener;
import dpf.sp.gpinf.indexer.search.App;

public class SelecionaItemFunction extends BrowserFunction {
	MapaCanvas mcanvas;

	public SelecionaItemFunction(MapaCanvas mcanvas, Browser browser, String name) {
		super(browser, name);
		this.mcanvas = mcanvas;
	}

	public Object function(Object[] arguments) {
		MarkerCheckBoxListener l = mcanvas.getMarkerCheckBoxListener();
		if(l!=null){
			int id = 0; 
			if(arguments[0] instanceof String){
				id = Integer.parseInt((String)arguments[0]);
			}else{
				if(arguments[0] instanceof Double){
					id = ((Double) arguments[0]).intValue();
				}else{
					id = ((Integer) arguments[0]).intValue();			
				}
			}

			boolean checked = false; 
			if(arguments[1] instanceof Boolean){
				checked = (Boolean) arguments[1];
			}else{
				checked = Boolean.parseBoolean((String) arguments[1]);
			}

			l.onClicked(id, checked);
		}
		
		return super.function(arguments);
	}

}
