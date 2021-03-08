package dpf.mt.gpinf.mapas.webkit;

import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Date;
import java.util.StringTokenizer;

import dpf.mt.gpinf.mapas.MapSelectionListener;
import dpf.mt.gpinf.mapas.MarkerCheckBoxListener;
import dpf.mt.gpinf.mapas.MarkerEventListener;
import netscape.javascript.JSObject;

public class JSInterfaceFunctions {

    MapaCanvasWebkit map;

    public JSInterfaceFunctions(MapaCanvasWebkit map) {
        this.map = map;
    }

    public void selecionaMarcadorBF(JSObject markers) {
        String arguments = markers.toString();
        StringTokenizer st = new StringTokenizer(arguments, ","); //$NON-NLS-1$
        String o[] = new String[st.countTokens()];

        int i = 0;
        while (st.hasMoreTokens()) {
            o[i] = st.nextToken();
            i++;
        }

        MapSelectionListener l = map.getMapSelectionListener();
        if (l != null) {
            l.OnSelect(o);
        }
    }

    public void alert(String alert) {
        System.out.println("App.Alert:" + alert); //$NON-NLS-1$
    }

    public void markerMouseClickedBF(String markerId, int button, String modfJS) {
        MarkerEventListener l = map.getMarkerEventListener();
        if (l != null) {
            int modf = 0;
            if (modfJS.toString().equals("shift")) { //$NON-NLS-1$
                modf = MouseEvent.SHIFT_DOWN_MASK;
            }

            try {
                MouseEvent e = new MouseEvent(map.getContainer(), 1, (new Date()).getTime(), modf, // modifiers
                        0, // x
                        0, // y
                        1, // clickcount
                        false, // popupTrigger
                        button // button
                );

                l.onClicked(markerId, e);
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }

    }

    public void markerMouseDblClickedBF(String markerId, int button) {
        markerMouseClickedBF(markerId, button, ""); //$NON-NLS-1$
    }

    public void markerMousePressedBF(String markerId) {
        MarkerEventListener l = map.getMarkerEventListener();
        if (l != null) {
            MouseEvent e = new MouseEvent(map.getContainer(), 1, (new Date()).getTime(), 0, 0, // x
                    0, // y
                    0, // clickcount
                    false, // popupTrigger
                    0 // button
            );
            l.onMousePressed(markerId, e);
        }
    }

    public void markerMouseReleasedBF(String markerId) {
        MarkerEventListener l = map.getMarkerEventListener();
        if (l != null) {
            MouseEvent e = new MouseEvent(map.getContainer(), 1, (new Date()).getTime(), 0, 0, // x
                    0, // y
                    0, // clickcount
                    false, // popupTrigger
                    0 // button
            );
            l.onMouseReleased(markerId, e);
        }
    }

    public void markerMouseEnteredBF(String markerId) {
        MarkerEventListener l = map.getMarkerEventListener();
        if (l != null) {
            MouseEvent e = new MouseEvent(map.getContainer(), 1, (new Date()).getTime(), 0, 0, // x
                    0, // y
                    0, // clickcount
                    false, // popupTrigger
                    0 // button
            );
            l.onMouseEntered(markerId, e);
        }
    }

    public void markerMouseExitedBF(String markerId) {
        MarkerEventListener l = map.getMarkerEventListener();
        if (l != null) {
            MouseEvent e = new MouseEvent(map.getContainer(), 1, (new Date()).getTime(), 0, 0, // x
                    0, // y
                    0, // clickcount
                    false, // popupTrigger
                    0 // button
            );
            l.onMouseExited(markerId, e);
        }
    }

    public void marcaMarcadorBF(String markerId, boolean checked) {
        MarkerCheckBoxListener l = map.getMarkerCheckBoxListener();
        if (l != null) {
            int id = 0;

            l.onClicked(markerId, checked);
        }
    }

    public void exportarKmlBF() {
        Runnable save = map.getSaveRunnable();
        (new Thread(save)).start();
    }

}
