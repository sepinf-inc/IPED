package iped.app.ui.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.app.ui.App;
import iped.engine.data.Category;

public class AIFiltersTreeModel implements TreeModel {

    public Category root;

    private List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    public static void install() {
        if (App.get().aiFiltersTree.getModel() instanceof AIFiltersTreeModel)
            ((AIFiltersTreeModel) App.get().aiFiltersTree.getModel()).updateCategories();
        else {
            AIFiltersTreeModel model = new AIFiltersTreeModel();
            App.get().aiFiltersTree.setModel(model);
        }
    }

    private AIFiltersTreeModel() {
        this.root = App.get().appCase.getCategoryTree();
    }

    private void updateCategories() {
        Category newRoot = App.get().appCase.getCategoryTree();
        updateChildren(this.root, newRoot);
    }

    private void fireNodeChanged(final Category category) {
        if (category == root)
            return;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Category cat = category;
                int idx = cat.getParent().getIndexOfChild(cat);
                int[] idxs = { idx };
                LinkedList<Category> path = new LinkedList<Category>();
                while (cat.getParent() != null)
                    path.addFirst(cat = cat.getParent());
                Category[] cats = { cat };
                TreeModelEvent e = new TreeModelEvent(this, path.toArray(), idxs, cats);
                for (TreeModelListener l : listeners)
                    l.treeNodesChanged(e);
            }
        });
    }

    private void updateChildren(Category oldRoot, Category newRoot) {
        int idx = 0;
        oldRoot.setNumItems(newRoot.getNumItems());
        fireNodeChanged(oldRoot);
        for (Category cat : newRoot.getChildren()) {
            if (!oldRoot.getChildren().contains(cat)) {
                cat.setParent(oldRoot);
                oldRoot.getChildren().add(cat);
                notifyNewNode(cat, idx);
            } else {
                Category oldCat = getFromCollection(oldRoot.getChildren(), cat);
                updateChildren(oldCat, cat);
            }
            idx++;
        }
    }

    private Category getFromCollection(Collection<Category> set, Category cat) {
        Iterator<Category> it = set.iterator();
        while (it.hasNext()) {
            Category next = it.next();
            if (next.equals(cat))
                return next;
        }
        return null;
    }

    private void notifyNewNode(Category cat, int idx) {
        int[] idxs = { idx };
        Category[] cats = { cat };
        LinkedList<Category> path = new LinkedList<Category>();
        while (cat.getParent() != null)
            path.addFirst(cat = cat.getParent());
        TreeModelEvent e = new TreeModelEvent(this, path.toArray(), idxs, cats);
        for (TreeModelListener l : listeners)
            l.treeNodesInserted(e);
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((Category) parent).getChildren().toArray()[index];
    }

    @Override
    public int getChildCount(Object parent) {
        return ((Category) parent).getChildren().size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((Category) node).getChildren().size() == 0;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null || child == null)
            return -1;
        return ((Category) parent).getIndexOfChild((Category) child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);

    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);

    }

}
