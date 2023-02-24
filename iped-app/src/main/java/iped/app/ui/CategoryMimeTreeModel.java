package iped.app.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.engine.data.Category;

public class CategoryMimeTreeModel implements TreeModel{
    private static final String ROOT = "ROOT";
    public static String rootName = Messages.getString("CategoryTreeModel.RootName"); //$NON-NLS-1$
    public static String uncategorizableName = Messages.getString("CategoryTreeModel.Uncategorizables"); //$NON-NLS-1$
    public boolean toShowUncategorizable=false;

    public Category rootCategory;
    private boolean toShowMimetypes=false;

    private List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    public CategoryMimeTreeModel(Category root) {
        this.rootCategory = root;
        this.rootCategory.setName(rootName);
    }

    public CategoryMimeTreeModel(Category root, boolean toShowMimetypes) {
        this.rootCategory = root;
        this.rootCategory.setName(rootName);
        this.toShowMimetypes = toShowMimetypes;
    }
    
    public void hideCategories(Predicate<Category> isToHide) {
        hideCategories(rootCategory, isToHide);        
    }

    private void hideCategories(Category cat, Predicate<Category> isToHide) {
        if(isToHide.test(cat)) {
            if(cat.getParent()!=null) {
                cat.getParent().getChildren().remove(cat);
            }
        }else {
            Category[] cats = cat.getChildren().toArray(new Category[0]);
            for (int i=0;i<cats.length;i++) {
                hideCategories(cats[i], isToHide);                
            }
        }
    }

    @Override
    public Object getRoot() {
        if(toShowUncategorizable) {
            return ROOT;
        }else {
            return rootCategory;
        }
    }

    @Override
    public Object getChild(Object parent, int index) {
        if(parent==ROOT) {
            if(index==0) {
                return uncategorizableName;
            }else {
                return rootCategory;
            }
        }
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
        if(parent==ROOT) {
            return 2;//uncategorizables and categories
        }
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
        if(node==ROOT) {
            return false;
        }
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

    public boolean isToShowUncategorizable() {
        return toShowUncategorizable;
    }

    public void setToShowUncategorizable(boolean showUncategorizable) {
        this.toShowUncategorizable = showUncategorizable;
    }

}
