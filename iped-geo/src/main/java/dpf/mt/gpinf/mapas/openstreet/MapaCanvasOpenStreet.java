package dpf.mt.gpinf.mapas.openstreet;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;

import dpf.mt.gpinf.mapas.AbstractMapaCanvas;
import dpf.sp.gpinf.network.util.ProxySever;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.web.WebErrorEvent;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

public class MapaCanvasOpenStreet extends AbstractMapaCanvas implements MouseMotionListener {

    WebView browser;
    WebEngine webEngine = null;
    final JFXPanel jfxPanel;
    JSInterfaceFunctionsOpenStreet jsInterface = new JSInterfaceFunctionsOpenStreet(this);

    boolean dragging=false;
    double dragStartX, dragStartY;
    
    String url;

   public MapaCanvasOpenStreet() {
        this.jfxPanel = new JFXPanel();
        this.jfxPanel.addMouseMotionListener(this);

        Platform.runLater(new Runnable() {
            public void run() {
                browser = new WebView();
                jfxPanel.setScene(new Scene(browser));

                browser.setOnMouseReleased(e->{
                   	dragging=false;
                });

                browser.setOnMouseDragged(e->{
               		String shift="false";
               		if(e.isShiftDown()) {
               			shift="true";
               		}

                   	if(!dragging) {
                       	dragging=true;
                       	dragStartX=e.getX();
                       	dragStartY=e.getY();
                   	}else {
                   		final int x = (int) Math.ceil(dragStartX-e.getX());
                   		final int y = (int) Math.ceil(dragStartY-e.getY());
                    		
                   		Platform.runLater(new Runnable() {
                               public void run() {
                           		try {
                           			webEngine.executeScript("map.panBy({x:"+x+", y:"+y+"},{duration: 1,easeLinearity: 1,noMoveStart: true,animate: true})");
                           		}catch (Exception e) {                           			
									e.printStackTrace();
								}
                               }
                   		});
                    }
                });
                
                webEngine = browser.getEngine();
                webEngine.setJavaScriptEnabled(true);
                com.sun.javafx.webkit.WebConsoleListener.setDefaultListener(new com.sun.javafx.webkit.WebConsoleListener() {
                    @Override
                    public void messageAdded(WebView webView, String message, int lineNumber, String sourceId) {
                        System.out.println("From webview: " + message + " [" + sourceId + " - " + lineNumber + "]");
                    }
                });
                webEngine.setOnError(new EventHandler<WebErrorEvent>() {
                    public void handle(WebErrorEvent event) {
                        System.out.println("Error:" + event.getMessage()); //$NON-NLS-1$
                    }
                });
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
        final MapaCanvasOpenStreet mapa = this;

        Platform.runLater(new Runnable() {
            public void run() {
                ProxySever.get().disable();
                webEngine.loadContent(html);
                jfxPanel.invalidate();
            }
        });
    }
    
    public void setUrl(String url) {
    	this.url=url;
    }

    @Override
    public void setKML(String kml) {
        try {
            String html = IOUtils.toString(getClass().getResourceAsStream("main.html"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
            String js = IOUtils.toString(getClass().getResourceAsStream("L.KML.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
            String markerclusterjs = IOUtils.toString(getClass().getResourceAsStream("leaflet.markercluster.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$

            String layers_img = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
        	.encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("layers.png"))); //$NON-NLS-1$
            
            html = html.replace("{{layers_img}}", layers_img);
            html = html.replace("{{markerclusterjs}}", markerclusterjs);
            html = html.replace("{{tileServerUrl}}", url);
            html = html.replace("{{toolbar}}", getToolBarHtml());

            
            String b64_selecionado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
        	.encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado.png"))); //$NON-NLS-1$
            String b64_selecionado_m = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
            	.encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado_m.png"))); //$NON-NLS-1$
            String b64_normal = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
            	.encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_normal.png"))); //$NON-NLS-1$
            String b64_marcado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
            	.encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_marcado.png"))); //$NON-NLS-1$
            
            //html = replaceApiKey(html);

            html = html.replace("{{L.KML}}", js); //$NON-NLS-1$
            //html = html.replace("{{load_keydragzoom}}", js2); //$NON-NLS-1$
            //html = html.replace("{{load_extensions}}", js3); //$NON-NLS-1$
            //html = html.replace("{{load_geoxml3_ext}}", js4); //$NON-NLS-1$
            //html = html.replace("{{icone_selecionado_base64}}", b64_selecionado); //$NON-NLS-1$
            //html = html.replace("{{icone_selecionado_m_base64}}", b64_selecionado_m); //$NON-NLS-1$
            //html = html.replace("{{icone_m_base64}}", b64_marcado); //$NON-NLS-1$
            //html = html.replace("{{kml}}", kml.replace("\n", "").replace("\r", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

            kml = kml.replace("\n", "").replace("\r", "");
            kml = kml.replace("<Document>", "<Document><Style id=\"item\"><IconStyle><Icon><href>{{icone_base64}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("<Document>", "<Document><Style id=\"itemSelecionado\"><IconStyle><Icon><href>{{b64_selecionado}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("<Document>", "<Document><Style id=\"itemSelecionadoMarcado\"><IconStyle><Icon><href>{{b64_selecionado_m}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("<Document>", "<Document><Style id=\"itemMarcado\"><IconStyle><Icon><href>{{b64_marcado}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("{{icone_base64}}", b64_normal);
            kml = kml.replace("{{b64_selecionado}}", b64_selecionado);
            kml = kml.replace("{{b64_selecionado_m}}", b64_selecionado_m);
            kml = kml.replace("{{b64_marcado}}", b64_marcado);
            kml = kml.replace("</Placemark>", "<styleUrl>#item</styleUrl></Placemark>");
            html = html.replace("{{kml}}", kml);

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
                            webEngine.executeScript("track.selecionaMarcador([\"" + marks[i] + "\"],'" + b + "');"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (marcadorselecionado) {
                        try {
                        	webEngine.executeScript("track.centralizaSelecao();");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
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
                    webEngine.executeScript("selecionaMarcador(\"" + mid + "\",'" + b + "');"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        ProxySever.get().disable();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        ProxySever.get().disable();
    }

}
