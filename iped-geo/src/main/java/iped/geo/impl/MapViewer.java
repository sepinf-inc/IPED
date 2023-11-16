package iped.geo.impl;

import java.util.HashMap;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import bibliothek.gui.dock.common.DefaultSingleCDockable;
import iped.data.IItemId;
import iped.geo.localization.Messages;
import iped.search.IMultiSearchResult;
import iped.viewers.api.GUIProvider;
import iped.viewers.api.IMultiSearchResultProvider;
import iped.viewers.api.ResultSetViewer;
import javafx.application.Platform;

public class MapViewer implements ResultSetViewer, TableModelListener, ListSelectionListener {

    JTable resultsTable;
    IMultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    AppMapPanel mapaPanel;
    DefaultSingleCDockable dockable; // dockable where the viewer is installed

    public static volatile boolean desabilitaTemp = false; // disables unnecessary map updates
    public static volatile boolean updatingCheckbox = false;

    public MapViewer() {
    }

    @Override
    public void init(JTable resultsTable, IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsTable = resultsTable;
        this.resultsProvider = resultsProvider;
        this.guiProvider = guiProvider;
        mapaPanel = new AppMapPanel(resultsProvider, guiProvider);
        mapaPanel.setMapViewer(this);
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
    public JPanel getPanel() {
        return mapaPanel;
    }

    @Override
    public void redraw() {
        if (unprocessedChange != null) {
            tableChanged(unprocessedChange);
        }

        if (mapaPanel.browserCanvas.isLoaded()) {
            if (!updatingCheckbox) {
                mapaPanel.updateMap();
            }
        }
    }

    boolean internaltableChanged = false;
    private TableModelEvent unprocessedChange;

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
        if (!mapaPanel.isShowing()) {
            unprocessedChange = e;
            return;
        }

        unprocessedChange = null;

        if ((e.getFirstRow() == -1 && e.getLastRow() == -1)) {
            /**/
            return;
        }

        if (!mapaPanel.browserCanvas.isLoaded()) {
            mapaPanel.updateMap();
        }

        /*
         * Se a alteração foi feita no próprio mapa ou a operação é de ordenação, ela
         * não precisa ser refeita.
         */
        if (!desabilitaTemp) {
            if (e.getColumn() == 1) {// se o evento foi disparado pelo check box que fica na coluna 1
                updatingCheckbox = true;

                IItemId item = resultsProvider.getResults().getItem(e.getFirstRow());

                Boolean b = (Boolean) resultsTable.getModel().getValueAt(e.getFirstRow(), e.getColumn());

                mapaPanel.selectCheckbox(item, b.booleanValue());
            } else {
                mapaPanel.setMapOutDated(true);
            }

            /* somente chamado se o tab de mapas estiver sendo exibido */
            if (dockable != null && dockable.isShowing()) {
                if (!updatingCheckbox) {
                    mapaPanel.updateMap();
                } else {
                    mapaPanel.update();
                }

                updatingCheckbox = false;
            }
        } else {
            // reabilita renderização automatica pela alteração no modelo
            desabilitaTemp = false;
        }
        if (dockable == null || !dockable.isShowing()) {
            updatingCheckbox = false;
        }
    }

    public void updateMapLeadCursor() {
        // update internal map item cursor with lead selection
        int resultTableLeadSelIdx = resultsTable.getSelectionModel().getLeadSelectionIndex();
        try {
            if (resultTableLeadSelIdx != -1) {
                int rowModel = resultsTable.convertRowIndexToModel(resultTableLeadSelIdx);
                IItemId item = resultsProvider.getResults().getItem(rowModel);
                String gid = "marker_" + item.getSourceId() + "_" + item.getId();
                mapaPanel.browserCanvas.sendLeadSelection(gid);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() || resultsTable.getRowSorter() == null || desabilitaTemp)
            return;

        Runnable run = new Runnable() {
            @Override
            public void run() {
                IMultiSearchResult results = resultsProvider.getResults();
                if (results.getLength() == 0) {
                    mapaPanel.browserCanvas.clearSelection();
                } else {
                    HashMap<String, Boolean> selecoes = new HashMap<String, Boolean>();
                    ListSelectionModel lsm = (ListSelectionModel) e.getSource();
                    for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
                        boolean selected = lsm.isSelectedIndex(i);

                        try {
                            int rowModel = resultsTable.convertRowIndexToModel(i);

                            IItemId item = results.getItem(rowModel);
                            mapaPanel.addSelection(selecoes, item);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    mapaPanel.browserCanvas.sendSelection(selecoes);
                }

                updateMapLeadCursor();

                if (dockable.isShowing()) {
                    mapaPanel.browserCanvas.update();
                }
            }
        };

        mapaPanel.runAfterLoad(run);
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

    @Override
    public void notifyCaseDataChanged() {
        // TODO Auto-generated method stub
    }

}
