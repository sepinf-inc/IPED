package iped.geo.openstreet;

import java.awt.event.MouseEvent;
import java.util.Date;
import java.util.StringTokenizer;

import iped.geo.MapSelectionListener;
import iped.geo.MarkerCheckBoxListener;
import iped.geo.MarkerEventListener;
import iped.geo.webkit.JSInterfaceFunctions;
import netscape.javascript.JSObject;

public class JSInterfaceFunctionsOpenStreet implements JSInterfaceFunctions {

    MapCanvasOpenStreet map;

    public MapCanvasOpenStreet getMap() {
        return map;
    }

    public JSInterfaceFunctionsOpenStreet(MapCanvasOpenStreet map) {
        this.map = map;
    }

    public void selectMarkerBF(JSObject markers) {
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
            if (modfJS.toString().contains("shift")) { //$NON-NLS-1$
                modf = modf | MouseEvent.SHIFT_DOWN_MASK;
            }
            if (modfJS.toString().contains("ctrl")) { //$NON-NLS-1$
                modf = modf | MouseEvent.CTRL_DOWN_MASK;
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

    public void checkMarkerBF(String markerId, boolean checked) {
        MarkerCheckBoxListener l = map.getMarkerCheckBoxListener();
        if (l != null) {
            l.onClicked(markerId, checked);
        }
    }

    public void exportKmlBF() {
        Runnable save = map.getSaveRunnable();
        (new Thread(save)).start();
    }

}
