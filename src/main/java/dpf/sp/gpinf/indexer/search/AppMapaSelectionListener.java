package dpf.sp.gpinf.indexer.search;

import java.io.IOException;

import javax.swing.JTable;

import dpf.sp.gpinf.indexer.search.mapas.MapSelectionListener;
import dpf.sp.gpinf.indexer.search.mapas.Marker;

public class AppMapaSelectionListener implements MapSelectionListener {

	@Override
	public void OnSelect(Marker[] marcadores) {
		int pos=0,mid;
        JTable t = App.get().getResultsTable();

		for (int j = 0; j < marcadores.length; j++) {
			mid = marcadores[j].getId();

	        SearchResult results = App.get().getResults();

	        for (int i = 0; i < results.docs.length; i++) {
	        	org.apache.lucene.document.Document doc = null;
	        	try {
					doc = App.get().searcher.doc(results.docs[i]);
					int did = Integer.parseInt(doc.get("id"));
		        	if(did == mid){
		        		pos = i;
		        		break;
		        	}
				} catch (IOException ex) {
					ex.printStackTrace();
					break;
				}
	        }

	        pos = t.convertRowIndexToView(pos);
	        t.changeSelection(pos, 1, true, false);
		}
	}

}
