package dpf.sp.gpinf.indexer.search.mapas;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.layout.GridData;

public class SaveKMLFunction extends BrowserFunction {

	Runnable save;
	
	public SaveKMLFunction(Runnable save, Browser browser, String name) {
		super(browser, name);
		this.save = save;
	}

	@Override
	public Object function(Object[] arguments) {
		(new Thread(save)).start();
		
		return super.function(arguments);
	}

}
