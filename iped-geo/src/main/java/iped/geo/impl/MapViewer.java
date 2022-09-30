package iped.geo.impl;

import java.util.HashMap;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import bibliothek.gui.dock.common.DefaultSingleCDockable;
import iped.data.IItemId;
import iped.geo.localization.Messages;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.api.ResultSetViewer;
import javafx.application.Platform;

public class MapViewer implements ResultSetViewer, TableModelListener, ListSelectionListener {

    JTable resultsTable;
    IMultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    AppMapPanel mapaPanel;
    JScrollPane mapsScroll;
    DefaultSingleCDockable dockable; // dockable where the viewer is installed

    public static volatile boolean desabilitaTemp = false;
    public static volatile boolean updatingCheckbox = false;

    public MapViewer() {
    }

    @Override
    public void init(JTable resultsTable, IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsTable = resultsTable;
        this.resultsProvider = resultsProvider;
        this.guiProvider = guiProvider;
        mapaPanel = new AppMapPanel(resultsProvider, guiProvider);
        mapsScroll = new JScrollPane(mapaPanel);
        resultsTable.getModel().addTableModelListener(this);
        resultsTable.getSelectionModel().addListSelectionListener(this);
    }

    @Override
    public String getTitle() {
        return Messages.getString("App.Map");
    }

    @Override
    public String getID() {
        return "maptab";
    }

    @Override
    public JScrollPane getPanel() {
        return mapsScroll;
    }

    @Override
    public void redraw() {
        mapaPanel.updateMap();
    }

    @Override
    public void checkAll(boolean value) {
        updatingCheckbox = true;
        for (IItemId itemId : resultsProvider.getResults().getIterator()) {
            mapaPanel.selectCheckbox(itemId, value);
        }
        updatingCheckbox = false;
        Platform.runLater(new Runnable() {
            public void run() {
                mapaPanel.update();
            }
        });
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        /*
         * if(e.getColumn()==-1) { if((e.getFirstRow()==0)) { return; } }
         */
        if (e.getColumn() == 1) {// se o evento foi disparado pelo check box que fica na coluna 1
            updatingCheckbox = true;
            IItemId item = resultsProvider.getResults().getItem(e.getFirstRow());

            Boolean b = (Boolean) resultsTable.getModel().getValueAt(e.getFirstRow(), e.getColumn());

            mapaPanel.selectCheckbox(item, b.booleanValue());
        }

        /* Se a alteração foi feita no próprio mapa, ela não precisa ser refeita. */
        if (!desabilitaTemp) {
            mapaPanel.setMapOutDated(true);

            /* somente chamado se o tab de mapas estiver sendo exibido */
            if (dockable != null && dockable.isShowing()) {
                if (!updatingCheckbox)
                    mapaPanel.updateMap();
                else {
                    mapaPanel.update();
                }

                updatingCheckbox = false;
            }
        } else {
            // reabilita renderização automatica pela alteração no modelo
            desabilitaTemp = false;
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting())
            return;

        if ((!mapaPanel.mapaDesatualizado)) {
            ListSelectionModel lsm = (ListSelectionModel) e.getSource();
            HashMap<String, Boolean> selecoes = new HashMap<String, Boolean>();
            for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
                boolean selected = lsm.isSelectedIndex(i);

                int rowModel = resultsTable.convertRowIndexToModel(i);
                IItemId item = resultsProvider.getResults().getItem(rowModel);

                if (mapaPanel.kmlResult != null && mapaPanel.kmlResult.getGPSItems().containsKey(item)) {
                    List<Integer> subitems = mapaPanel.kmlResult.getGPSItems().get(item);
                    if (subitems == null) {
                        String gid = "marker_" + item.getSourceId() + "_" + item.getId(); //$NON-NLS-1$ //$NON-NLS-2$
                        selecoes.put(gid, selected);
                    } else {
                        for (Integer subitem : subitems) {
                            String gid = "marker_" + item.getSourceId() + "_" + item.getId() + "_" + subitem; //$NON-NLS-1$ //$NON-NLS-2$
                            selecoes.put(gid, selected);
                        }
                    }
                }
            }
            mapaPanel.browserCanvas.sendSelection(selecoes);

            if (dockable.isShowing()) {
                mapaPanel.browserCanvas.update();
            }
        }
    }

    @Override
    public void updateSelection() {
        updatingCheckbox = true;
    }

    @Override
    public void setDockableContainer(DefaultSingleCDockable dockable) {
        this.dockable = dockable;
    }

    @Override
    public GUIProvider getGUIProvider() {
        return guiProvider;
    }

}
