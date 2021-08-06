package dpf.mt.gpinf.mapas.webkit;

import dpf.mt.gpinf.mapas.AbstractMapaCanvas;
import dpf.sp.gpinf.network.util.ProxySever;
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

public abstract class AbstractWebkitMapaCanvas extends AbstractMapaCanvas {
    WebView browser;
    WebEngine webEngine = null;
    final JFXPanel jfxPanel;

    JSInterfaceFunctions jsInterface;

    public AbstractWebkitMapaCanvas() {
        this.jfxPanel = new JFXPanel();

        Platform.runLater(new Runnable() {
            public void run() {
                browser = new WebView();
                jfxPanel.setScene(new Scene(browser));
                
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
    public void setText(final String html) {
        final AbstractWebkitMapaCanvas mapa = this;

        Platform.runLater(new Runnable() {
            public void run() {
                ProxySever.get().disable();
                webEngine.loadContent(html);
                jfxPanel.invalidate();
            }
        });
    }

}
