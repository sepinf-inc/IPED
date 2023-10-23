package iped.geo.webkit;

import iped.geo.AbstractMapCanvas;
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

public abstract class AbstractWebkitMapCanvas extends AbstractMapCanvas {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    WebView browser;
    WebEngine webEngine = null;
    final JFXPanel jfxPanel;

    JSInterfaceFunctions jsInterface;

    public AbstractWebkitMapCanvas() {
        this.jfxPanel = new JFXPanel();

        Platform.runLater(new Runnable() {
            public void run() {
                browser = new WebView();
                jfxPanel.setScene(new Scene(browser));

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
        Platform.runLater(new Runnable() {
            public void run() {
                webEngine.loadContent(html);
                jfxPanel.invalidate();
            }
        });
    }

}
