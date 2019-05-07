package dpf.mt.gpinf.mapas.impl;

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
import dpf.mt.gpinf.mapas.util.Messages;
import iped3.ItemId;
import iped3.desktop.GUIProvider;
import iped3.desktop.ResultSetViewer;
import iped3.search.MultiSearchResultProvider;

public class MapaViewer implements ResultSetViewer, TableModelListener, ListSelectionListener {

    JTable resultsTable;
    MultiSearchResultProvider resultsProvider;
    GUIProvider guiProvider;
    AppMapaPanel mapaPanel;
    JScrollPane mapsScroll;
    DefaultSingleCDockable dockable; // dockable where the viewer is installed

    public static volatile boolean desabilitaTemp = false;
    public static volatile boolean updatingSelection = false;

    public MapaViewer() {
    }

    @Override
    public void init(JTable resultsTable, MultiSearchResultProvider resultsProvider, GUIProvider guiProvider) {
        this.resultsTable = resultsTable;
        this.resultsProvider = resultsProvider;
        this.guiProvider = guiProvider;
        mapaPanel = new AppMapaPanel(resultsProvider, guiProvider);
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
        mapaPanel.redesenhaMapa();
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        /*
         * if(e.getColumn()==-1) { if((e.getFirstRow()==0)) { return; } }
         */
        if (e.getColumn() == 1) {// se o evento foi disparado pelo check box que fica na coluna 1
            updatingSelection = true;
            ItemId item = resultsProvider.getResults().getItem(e.getFirstRow());

            Boolean b = (Boolean) resultsTable.getModel().getValueAt(e.getFirstRow(), e.getColumn());

            mapaPanel.selecionaMarcador(item, !b.booleanValue());
        }

        /* Se a alteração foi feita no próprio mapa, ela não precisa ser refeita. */
        if (!desabilitaTemp) {
            mapaPanel.setMapaDesatualizado(true);

            /* somente chamado se o tab de mapas estiver sendo exibido */
            if (dockable != null && dockable.isShowing()) {
                if (!updatingSelection)
                    mapaPanel.redesenhaMapa();
                else {
                    mapaPanel.redesenha();
                }

                updatingSelection = false;
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
            if (resultsTable.getSelectedRowCount() > 0)
                for (int i = e.getFirstIndex(); i <= e.getLastIndex(); i++) {
                    boolean selected = lsm.isSelectedIndex(i);

                    int rowModel = resultsTable.convertRowIndexToModel(i);
                    ItemId item = resultsProvider.getResults().getItem(rowModel);

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
            mapaPanel.browserCanvas.enviaSelecoes(selecoes);

            if (dockable.isShowing()) {
                mapaPanel.browserCanvas.redesenha();
            }
        }
    }

    @Override
    public void updateSelection() {
        updatingSelection = true;
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
