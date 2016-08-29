package dpf.mt.gpinf.mapas.webkit;

import java.awt.Component;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;

import dpf.mt.gpinf.mapas.AbstractMapaCanvas;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;


public class MapaCanvasWebkit extends AbstractMapaCanvas {

	WebView browser;
	WebEngine webEngine = null;
    final JFXPanel jfxPanel;

	@SuppressWarnings("restriction")
	public MapaCanvasWebkit(){
	    this.jfxPanel = new JFXPanel();

		Platform.runLater(new Runnable() {
			public void run() {
				browser = new WebView();
				jfxPanel.setScene(new Scene(browser));
				webEngine = browser.getEngine();
			}
	    });
	}

	@Override
	public void connect() {
		if(webEngine == null){
			browser = new WebView();
			jfxPanel.setScene(new Scene(browser));
			webEngine = browser.getEngine();
		}
		
	}

	@Override
	public void disconnect() {
		jfxPanel.setScene(null);
		webEngine = null;
		browser = null;
	}

	@Override
	public void setText(final String html) {
		Platform.runLater(new Runnable() {
			public void run() {
				webEngine.loadContent(html);
				jfxPanel.invalidate();
			}
		});
	}

	@Override
	public void setKML(String kml) {
		  try {
			  String html = IOUtils.toString(getClass().getResourceAsStream("../main.html"), "UTF-8");
			  String js = IOUtils.toString(getClass().getResourceAsStream("../geoxmlfull_v3.js"), "UTF-8");
			  String js2 = IOUtils.toString(getClass().getResourceAsStream("../keydragzoom.js"), "UTF-8");
			  String js3 = IOUtils.toString(getClass().getResourceAsStream("../extensions.js"), "UTF-8");
			  String js4 = IOUtils.toString(getClass().getResourceAsStream("../ext_geoxml3.js"), "UTF-8");

			  String b64_selecionado = "data:image/png;base64," + new String(Base64.getEncoder().encode(IOUtils.toByteArray(getClass().getResourceAsStream("../marcador_selecionado.png"))));
			  String b64_selecionado_m = "data:image/png;base64," + new String(Base64.getEncoder().encode(IOUtils.toByteArray(getClass().getResourceAsStream("../marcador_selecionado_m.png"))));
			  String b64_normal = "data:image/png;base64," + new String(Base64.getEncoder().encode(IOUtils.toByteArray(getClass().getResourceAsStream("../marcador_normal.png"))));
			  String b64_marcado = "data:image/png;base64," + new String(Base64.getEncoder().encode(IOUtils.toByteArray(getClass().getResourceAsStream("../marcador_marcado.png"))));
			  
			  html = html.replace("{{load_geoxml3}}", js);
			  html = html.replace("{{load_keydragzoom}}", js2);
			  html = html.replace("{{load_extensions}}", js3);
			  html = html.replace("{{load_geoxml3_ext}}", js4);
			  html = html.replace("{{icone_selecionado_base64}}", b64_selecionado);
			  html = html.replace("{{icone_base64}}", b64_normal);
			  html = html.replace("{{icone_selecionado_m_base64}}", b64_selecionado_m);
			  html = html.replace("{{icone_m_base64}}", b64_marcado);		  
			  html = html.replace("{{kml}}", kml.replace("\n", "").replace("\r", ""));
			  
			  setText(html);		  
		  } catch (IOException e) {
			  e.printStackTrace();
		  }
	}

	@Override
	public void redesenha() {
		// TODO Auto-generated method stub

	}

	@Override
	public void addSaveKmlFunction(Runnable save) {
		// TODO Auto-generated method stub

	}

	@Override
	public void enviaSelecoes(HashMap<String, Boolean> selecoes) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isConnected() {
		return (webEngine!=null);
	}

	@Override
	public Component getContainer() {
		return jfxPanel;
	}

}
