package dpf.sp.gpinf.indexer.search.mapas;

import java.awt.event.MouseEvent;

public interface MarkerEventListener {
	
	public void onClicked(int mid, MouseEvent e);
	public void onMouseEntered(int mid, MouseEvent e);
	public void onMouseExited(int mid, MouseEvent e);
	public void onMousePressed(int mid, MouseEvent e);
	public void onMouseReleased(int mid, MouseEvent e);

}