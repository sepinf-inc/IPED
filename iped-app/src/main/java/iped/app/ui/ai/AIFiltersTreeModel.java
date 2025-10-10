package iped.app.ui.ai;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.app.ui.App;
import iped.engine.data.SimpleFilterNode;

public class AIFiltersTreeModel implements TreeModel {

    private SimpleFilterNode root;

    private final List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    public static void install() {
        if (App.get().aiFiltersTree.getModel() instanceof AIFiltersTreeModel) {
            ((AIFiltersTreeModel) App.get().aiFiltersTree.getModel()).updateAIFilters();
        } else {
            AIFiltersTreeModel model = new AIFiltersTreeModel();
            App.get().aiFiltersTree.setModel(model);
        }
    }

    private AIFiltersTreeModel() {
        this.root = App.get().appCase.getAIFilterRoot();
    }

    private void updateAIFilters() {
        SimpleFilterNode newRoot = App.get().appCase.getAIFilterRoot();
        updateChildren(this.root, newRoot);
    }

    private void fireNodeChanged(final SimpleFilterNode node) {
        /*
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
        */
    }

    private void updateChildren(SimpleFilterNode oldRoot, SimpleFilterNode newRoot) {
        /*
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
        */
    }

    private SimpleFilterNode getFromCollection(Collection<SimpleFilterNode> set, SimpleFilterNode node) {
        /*
        Iterator<Category> it = set.iterator();
        while (it.hasNext()) {
            Category next = it.next();
            if (next.equals(cat))
                return next;
        }
        */
        return null;
    }

    private void notifyNewNode(SimpleFilterNode node, int idx) {
        /*
        int[] idxs = { idx };
        Category[] cats = { cat };
        LinkedList<Category> path = new LinkedList<Category>();
        while (cat.getParent() != null)
            path.addFirst(cat = cat.getParent());
        TreeModelEvent e = new TreeModelEvent(this, path.toArray(), idxs, cats);
        for (TreeModelListener l : listeners)
            l.treeNodesInserted(e);
        */
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((SimpleFilterNode) parent).getChildren().get(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return ((SimpleFilterNode) parent).getChildren().size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((SimpleFilterNode) node).getChildren().size() == 0;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null || child == null)
            return -1;
        return ((SimpleFilterNode) parent).getIndexOfChild((SimpleFilterNode) child);
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
