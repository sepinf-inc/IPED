package iped.app.ui;

import java.text.Collator;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public class BookmarksTreeModel implements TreeModel {

    public static final String ROOT_NAME = Messages.getString("BookmarksTreeModel.RootName"); //$NON-NLS-1$
    public static final String NO_BOOKMARKS_NAME = Messages.getString("BookmarksTreeModel.NoBookmarks"); //$NON-NLS-1$

    public static final BookmarkRoot ROOT = new BookmarkRoot();
    public static final NoBookmarks NO_BOOKMARKS = new NoBookmarks();

    public Set<String> bookmarks;

    private static class BookmarkRoot {
        public String toString() {
            return ROOT_NAME;
        }
    }

    private static class NoBookmarks {
        public String toString() {
            return NO_BOOKMARKS_NAME;
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
