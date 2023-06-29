package iped.geo.openstreet;

import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;

import javax.swing.UIManager;

import org.apache.commons.io.IOUtils;

import iped.geo.AbstractMapCanvas;
import iped.geo.impl.JMapOptionsPane;
import iped.utils.UiUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebErrorEvent;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

public class MapCanvasOpenStreet extends AbstractMapCanvas {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private static final String DARK_THEME_SCRIPT = "applyDarkTheme();";
    private static final String LIGHT_THEME_SCRIPT = "applyLightTheme();";
    WebView browser;
    WebEngine webEngine = null;
    final JFXPanel jfxPanel;
    JSInterfaceFunctionsOpenStreet jsInterface = new JSInterfaceFunctionsOpenStreet(this);

    boolean dragging = false;
    double dragStartX, dragStartY;
    ChangeListener<State> onLoadChange;

    String url;
    String lastGoogleApiKey = null;
    private boolean htmlloaded;
    private Semaphore sem;
    private String themeScript;

    public MapCanvasOpenStreet() {
        this.jfxPanel = new JFXPanel();

        this.url = JMapOptionsPane.getSavedTilesSourceURL();
        if (url != null) {
            int i = url.indexOf("key=");
            if (i >= 0) {
                lastGoogleApiKey = url.substring(url.indexOf("key="));
            }
        }

        Platform.runLater(new Runnable() {
            public void run() {
                browser = new WebView();
                jfxPanel.setScene(new Scene(browser));

                browser.setOnMouseReleased(e -> {
                    dragging = false;
                });

                browser.setOnMouseDragged(e -> {

                    if (dragging) {
                        final int x = (int) Math.ceil(dragStartX - e.getX());
                        final int y = (int) Math.ceil(dragStartY - e.getY());

                        try {
                            webEngine.executeScript("map.panBy({x:" + x + ", y:" + y + "},{duration: 1,easeLinearity: 1,noMoveStart: true,animate: false})");
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }

                    dragging = true;
                    dragStartX = e.getX();
                    dragStartY = e.getY();

                });

                webEngine = browser.getEngine();
                webEngine.setJavaScriptEnabled(true);
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

                webEngine.loadContent(UiUtil.getUIEmptyHtml());

                onLoadChange = new ChangeListener<State>() {
                    @Override
                    public void changed(ObservableValue<? extends State> observable, State oldState, State newState) {
                        if (newState == State.SUCCEEDED) {
                            JSObject window = (JSObject) webEngine.executeScript("window"); //$NON-NLS-1$
                            window.setMember("app", jsInterface); //$NON-NLS-1$
                            try {
                                executeOnLoadRunnables();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
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
        Platform.runLater(new Runnable() {
            public void run() {
                webEngine.getLoadWorker().stateProperty().removeListener(onLoadChange);
                webEngine.getLoadWorker().stateProperty().addListener(onLoadChange);
                runAfterLoad(new Runnable() {
                    @Override
                    public void run() {
                        updateUI();
                    }
                });
                webEngine.loadContent(html);
                jfxPanel.invalidate();
            }
        });
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public void load() {
        try {
            if (sem != null) {
                sem.release();
            }
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    webEngine.getLoadWorker().cancel();
                }
            });

            clearAddPlacemarkLines();

            sem = new Semaphore(1);
            sem.acquire();

            String b64_selecionado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado.png"))); //$NON-NLS-1$
            String b64_selecionado_m = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado_m.png"))); //$NON-NLS-1$
            String b64_normal = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_normal.png"))); //$NON-NLS-1$
            String b64_marcado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_marcado.png"))); //$NON-NLS-1$

            String kml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml xmlns=\"http://www.opengis.net/kml/2.2\">";
            kml += "<Document>";
            kml += "<Style id=\"item\"><IconStyle><Icon><href>{{icone_base64}}</href></Icon></IconStyle></Style>";
            kml += "<Style id=\"itemSelecionado\"><IconStyle><Icon><href>{{b64_selecionado}}</href></Icon></IconStyle></Style>";
            kml += "<Style id=\"itemSelecionadoMarcado\"><IconStyle><Icon><href>{{b64_selecionado_m}}</href></Icon></IconStyle></Style>";
            kml += "<Style id=\"itemMarcado\"><IconStyle><Icon><href>{{b64_marcado}}</href></Icon></IconStyle></Style>";
            kml = kml.replace("{{icone_base64}}", b64_normal);
            kml = kml.replace("{{b64_selecionado}}", b64_selecionado);
            kml = kml.replace("{{b64_selecionado_m}}", b64_selecionado_m);
            kml = kml.replace("{{b64_marcado}}", b64_marcado);
            kml += "</Document>";
            kml += "</kml>";

            String html = getMainHtml();
            String kmlFinal = kml.toString();
            runAfterLoad(new Runnable() {
                @Override
                public void run() {
                    try {
                        webEngine.executeScript("track.parseStylesFromXmlString('" + kmlFinal + "');");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        sem.release();
                    }
                }
            });

            setText(html);

            sem.acquire();
            sem.release();
        } catch (Exception e) {
            loaded = false;
            if ((e instanceof CancellationException) || (e instanceof InterruptedException)) {
                sem.release();
            } else {
                e.printStackTrace();
            }
        }
    }

    public String getMainHtml() throws IOException {
        if (this.url == null) {
            setUrl(JMapOptionsPane.getSavedTilesSourceURL());
        }

        String html = IOUtils.toString(getClass().getResourceAsStream("main.html"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
        String js = IOUtils.toString(getClass().getResourceAsStream("L.KML.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
        String markerclusterjs = IOUtils.toString(getClass().getResourceAsStream("leaflet.markercluster.js"), //$NON-NLS-1$
                "UTF-8"); //$NON-NLS-1$
        String leafletarrowheads = IOUtils.toString(getClass().getResourceAsStream("leaflet-arrowheads.js"), //$NON-NLS-1$
                "UTF-8"); //$NON-NLS-1$
        String leafletgeometryutil = IOUtils.toString(getClass().getResourceAsStream("leaflet.geometryutil.js"), //$NON-NLS-1$
                "UTF-8"); //$NON-NLS-1$

        String layers_img = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("layers.png"))); //$NON-NLS-1$

        html = html.replace("{{layers_img}}", layers_img);
        html = html.replace("{{markerclusterjs}}", markerclusterjs);
        html = html.replace("{{leafletgeometryutil}}", leafletgeometryutil);
        html = html.replace("{{leafletarrowheads}}", leafletarrowheads);

        String apikey = JMapOptionsPane.getGoogleAPIKey();
        html = html.replace("{{googlemaps_scripts}}", "<script id=\"mapsapi\" src=\"https://maps.googleapis.com/maps/api/js?key=" + apikey + "\" async defer></script>\n"
                + "<script src=\"https://unpkg.com/leaflet.gridlayer.googlemutant@latest/dist/Leaflet.GoogleMutant.js\"></script>");

        html = html.replace("{{tilelayer_script}}", "setTileServerUrl('" + url + "');");

        themeScript = MapCanvasOpenStreet.LIGHT_THEME_SCRIPT;
        Color bgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");
        if (bgColor != null) {
            // disabled for now, see https://github.com/sepinf-inc/IPED/issues/1443
            // themeScript = MapCanvasOpenStreet.DARK_THEME_SCRIPT;
        }
        html = html.replace("{{applyTheme}}", themeScript);

        html = html.replace("{{toolbar}}", getToolBarHtml());

        html = html.replace("{{L.KML}}", js); //$NON-NLS-1$
        return html;
    }

    @Override
    public void setKML(String kml) {
        if (kml == null || kml.length() < 4) {
            return;
        }
        try {
            String html = getMainHtml();

            String b64_selecionado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado.png"))); //$NON-NLS-1$
            String b64_selecionado_m = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado_m.png"))); //$NON-NLS-1$
            String b64_normal = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_normal.png"))); //$NON-NLS-1$
            String b64_marcado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_marcado.png"))); //$NON-NLS-1$

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

            setText(html);

            final String kmlFinal = kml;

            if (!htmlloaded) {
                htmlloaded = true;
                runAfterLoad(new Runnable() {
                    @Override
                    public void run() {
                        webEngine.executeScript("loadKml('" + kmlFinal + "')");
                    }
                });
            } else {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        webEngine.executeScript("loadKml('" + kmlFinal + "')");
                    }
                });
            }
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
    public void update() {
        MapCanvasOpenStreet self = this;
        if (self.selectionMapToApply != null) {
            // repinta selecoes alteradas
            final String[] marks = new String[self.selectionMapToApply.keySet().size()];
            self.selectionMapToApply.keySet().toArray(marks);
            final HashMap<String, Boolean> selecoesAfazerCopy = selectionMapToApply;
            self.selectionMapToApply = null;


            Runnable selecionaMarcadores = new Runnable() {
                public void run() {
                    boolean marcadorselecionado = false;
                    StringBuffer script = new StringBuffer();

                    if (marks.length > 0) {
                        marcadorselecionado = true;
                        script.append("track.deselectAll();");
                    }

                    for (int i = 0; i < marks.length; i++) {
                        if ((marks[0].equals(ALLMARKERS_TAG))) {
                            continue;
                        }
                        Boolean b = selecoesAfazerCopy.get(marks[i]);
                        try {
                            script.append("track.selecionaMarcador([\"" + marks[i] + "\"],'" + b + "');");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (marcadorselecionado) {
                        webEngine.executeScript(script.toString()); // $NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        try {
                            webEngine.executeScript("track.centralizaSelecao();");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            if (Platform.isFxApplicationThread()) {
                selecionaMarcadores.run();
            } else {
                Platform.runLater(selecionaMarcadores);
            }

        }
        if (self.leadSelectionToApply != null) {
            final String leadSelectionToApplyCopy = self.leadSelectionToApply;
            Platform.runLater(new Runnable() {
                public void run() {
                    try {
                        webEngine.executeScript("track.updateLeadMarker(\"" + leadSelectionToApplyCopy + "\");");
                        if (markerEventListener != null) {
                            markerEventListener.onClicked(leadSelectionToApplyCopy, null);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // nothing
                    }
                }
            });
        }
        updateUI();
    }

    public void updateUI() {
        boolean updateTheme = false;
        Color bgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");

        // Dark theme disabled for now until it is optimized, see https://github.com/sepinf-inc/IPED/issues/1443
        if (bgColor != null && themeScript.equals(MapCanvasOpenStreet.LIGHT_THEME_SCRIPT)) {
            // themeScript = MapCanvasOpenStreet.DARK_THEME_SCRIPT;
            // updateTheme = true;
        }
        if (bgColor == null && themeScript.equals(MapCanvasOpenStreet.DARK_THEME_SCRIPT)) {
            // themeScript = MapCanvasOpenStreet.LIGHT_THEME_SCRIPT;
            // updateTheme = true;
        }

        if (updateTheme) {
            final String themeScriptFinal = themeScript;
            Platform.runLater(new Runnable() {
                public void run() {
                    try {
                        webEngine.executeScript(themeScriptFinal);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // nothing
                    }
                }
            });
        }
    }

    @Override
    public void selectCheckbox(final String mid, final boolean b) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript("track.marca(\"" + mid + "\",'" + b + "');"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void viewAll() {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript("track.fire('loaded');track.viewAll();");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    private void clearAddPlacemarkLines() {
    }

    @Override
    public void viewAll(double minlongit, double minlat, double maxlongit, double maxlat) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript("asyncCreatePaths();track.setAllRange('" + minlongit + "','" + minlat + "','" + maxlongit + "','" + maxlat + "');");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    @Override
    public boolean setTileServerUrl(String url) {
        setUrl(url);
        if (url.contains("googleapis") && url.contains("key") && !url.substring(url.indexOf("key=")).equals(lastGoogleApiKey)) {
            lastGoogleApiKey = url.substring(url.indexOf("key="));
            return true;
        } else {
            if (isLoaded()) {
                Platform.runLater(new Runnable() {
                    public void run() {
                        try {
                            webEngine.executeScript("setTileServerUrl('" + url + "');");
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            // nothing
                        }
                    }
                });
            }
            return false;
        }
    }

    @Override
    public void updateView(List<StringBuffer> gids) {
        MapCanvasOpenStreet self = this;
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    if (gids.size() > 0) {
                        webEngine.executeScript("track.clearVisibleMarkers();");
                        for (int i = 0; i < gids.size(); i++) {
                            webEngine.executeScript("track.showMarkers(" + gids.get(i).toString() + ");");
                        }
                        webEngine.executeScript("track.orderVisibleMarkers();");
                    } else {
                        webEngine.executeScript("track.clearVisibleMarkers();");
                    }
                    webEngine.executeScript("track.tourOrder='"+self.tourOrder+"';");                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    @Override
    public void createPlacemarks(List<StringBuffer> gids) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    if (gids.size() > 0) {
                        for (int i = 0; i < gids.size(); i++) {
                            String jsgids = gids.get(i).toString();
                            webEngine.executeScript("track.createMarkers(" + jsgids + ");");
                        }
                        webEngine.executeScript("track.orderVisibleMarkers();");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    @Override
    public void drawPolyline(List<StringBuffer> gids) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    if (gids.size() > 0) {
                        for (int i = 0; i < gids.size(); i++) {
                            webEngine.executeScript("try{track.drawPolyline(" + gids.get(i).toString() + ");}catch(e){alert(e);}");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    @Override
    public void drawJSONFeature(String jsonFeature) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    if (jsonFeature != null && jsonFeature.length() > 3) {
                        webEngine.executeScript("try{track.hideLastFeature();track.drawFeature(" + jsonFeature + ");}catch(e){alert(e);}");
                    } else {
                        webEngine.executeScript("try{track.hideLastFeature();}catch(e){alert(e);}");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    @Override
    public void drawJSONFeatures(String[] jsonFeature) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    if (jsonFeature.length > 0) {
                        StringBuffer str = new StringBuffer();
                        for (int i = 0; i < jsonFeature.length; i++) {
                            str.append("track.drawFeature(" + convertToGeoJson(jsonFeature[i]) + ");");
                        }

                        if (str.length() > 3) {
                            webEngine.executeScript("try{track.hideLastFeature();+" + str.toString() + "}catch(e){alert(e);}");
                        } else {
                            webEngine.executeScript("try{track.hideLastFeature();}catch(e){alert(e);}");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    private String convertToGeoJson(String string) {
        if (string.trim().startsWith("{")) {
            return string;
        }
        return null;
    }
}
