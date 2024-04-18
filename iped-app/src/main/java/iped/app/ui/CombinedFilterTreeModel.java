package iped.app.ui;

import java.util.HashMap;
import java.util.Set;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.app.ui.filterdecisiontree.CombinedFilterer;
import iped.app.ui.filterdecisiontree.DecisionNode;
import iped.app.ui.filterdecisiontree.OperandNode;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.viewers.api.IFilter;
import iped.viewers.api.IFilterer;

public class CombinedFilterTreeModel implements TreeModel {

    String rootName = "Filters";
    private IFilterer[] filterers;
    HashMap<IFilter, Set<DecisionNode>> filtersToNodeMap = new HashMap<IFilter, Set<DecisionNode>>();

    public CombinedFilterTreeModel(CombinedFilterer logicFilterer) {
        logicFilterer.setRootNode(new OperandNode(Operand.OR, this));
        this.rootName = logicFilterer.getFilterName();
        this.filterers = new IFilterer[1];
        this.filterers[0] = logicFilterer;
    }

    @Override
    public Object getRoot() {
        return rootName;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (rootName.equals(parent)) {
            return filterers[index];
        }

        if (parent instanceof CombinedFilterer) {
            CombinedFilterer lf = (CombinedFilterer) parent;
            return lf.getRootNode().getChildren().get(index);
        }

        if (parent instanceof OperandNode) {
            return ((OperandNode) parent).getChildren().get(index);
        }

        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (rootName.equals(parent)) {
            return filterers.length;
        }

        if (parent instanceof CombinedFilterer) {
            CombinedFilterer lf = (CombinedFilterer) parent;
            return lf.getRootNode().getChildren().size();
        }

        if (parent instanceof OperandNode) {
            return ((OperandNode) parent).getChildren().size();
        }

        return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        if (rootName.equals(node)) {
            return false;
        }

        if (node instanceof CombinedFilterer) {
            return false;
        }

        if (node instanceof OperandNode) {
            return false;
        }

        return true;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        // TODO Auto-generated method stub

    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        // TODO Auto-generated method stub

    }

    public String getRootName() {
        return rootName;
    }

    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    public boolean hasFilter(IFilter iFilter) {
        return filtersToNodeMap.get(iFilter).size() > 0;
    }

    public HashMap<IFilter, Set<DecisionNode>> getFiltersToNodeMap() {
        return filtersToNodeMap;
    }

}