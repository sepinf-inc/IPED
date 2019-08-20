package iped3.desktop;

import javax.swing.JScrollPane;
import javax.swing.JTable;

import bibliothek.gui.dock.common.DefaultSingleCDockable;
import iped3.search.IMultiSearchResultProvider;

/*
 * Defines a viewer for a set of results controlled by a MultiSearchResultProvider
 */

public interface ResultSetViewer {

    public void init(JTable resultsTable, IMultiSearchResultProvider resultsProvider, GUIProvider guiProvider);

    public void setDockableContainer(DefaultSingleCDockable dockable);

    public String getTitle();

    public String getID();

    public JScrollPane getPanel();

    public void redraw();

    public void updateSelection();

    public GUIProvider getGUIProvider();

}
