package dpf.mt.gpinf.mapas;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import dpf.sp.gpinf.indexer.search.App;

public class NavegarPaiFunction extends BrowserFunction {

	public NavegarPaiFunction(Browser browser, String name) {
		super(browser, name);
	}

	public Object function(Object[] arguments) {
		String name = ((String) arguments[0]);
		String id = name.substring(0, name.indexOf("-"));
		
        App.get().getTreeListener().navigateToParent(Integer.parseInt(id));
		
		return super.function(arguments);
	}
	
}
