package iped.geo.impl;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import iped.data.IItemId;
import iped.engine.config.ConfigurationManager;
import iped.geo.AbstractMapCanvas;
import iped.geo.kml.GetResultsKMLWorker;
import iped.geo.kml.KMLResult;
import iped.geo.localization.Messages;
import iped.properties.BasicProps;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IMultiSearchResultProvider;

/* 
 * Classe que controla a integração da classe App com a classe MapaCanvas
 */

public class AppMapPanel extends JPanel implements Consumer<KMLResult> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    IMultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    MapCanvasFactory mcf;

    AbstractMapCanvas browserCanvas;
    boolean mapaDesatualizado = true; // variável para registrar se os dados a serem apresentados pelo mapa precisa
    // renderização
    KMLResult kmlResult;
    JTable resultsTable;
    boolean mapSrcSelected = false;
    ActionListener changeTileServer = null;

    String tilesSourceURL = null, savedTilesSourceURL = null;
    private MapPanelConfig mpConfig;

    private JProgressBar gpsProgressBar;

    public AppMapPanel(IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsProvider = resultsProvider;
        this.guiProvider = guiProvider;
        this.setLayout(new BorderLayout());

        init();
    }

    public void init() {
        gpsProgressBar = new JProgressBar();
        gpsProgressBar.setOpaque(true);
        gpsProgressBar.setStringPainted(true);
        gpsProgressBar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.gray, 1), BorderFactory.createEmptyBorder(4, 0, 4, 0)));

        mcf = new MapCanvasFactory(this);

        this.addMouseListener(new MouseListener() {
            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                updateMap();
            }
        });

        savedTilesSourceURL = JMapOptionsPane.getSavedTilesSourceURL();

        mpConfig = new MapPanelConfig();

        try {
            ConfigurationManager.get().addObject(mpConfig);
            ConfigurationManager.get().loadConfigs();
        } catch (Exception e) {
            tilesSourceURL = null;
        }

        final Component self = this;
        changeTileServer = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final String leadSelectionToApply = browserCanvas.getLeadSelectionToApply();
                StringBuffer url = new StringBuffer("");
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            String result = JMapOptionsPane.showOptionsDialog(self);
                            if (result != null) {
                                url.append(result);
                            }
                        }
                    });
                } catch (InvocationTargetException | InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (url.length() > 0) {
                            tilesSourceURL = url.toString();
                            config(tilesSourceURL);
                            mapaDesatualizado = true;

                            /*
                             * Sends the current lead selection to the next map
                             * rendered to select it after load.
                             * */
                            runAfterLoad(new Runnable() {
                                @Override
                                public void run() {
                                    browserCanvas.sendLeadSelection(leadSelectionToApply);
                                    browserCanvas.update();
                                }
                            });

                            updateMap();
                        }
                    }
                });
            }
        };
    }

    public void config(String url) {
        if (url == null) {
        } else {
            // remove o antigo browsercanvas, caso haja algum configurado
            if (browserCanvas != null) {
                this.remove(browserCanvas.getContainer());
                this.repaint();
            }

            browserCanvas = mcf.createMapCanvas(url);

            gpsProgressBar.setVisible(false);
            this.add(gpsProgressBar, BorderLayout.NORTH);
            this.add(browserCanvas.getContainer(), BorderLayout.CENTER);
        }
    }

    public void updateMap() {
        if (tilesSourceURL == null) {
            if (savedTilesSourceURL != null) {
                tilesSourceURL = savedTilesSourceURL;
            } else {
                try {
                    tilesSourceURL = mpConfig.getTileServerUrlPattern();
                } catch (Exception e) {
                    tilesSourceURL = null;
                }
            }

            if (tilesSourceURL == null) {
                tilesSourceURL = JMapOptionsPane.showOptionsDialog(this);
            }
            config(tilesSourceURL);
        }

        if (mapaDesatualizado && (resultsProvider.getResults().getLength() > 0)) {
            this.kmlResult = null;

            gpsProgressBar.setString(Messages.getString("KMLResult.LoadingGPSData") + "..."); //$NON-NLS-1$ //$NON-NLS-2$
            gpsProgressBar.setValue(0);
            gpsProgressBar.setVisible(true);

            String[] cols = new String[] { BasicProps.ID };
            GetResultsKMLWorker kmlWorker = new GetResultsKMLWorker(resultsProvider, cols, gpsProgressBar, this);
            kmlWorker.execute();

        } else {
            browserCanvas.update();
        }
    }

    @Override
    public void accept(KMLResult kmlResult) {
        if (kmlResult.getItemsWithGPS() == 0) {
            gpsProgressBar.setValue(0);
            gpsProgressBar.setString(Messages.getString("KMLResult.NoGPSItem"));
        } else {
            gpsProgressBar.setVisible(false);
        }
        browserCanvas.setKML(kmlResult.getKML());
        this.kmlResult=kmlResult;
        mapaDesatualizado = false;
    }

    public void update() {
        browserCanvas.update();
        mapaDesatualizado = false;
    }

    public boolean isMapOutDated() {
        return mapaDesatualizado;
    }

    public void setMapOutDated(boolean mapOutDated) {
        this.mapaDesatualizado = mapOutDated;
    }

    public void selectCheckbox(IItemId item, boolean b) {

        if (kmlResult != null && kmlResult.getGPSItems().containsKey(item)) {
            List<Integer> subitems = kmlResult.getGPSItems().get(item);
            if (subitems == null) {
                String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
                browserCanvas.selectCheckbox(gid, b);
            } else {
                for (Integer subitem : subitems) {
                    String gid = "marker_" + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$
                    browserCanvas.selectCheckbox(gid, b);
                }
            }

        }
    }

    public IMultiSearchResultProvider getResultsProvider() {
        return resultsProvider;
    }

    public void runAfterLoad(Runnable run) {
        if (this.kmlResult != null && mapaDesatualizado == false) {
            run.run();
        } else {
            if (browserCanvas != null) {
                browserCanvas.runAfterLoad(run);
            }
        }
    }

}
