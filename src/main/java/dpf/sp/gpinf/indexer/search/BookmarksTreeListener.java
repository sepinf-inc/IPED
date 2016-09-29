package dpf.sp.gpinf.indexer.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

public class BookmarksTreeListener implements TreeSelectionListener, TreeExpansionListener {

  public HashSet<String> selection = new HashSet<String>();
  private volatile boolean updatingSelection = false;
  private long collapsed = 0;

  @Override
  public void valueChanged(TreeSelectionEvent evt) {

    if (updatingSelection) {
      return;
    }

    if (System.currentTimeMillis() - collapsed < 500) {
      if (evt.getPath().getLastPathComponent().equals(BookmarksTreeModel.ROOT)) {
        updateModelAndSelection();
      }
      return;
    }

    for (TreePath path : evt.getPaths()) {
      if (selection.contains(path.getLastPathComponent())) {
        selection.remove(path.getLastPathComponent());
      } else {
        selection.add((String) path.getLastPathComponent());
      }
    }

    App.get().appletListener.updateFileListing();

    if (selection.contains(BookmarksTreeModel.ROOT) || selection.isEmpty()) {
      App.get().treeTab.setBackgroundAt(1, App.get().defaultTabColor);
    } else {
      App.get().treeTab.setBackgroundAt(1, App.get().alertColor);
    }

  }

  public void updateModelAndSelection() {

    updatingSelection = true;
    TreeMap<Integer, String> labelMap = ((BookmarksTreeModel) App.get().bookmarksTree.getModel()).labelMap;

    if (labelMap != null && !selection.isEmpty()) {

      HashSet<String> tempSel = (HashSet<String>) selection.clone();
      selection.clear();
      if (tempSel.contains(BookmarksTreeModel.NO_BOOKMARKS)) {
        selection.add(BookmarksTreeModel.NO_BOOKMARKS);
      }

      for (String path : tempSel) {
        for (int i : labelMap.keySet()) {
          if (labelMap.get(i).equals(path)) {
            String name = App.get().appCase.marcadores.getLabelMap().get(i);
            if (name != null) {
              selection.add(name);
            }
          }
        }
      }

      ArrayList<TreePath> selectedPaths = new ArrayList<TreePath>();
      for (String name : selection) {
        String[] path = {BookmarksTreeModel.ROOT, name};
        selectedPaths.add(new TreePath(path));
      }

      boolean rootCollapsed = App.get().bookmarksTree.isCollapsed(0);
      App.get().bookmarksTree.setModel(new BookmarksTreeModel());
      if (rootCollapsed) {
        App.get().bookmarksTree.collapseRow(0);
      }

      App.get().bookmarksTree.setSelectionPaths(selectedPaths.toArray(new TreePath[0]));

    } else {
      boolean rootCollapsed = App.get().bookmarksTree.isCollapsed(0);
      App.get().bookmarksTree.setModel(new BookmarksTreeModel());
      if (rootCollapsed) {
        App.get().bookmarksTree.collapseRow(0);
      }
    }
    updatingSelection = false;
  }

  @Override
  public void treeExpanded(TreeExpansionEvent event) {

  }

  @Override
  public void treeCollapsed(TreeExpansionEvent event) {
    collapsed = System.currentTimeMillis();

  }

}
