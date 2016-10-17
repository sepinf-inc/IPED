package dpf.sp.gpinf.indexer.desktop;

import java.awt.event.MouseEvent;

import javax.swing.JTable;

import dpf.mt.gpinf.mapas.MarkerEventListener;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;

public class AppMapMarkerEventListener implements MarkerEventListener {

	@Override
	public void onClicked(String mid, MouseEvent e) {
        int pos = 0;
        
        //procura pela posição correspondente na tabela do item clicado no mapa
        MultiSearchResult results = App.get().getResults();
        for (int i = 0; i < results.getLength(); i++) {
        	ItemId item = App.get().getResults().getItem(i);
			String gid = item.getSourceId() + "-" + item.getId();
        	if(gid.equals(mid)){
        		pos = i;
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
	public void onMouseEntered(String mid, MouseEvent e) {
	}

	@Override
	public void onMouseExited(String mid, MouseEvent e) {
	}

	@Override
	public void onMousePressed(String mid, MouseEvent e) {
	}

	@Override
	public void onMouseReleased(String mid, MouseEvent e) {
	}

}
