package iped.app.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.engine.data.Category;

public class CategoryMimeTreeModel implements TreeModel{

    public static String rootName = Messages.getString("CategoryTreeModel.RootName"); //$NON-NLS-1$
    
    public Category root;
    private boolean toShowMimetypes=false; 

    private List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    public CategoryMimeTreeModel(Category root) {
        this.root = root;
        this.root.setName(rootName);
    }

    public CategoryMimeTreeModel(Category root, boolean toShowMimetypes) {
        this.root = root;
        this.root.setName(rootName);
        this.toShowMimetypes = toShowMimetypes;
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if(parent instanceof String) {
            return null;
        }
        if(toShowMimetypes) {
            int subCatsListSize = ((Category) parent).getChildren().size();
            if(index >= subCatsListSize) {
                index -= subCatsListSize;
                return ((Category) parent).getMimes().get(index);
            }else {
                return ((Category) parent).getChildren().toArray()[index];
            }
        }else {
            return ((Category) parent).getChildren().toArray()[index];
        }
    }

    @Override
    public int getChildCount(Object parent) {
        if(parent instanceof String) {
            return 0;
        }
        int size = ((Category) parent).getChildren().size();
        if(toShowMimetypes) {
            size+=((Category) parent).getMimes().size();
        }
        return size;
    }

    @Override
    public boolean isLeaf(Object node) {
        if(node instanceof String) {
            return true;
        }
        return getChildCount(node) == 0;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        ((Category)path.getLastPathComponent()).setName(newValue.toString());
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

    public void updateModel() {
        
        
    }

}
