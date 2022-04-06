package dpf.sp.gpinf.indexer.desktop;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class BookmarksTreeModel implements TreeModel {

    public static String ROOT = Messages.getString("BookmarksTreeModel.RootName"); //$NON-NLS-1$
    public static String NO_BOOKMARKS = Messages.getString("BookmarksTreeModel.NoBookmarks"); //$NON-NLS-1$
    public Set<String> bookmarks;

    static class Bookmark implements Comparator<Bookmark> {

        int id;
        String name;

        public Bookmark(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public boolean equals(Bookmark b) {
            return this.id == b.id;
        }

        @Override
        final public int compare(Bookmark a, Bookmark b) {
            return a.name.compareTo(b.name);
        }
    }

    @Override
    public Object getRoot() {
        return ROOT;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (!ROOT.equals(parent)) {
            return null;
        }

        if (bookmarks == null) {
            bookmarks = new TreeSet<>();
            bookmarks.addAll(App.get().appCase.getMultiBookmarks().getBookmarkSet());
        }

        if (index == 0) {
            return NO_BOOKMARKS;
        }

        String[] array = bookmarks.toArray(new String[0]);
        Arrays.sort(array, Collator.getInstance());

        return array[index - 1];

    }

    @Override
    public int getChildCount(Object parent) {
        if (!ROOT.equals(parent)) {
            return 0;
        } else {
            if (App.get().appCase == null) {
                return 0;
            } else {
                return App.get().appCase.getMultiBookmarks().getBookmarkSet().size() + 1;
            }
        }
    }

    @Override
    public boolean isLeaf(Object node) {
        if (!ROOT.equals(node)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        System.out.println("get index of child"); //$NON-NLS-1$
        return 0;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        // treeModelListeners.addElement(l);

    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        // treeModelListeners.removeElement(l);

    }

}
