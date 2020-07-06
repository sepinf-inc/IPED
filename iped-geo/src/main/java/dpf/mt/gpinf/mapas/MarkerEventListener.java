package dpf.mt.gpinf.mapas;

import java.awt.event.MouseEvent;

public interface MarkerEventListener {

    public void onClicked(String mid, MouseEvent e);

    public void onMouseEntered(String mid, MouseEvent e);

    public void onMouseExited(String mid, MouseEvent e);

    public void onMousePressed(String mid, MouseEvent e);

    public void onMouseReleased(String mid, MouseEvent e);

}