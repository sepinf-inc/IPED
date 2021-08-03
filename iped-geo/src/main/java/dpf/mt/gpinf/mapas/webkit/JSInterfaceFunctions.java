package dpf.mt.gpinf.mapas.webkit;

import netscape.javascript.JSObject;

public interface JSInterfaceFunctions {

	void selecionaMarcadorBF(JSObject markers);

	void alert(String alert);

	void markerMouseClickedBF(String markerId, int button, String modfJS);

	void markerMouseDblClickedBF(String markerId, int button);

	void markerMousePressedBF(String markerId);

	void markerMouseReleasedBF(String markerId);

	void markerMouseEnteredBF(String markerId);

	void markerMouseExitedBF(String markerId);

	void marcaMarcadorBF(String markerId, boolean checked);

	void exportarKmlBF();

}