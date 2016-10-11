package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.MouseEvent;
import java.io.IOException;

import javax.swing.JTable;

import dpf.mt.gpinf.mapas.MarkerEventListener;
import dpf.sp.gpinf.indexer.search.LuceneSearchResult;

public class AppMapMarkerEventListener implements MarkerEventListener {

	@Override
	public void onClicked(int mid, MouseEvent e) {
        int pos = 0;
        
        //procura pela posição correspondente na tabela do item clicado no mapa
        LuceneSearchResult results = App.get().getResults();
        for (int i = 0; i < results.getLength(); i++) {
        	org.apache.lucene.document.Document doc = null;
        	try {
				doc = App.get().appCase.getSearcher().doc(results.getLuceneIds()[i]);
	        	if(doc.get("id").equals(Integer.toString(mid))){
	        		pos = i;
	        		break;
	        	}
			} catch (IOException ex) {
				ex.printStackTrace();
				break;
			}
        }
        
        JTable t = App.get().getResultsTable();
        pos = t.convertRowIndexToView(pos);
        if(e.isShiftDown()){
        	if(t.isRowSelected(pos)){
                t.removeRowSelectionInterval(pos, pos);
        	}else{
                t.addRowSelectionInterval(pos, pos);
        	}
        }else{
            t.setRowSelectionInterval(pos, pos);
        }
	}

	@Override
	public void onMouseEntered(int mid, MouseEvent e) {
	}

	@Override
	public void onMouseExited(int mid, MouseEvent e) {
	}

	@Override
	public void onMousePressed(int mid, MouseEvent e) {
	}

	@Override
	public void onMouseReleased(int mid, MouseEvent e) {
	}

}
