package dpf.sp.gpinf.indexer.search.mapas;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import dpf.sp.gpinf.indexer.search.App;

public class SelecionaItemFunction extends BrowserFunction {
	MapaCanvas mcanvas;

	public SelecionaItemFunction(MapaCanvas mcanvas, Browser browser, String name) {
		super(browser, name);
		this.mcanvas = mcanvas;
	}

	public Object function(Object[] arguments) {
		String name = ((String) arguments[0]);
		String id = name.substring(0, name.indexOf("-"));
		
		boolean checked = getBrowser().evaluate("return document.getElementById('marker"+id+"').checked;").equals(true);
		
		 
		App.get().getResultsTable().setValueAt(checked, App.get().getResultsTable().getSelectedRow(), 1);
		
		return super.function(arguments);
	}

}
