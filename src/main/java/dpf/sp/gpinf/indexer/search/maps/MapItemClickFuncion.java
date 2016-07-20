package dpf.sp.gpinf.indexer.search.maps;

import java.io.IOException;

import javax.swing.JTable;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;

import dpf.sp.gpinf.indexer.search.App;
import dpf.sp.gpinf.indexer.search.SearchResult;


/**
 * Implementa o evento que ocorre ao se clicar em um item redenrizado no mapa.
 *
 * @author Patrick Dalla Bernardina <patrick.dalla@gmail.com>
 */

public class MapItemClickFuncion extends BrowserFunction {
	
	public MapItemClickFuncion(Browser browser, String item) {
		super(browser, item);
	}

	@Override
	public Object function(Object[] arguments) {

		String name = ((String) arguments[0]);
		double lat = ((Double) arguments[1]).doubleValue();
        double lng = ((Double) arguments[2]).doubleValue();
        
        int pos = 0;
        SearchResult results = App.get().getResults();
        for (int i = 0; i < results.docs.length; i++) {
        	org.apache.lucene.document.Document doc = null;
        	try {
				doc = App.get().searcher.doc(results.docs[i]);
	        	if(doc.get("id").equals(name.substring(0, name.indexOf("-")))){
	        		pos = i;
	        		break;
	        	}
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
        }
        
        JTable t = App.get().getResultsTable();
        t.setRowSelectionInterval(pos, pos);

		return super.function(arguments);
	}

}
