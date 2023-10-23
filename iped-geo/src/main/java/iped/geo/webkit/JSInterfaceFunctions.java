package iped.geo.webkit;

import netscape.javascript.JSObject;

public interface JSInterfaceFunctions {

    void selectMarkerBF(JSObject markers);

    void alert(String alert);

    void markerMouseClickedBF(String markerId, int button, String modfJS);

    void markerMouseDblClickedBF(String markerId, int button);

    void markerMousePressedBF(String markerId);

    void markerMouseReleasedBF(String markerId);

    void markerMouseEnteredBF(String markerId);

    void markerMouseExitedBF(String markerId);

    void checkMarkerBF(String markerId, boolean checked);

    void exportKmlBF();

}