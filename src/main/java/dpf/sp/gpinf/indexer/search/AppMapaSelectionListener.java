package dpf.sp.gpinf.indexer.search;

import java.io.IOException;
import java.util.HashSet;
import javax.swing.JTable;

import dpf.mt.gpinf.mapas.MapSelectionListener;

public class AppMapaSelectionListener implements MapSelectionListener {

	@Override
	public void OnSelect(String[] mids) {
		int pos=0;
        JTable t = App.get().getResultsTable();
        org.apache.lucene.document.Document doc = null;
        SearchResult results = App.get().getResults();
        
        HashSet<String> columns = new HashSet<String>();
        columns.add("id");

        t.getSelectionModel().setValueIsAdjusting(true);
        for (int i = 0; i < results.docs.length; i++) {
        	try {
        		pos = -1;
    			doc = App.get().appCase.searcher.doc(results.docs[i], columns);
    			for (int j = 0; j < mids.length; j++) {
    	        	if(mids[j].equals(doc.get("id"))){
    	        		pos = i;
    	        		break;
    	        	}
    			}
    			if(pos>=0){
        	        pos = t.convertRowIndexToView(pos);
        	        t.addRowSelectionInterval(pos, pos);
        	        //t.changeSelection(pos, 1, false, false);
    			}
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}
        }
        
        t.getSelectionModel().setValueIsAdjusting(false);
	}

}
