package gpinf.dev.data;

import java.io.Serializable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonAlias;

import dpf.sp.gpinf.indexer.localization.CategoryLocalization;
import dpf.sp.gpinf.indexer.util.LocalizedFormat;

public class Category implements Serializable, Comparable<Category> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static Collator collator;

    static {
        collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
    }

    @JsonAlias("name")
    private String name;

    @JsonAlias("mimes")
    private List<String> mimes = new ArrayList<>();

    @JsonAlias("categories")
    private SortedSet<Category> children = new TreeSet<>();

    private Category parent;

    private int numItems = -1;

    public Category() {
    }

    public Category(String name, Category parent) {
        this.name = name;
        this.parent = parent;
    }

    public String getName() {
        return name;
    }

    public List<String> getMimes() {
        return mimes;
    }

    public SortedSet<Category> getChildren() {
        return children;
    }

    public Category getParent() {
        return parent;
    }

    public int getNumItems() {
        return this.numItems;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNumItems(int num) {
        this.numItems = num;
    }

    public void setParent(Category parent) {
        this.parent = parent;
    }

    public int getIndexOfChild(Category child) {
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
        return collator.compare(CategoryLocalization.getInstance().getLocalizedCategory(name),
                CategoryLocalization.getInstance().getLocalizedCategory(o.name));
    }

    @Override
    public boolean equals(Object o) {
        return compareTo((Category) o) == 0;
    }

    public String toString() {
        if (this.parent == null)
            return name;
        String name = CategoryLocalization.getInstance().getLocalizedCategory(this.name);
        name = adjustCase(name);
        if (numItems == -1) {
            return name + " (...)"; //$NON-NLS-1$
        } else {
            return name + " (" + LocalizedFormat.format(numItems) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    @Override
    public Category clone() {
        Category clone = new Category(this.name, null);
        clone.numItems = this.numItems;
        clone.mimes = new ArrayList<String>(this.getMimes());
        for (Category child : this.getChildren()) {
            Category newChild = child.clone();
            newChild.parent = clone;
            clone.children.add(newChild);
        }
        return clone;
    }

    private String adjustCase(String cat) {
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

}
