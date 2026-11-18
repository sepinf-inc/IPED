package iped.app.ui.ai;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.engine.data.SimpleFilterNode;

public class AIFiltersTreeModel implements TreeModel {
    private SimpleFilterNode root;
    private final List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    AIFiltersTreeModel(SimpleFilterNode root) {
        this.root = root;
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
        if (parent == null || child == null) {
            return -1;
        }
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
