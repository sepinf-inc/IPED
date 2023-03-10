package iped.app.ui;

import java.util.List;
import java.util.Set;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.viewers.api.IFilterer;

public class FiltersTreeModel implements TreeModel {
    
    String rootName = "Filters";
    private IFilterer[] filterers;

    public FiltersTreeModel(Set<IFilterer> filterers){
        this.filterers=filterers.toArray(new IFilterer[0]);
    }

    @Override
    public Object getRoot() {
        return rootName;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if(rootName.equals(parent)) {
            return filterers[index];
        }

        if(parent instanceof IFilterer) {
            List filters = ((IFilterer) parent).getDefinedFilters();
            return filters.get(index);
        }

        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if(rootName.equals(parent)) {
            return filterers.length;
        }

        if(parent instanceof IFilterer) {
            List filters = ((IFilterer) parent).getDefinedFilters();
            if(filters==null) {
                return 0;
            }
            return ((IFilterer) parent).getDefinedFilters().size();
        }

        return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        if(rootName.equals(node)) {
            return false;
        }

        if(node instanceof IFilterer) {
            return ((IFilterer) node).getDefinedFilters().size()==0;
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
    
}