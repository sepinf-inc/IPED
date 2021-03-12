package dpf.sp.gpinf.indexer.desktop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;

public class CategoryTreeModel implements TreeModel {

    public static String rootName = Messages.getString("CategoryTreeModel.RootName"); //$NON-NLS-1$
    private static String CONF_FILE = "conf/CategoryHierarchy.txt"; //$NON-NLS-1$

    public Category root;

    private List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

    private Collator collator;

    public static void install() {
        if (App.get().categoryTree.getModel() instanceof CategoryTreeModel)
            ((CategoryTreeModel) App.get().categoryTree.getModel()).updateCategories();
        else {
            CategoryTreeModel model = new CategoryTreeModel();
            App.get().categoryTree.setModel(model);
            model.root.updateItemCount();
        }
    }

    private CategoryTreeModel() {
        try {
            collator = Collator.getInstance();
            collator.setStrength(Collator.PRIMARY);
            this.root = loadHierarchy();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateCategories() {
        try {
            Category newRoot = loadHierarchy();
            updateChildren(this.root, newRoot);
            this.root.updateItemCount();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class Category implements Comparable<Category> {

        String name;
        Category parent;
        TreeSet<Category> children = new TreeSet<Category>();
        volatile Integer numItems;

        private Category(String name, Category parent) {
            this.name = name;
            this.parent = parent;
        }

        public String toString() {
            if (this.equals(root))
                return name;
            if (numItems == null) {
                return name + " (...)"; //$NON-NLS-1$
            } else
                return name + " (" + numItems + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }

        private synchronized Integer countNumItems() {
            if (numItems != null)
                return numItems;

            if (children.size() > 0) {
                int num = 0;
                for (Category child : children)
                    num += child.countNumItems();
                numItems = num;

            } else {
                String query = IndexItem.CATEGORY + ":\"" + name + "\""; //$NON-NLS-1$ //$NON-NLS-2$
                IPEDSearcher searcher = new IPEDSearcher(App.get().appCase, query);
                searcher.setNoScoring(true);
                try {
                    numItems = searcher.multiSearch().getLength();

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            fireNodeChanged(this);
            return numItems;
        }

        private void clearItemCount() {
            numItems = null;
            fireNodeChanged(this);
        }

        private void updateItemCount() {
            new Thread() {
                public void run() {
                    countNumItems();
                }
            }.start();
        }

        private int getIndexOfChild(Category child) {
            int idx = 0;
            for (Category c : children) {
                if (c.equals(child))
                    return idx;
                idx++;
            }
            return -1;
        }

        @Override
        public int compareTo(Category o) {
            return collator.compare(name, o.name);
        }

        @Override
        public boolean equals(Object o) {
            return compareTo((Category) o) == 0;
        }

    }

    private void fireNodeChanged(final Category category) {
        if (category == root)
            return;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Category cat = category;
                int idx = cat.parent.getIndexOfChild(cat);
                int[] idxs = { idx };
                LinkedList<Category> path = new LinkedList<Category>();
                while (cat.parent != null)
                    path.addFirst(cat = cat.parent);
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

        Category root = new Category(rootName, null);

        ArrayList<Category> categoryList = getLeafCategories(root);

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(new File(App.get().appCase.getAtomicSourceBySourceId(0).getModuleDir(), CONF_FILE)),
                "UTF-8")); //$NON-NLS-1$

        String line = reader.readLine();
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) { //$NON-NLS-1$
                continue;
            }
            String[] keyValuePair = line.split("="); //$NON-NLS-1$
            if (keyValuePair.length == 2) {
                Category category = new Category(keyValuePair[0].trim(), root);
                category = tryAddAndGet(categoryList, category);
                String subcats = keyValuePair[1].trim();
                for (String subcat : subcats.split(";")) { //$NON-NLS-1$
                    Category sub = new Category(subcat.trim(), category);
                    Category cat = tryAddAndGet(categoryList, sub);
                    cat.parent = category;
                }
            }
        }
        reader.close();

        populateChildren(root, categoryList);

        filterEmptyCategories(root, getLeafCategories(root));

        return root;
    }

    private void updateChildren(Category oldRoot, Category newRoot) {
        int idx = 0;
        oldRoot.clearItemCount();
        for (Category cat : newRoot.children) {
            if (!oldRoot.children.contains(cat)) {
                cat.parent = oldRoot;
                oldRoot.children.add(cat);
                notifyNewNode(cat, idx);
            } else {
                Category oldCat = getFromSet(oldRoot.children, cat);
                updateChildren(oldCat, cat);
            }
            idx++;
        }
    }

    private Category getFromSet(Set<Category> set, Category cat) {
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
        while (cat.parent != null)
            path.addFirst(cat = cat.parent);
        TreeModelEvent e = new TreeModelEvent(this, path.toArray(), idxs, cats);
        for (TreeModelListener l : listeners)
            l.treeNodesInserted(e);
    }

    private Category tryAddAndGet(ArrayList<Category> categoryList, Category category) {
        if (!categoryList.contains(category)) {
            categoryList.add(category);
            return category;
        } else
            return categoryList.get(categoryList.indexOf(category));
    }

    private ArrayList<Category> getLeafCategories(Category root) {
        ArrayList<Category> categoryList = new ArrayList<Category>();
        for (String category : App.get().appCase.getCategories()) {
            category = upperCaseChars(category);
            categoryList.add(new Category(category, root));
        }
        return categoryList;
    }

    private void populateChildren(Category category, ArrayList<Category> categoryList) {
        for (Category cat : categoryList) {
            if (cat.parent.equals(category)) {
                category.children.add(cat);
                populateChildren(cat, categoryList);
            }
        }
    }

    private boolean filterEmptyCategories(Category category, ArrayList<Category> leafCategories) {
        boolean hasItems = false;
        if (leafCategories.contains(category)) {
            hasItems = true;
        }
        for (Category child : (TreeSet<Category>) category.children.clone()) {
            if (filterEmptyCategories(child, leafCategories)) {
                hasItems = true;
            }
        }
        if (!hasItems && category.parent != null) {
            category.parent.children.remove(category);
        }
        return hasItems;
    }

    @Override
    public Object getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((Category) parent).children.toArray()[index];
    }

    @Override
    public int getChildCount(Object parent) {
        return ((Category) parent).children.size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((Category) node).children.size() == 0;
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
