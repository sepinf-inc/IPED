package dpf.mt.gpinf.mapas.webkit;

import java.awt.Component;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import dpf.mt.gpinf.mapas.AbstractMapaCanvas;
import dpf.sp.gpinf.network.util.ProxySever;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import javafx.concurrent.Worker.State;

public class MapaCanvasWebkit extends AbstractMapaCanvas {

    WebView browser;
    WebEngine webEngine = null;
    final JFXPanel jfxPanel;
    JSInterfaceFunctions jsInterface = new JSInterfaceFunctions(this);

    @SuppressWarnings("restriction")
    public MapaCanvasWebkit() {
        this.jfxPanel = new JFXPanel();

        Platform.runLater(new Runnable() {
            public void run() {
                browser = new WebView();
                jfxPanel.setScene(new Scene(browser));
                webEngine = browser.getEngine();
                webEngine.setJavaScriptEnabled(true);
                webEngine.setOnAlert(new EventHandler<WebEvent<String>>() {
                    public void handle(WebEvent<String> event) {
                        System.out.println("Alert:" + event.getData()); //$NON-NLS-1$
                    }
                });

                webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {

                    @Override
                    public void changed(ObservableValue<? extends State> observable, State oldState, State newState) {
                        if (newState == State.SUCCEEDED) {
                            JSObject window = (JSObject) webEngine.executeScript("window"); //$NON-NLS-1$
                            window.setMember("app", jsInterface); //$NON-NLS-1$
                        }
                    }
                });

            }
        });
    }

    @Override
    public void connect() {
        if (webEngine == null) {
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
        final MapaCanvasWebkit mapa = this;

        Platform.runLater(new Runnable() {
            public void run() {
                ProxySever.get().disable();
                webEngine.loadContent(html);
                jfxPanel.invalidate();
            }
        });
    }

    @Override
    public void setKML(String kml) {
        try {
            String html = IOUtils.toString(getClass().getResourceAsStream("main.html"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
            String js = IOUtils.toString(getClass().getResourceAsStream("geoxmlfull_v3.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
            String js2 = IOUtils.toString(getClass().getResourceAsStream("keydragzoom.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
            String js3 = IOUtils.toString(getClass().getResourceAsStream("extensions.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
            String js4 = IOUtils.toString(getClass().getResourceAsStream("ext_geoxml3.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$

            String b64_selecionado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado.png"))); //$NON-NLS-1$
            String b64_selecionado_m = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado_m.png"))); //$NON-NLS-1$
            String b64_normal = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_normal.png"))); //$NON-NLS-1$
            String b64_marcado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_marcado.png"))); //$NON-NLS-1$

            html = html.replace("{{load_geoxml3}}", js); //$NON-NLS-1$
            html = html.replace("{{load_keydragzoom}}", js2); //$NON-NLS-1$
            html = html.replace("{{load_extensions}}", js3); //$NON-NLS-1$
            html = html.replace("{{load_geoxml3_ext}}", js4); //$NON-NLS-1$
            html = html.replace("{{icone_selecionado_base64}}", b64_selecionado); //$NON-NLS-1$
            html = html.replace("{{icone_base64}}", b64_normal); //$NON-NLS-1$
            html = html.replace("{{icone_selecionado_m_base64}}", b64_selecionado_m); //$NON-NLS-1$
            html = html.replace("{{icone_m_base64}}", b64_marcado); //$NON-NLS-1$
            html = html.replace("{{kml}}", kml.replace("\n", "").replace("\r", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

            setText(html);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addSaveKmlFunction(Runnable save) {
        this.saveRunnable = save;
    }

    @Override
    public boolean isConnected() {
        return (webEngine != null);
    }

    @Override
    public Component getContainer() {
        return jfxPanel;
    }

    @Override
    public void redesenha() {

        if (this.selecoesAfazer != null) {
            // repinta selecoes alteradas
            final String[] marks = new String[this.selecoesAfazer.keySet().size()];
            this.selecoesAfazer.keySet().toArray(marks);
            final HashMap<String, Boolean> selecoesAfazerCopy = selecoesAfazer;

            Platform.runLater(new Runnable() {
                public void run() {
                    boolean marcadorselecionado = false;
                    for (int i = 0; i < marks.length; i++) {
                        Boolean b = selecoesAfazerCopy.get(marks[i]);
                        if (b) {
                            marcadorselecionado = true;
                        }
                        try {
                            webEngine.executeScript("gxml.seleciona(\"" + marks[i] + "\",'" + b + "');"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (marcadorselecionado) {
                        webEngine.executeScript("gxml.centralizaSelecao();");
                    }
                }
            });
            this.selecoesAfazer = null;
        }
    }

    @Override
    public void selecionaMarcador(final String mid, final boolean b) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript("gxml.marca(\"" + mid + "\",'" + b + "');"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
