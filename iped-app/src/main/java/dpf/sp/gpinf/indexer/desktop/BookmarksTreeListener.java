package dpf.sp.gpinf.indexer.desktop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

public class BookmarksTreeListener implements TreeSelectionListener, TreeExpansionListener, ClearFilterListener {

    public HashSet<String> selection = new HashSet<String>();
    private volatile boolean updatingSelection = false;
    private long collapsed = 0;
    private boolean clearing = false;

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

        if (!clearing)
            App.get().appletListener.updateFileListing();

        if (selection.contains(BookmarksTreeModel.ROOT) || selection.isEmpty()) {
            App.get().setBookmarksDefaultColor(true);
        } else {
            App.get().setBookmarksDefaultColor(false);
        }

    }

    public void updateModelAndSelection() {

        updatingSelection = true;
        Set<String> labelSet = ((BookmarksTreeModel) App.get().bookmarksTree.getModel()).labels;

        if (labelSet != null && !selection.isEmpty()) {

            HashSet<String> tempSel = (HashSet<String>) selection.clone();
            selection.clear();
            if (tempSel.contains(BookmarksTreeModel.NO_BOOKMARKS)) {
                selection.add(BookmarksTreeModel.NO_BOOKMARKS);
            }

            for (String path : tempSel) {
                if (App.get().appCase.getMultiBookmarks().getAllBookmarks().contains(path)) {
                    selection.add(path);
                }
            }

            ArrayList<TreePath> selectedPaths = new ArrayList<TreePath>();
            for (String name : selection) {
                String[] path = { BookmarksTreeModel.ROOT, name };
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

    @Override
    public void clearFilter() {
        clearing = true;
        App.get().bookmarksTree.clearSelection();
        clearing = false;
    }

}
