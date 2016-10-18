package dpf.mt.gpinf.mapas.swt;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import dpf.mt.gpinf.mapas.MarkerCheckBoxListener;
import dpf.sp.gpinf.indexer.desktop.App;

public class SelecionaItemFunction extends BrowserFunction {
	MapaCanvas mcanvas;

	public SelecionaItemFunction(MapaCanvas mcanvas, Browser browser, String name) {
		super(browser, name);
		this.mcanvas = mcanvas;
	}

	public Object function(Object[] arguments) {
		MarkerCheckBoxListener l = mcanvas.getMarkerCheckBoxListener();
		if(l!=null){
			String id; 
			if(arguments[0] instanceof String){
				id = (String)arguments[0];
			}else{
				if(arguments[0] instanceof Double){
					id = String.valueOf((Double)arguments[0]);
				}else{
					id = String.valueOf((Integer)arguments[0]);			
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
