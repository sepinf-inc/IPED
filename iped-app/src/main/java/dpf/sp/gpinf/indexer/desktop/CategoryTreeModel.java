package dpf.sp.gpinf.indexer.desktop;

import java.io.IOException;
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

import dpf.sp.gpinf.indexer.config.CategoryConfig;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import gpinf.dev.data.Category;

public class CategoryTreeModel implements TreeModel {

    public static String rootName = Messages.getString("CategoryTreeModel.RootName"); //$NON-NLS-1$

    public Category root;

    private List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    public static void install() {
        if (App.get().categoryTree.getModel() instanceof CategoryTreeModel)
            ((CategoryTreeModel) App.get().categoryTree.getModel()).updateCategories();
        else {
            CategoryTreeModel model = new CategoryTreeModel();
            App.get().categoryTree.setModel(model);
            model.updateItemCount(model.root);
        }
    }

    private CategoryTreeModel() {
        try {
            this.root = loadHierarchy();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateCategories() {
        try {
            Category newRoot = loadHierarchy();
            updateChildren(this.root, newRoot);
            updateItemCount(this.root);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized int countNumItems(Category category, IPEDSource ipedCase) {
        if (category.getNumItems() != -1)
            return category.getNumItems();

        if (!category.getChildren().isEmpty()) {
            int num = 0;
            for (Category child : category.getChildren()) {
                num += countNumItems(child, ipedCase);
            }
            category.setNumItems(num);

        } else {
            String query = IndexItem.CATEGORY + ":\"" + category.getName() + "\"";
            IPEDSearcher searcher = new IPEDSearcher(ipedCase, query);
            searcher.setNoScoring(true);
            try {
                category.setNumItems(searcher.multiSearch().getLength());

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        fireNodeChanged(category);
        return category.getNumItems();
    }

    private void updateItemCount(Category category) {
        new Thread() {
            public void run() {
                countNumItems(category, App.get().appCase);
            }
        }.start();
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

    private String upperCaseChars(String cat) {
        StringBuilder str = new StringBuilder();
        for (String s : cat.split(" ")) //$NON-NLS-1$
            if (s.length() == 3)
                str.append(s.toUpperCase() + " "); //$NON-NLS-1$
            else if (s.length() > 3)
                str.append(s.substring(0, 1).toUpperCase() + s.substring(1) + " "); //$NON-NLS-1$
            else
                str.append(s + " "); //$NON-NLS-1$
        return str.toString().trim();
    }

    private Category loadHierarchy() throws IOException {
        CategoryConfig config = ConfigurationManager.get().findObject(CategoryConfig.class);
        Category root = config.getConfiguration().clone();
        root.setName(rootName);
        filterEmptyCategories(root, getLeafCategories(root));
        return root;
    }

    private void updateChildren(Category oldRoot, Category newRoot) {
        int idx = 0;
        oldRoot.clearItemCount();
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

    private ArrayList<Category> getLeafCategories(Category root) {
        ArrayList<Category> categoryList = new ArrayList<Category>();
        for (String category : App.get().appCase.getCategories()) {
            category = upperCaseChars(category);
            categoryList.add(new Category(category, root));
        }
        return categoryList;
    }

    private boolean filterEmptyCategories(Category category, ArrayList<Category> leafCategories) {
        boolean hasItems = false;
        if (leafCategories.contains(category)) {
            hasItems = true;
        }
        for (Category child : category.getChildren().toArray(new Category[0])) {
            if (filterEmptyCategories(child, leafCategories)) {
                hasItems = true;
            }
        }
        if (!hasItems && category.getParent() != null) {
            category.getParent().getChildren().remove(category);
        }
        return hasItems;
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
        // TODO Auto-generated method stub

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
