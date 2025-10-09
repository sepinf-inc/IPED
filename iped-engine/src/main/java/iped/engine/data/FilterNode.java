package iped.engine.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class FilterNode implements Serializable {
    private static final long serialVersionUID = 197209091220L;

    @JsonAlias("name")
    private String name;

    @JsonAlias("prefix")
    private String prefix;

    @JsonAlias("property")
    private String property;

    @JsonAlias("value")
    private String value;

    @JsonAlias("children")
    private List<FilterNode> children = new ArrayList<>();

    @JsonIgnore
    private FilterNode parent;

    @JsonIgnore
    private int numItems = -1;

    public FilterNode() {
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getProperty() {
        return property;
    }

    public String getValue() {
        return value;
    }

    public List<FilterNode> getChildren() {
        return children;
    }

    public FilterNode getParent() {
        return parent;
    }

    public int getNumItems() {
        return this.numItems;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setNumItems(int num) {
        this.numItems = num;
    }

    public void setParent(FilterNode parent) {
        this.parent = parent;
    }

    public int getIndexOfChild(FilterNode child) {
        int idx = 0;
        for (FilterNode n : children) {
            if (n.equals(child)) {
                return idx;
            }
            idx++;
        }
        return -1;
    }

    public String toString() {
        // TODO
        /*
        if (this.parent == null)
            return name;
        String name = CategoryLocalization.getInstance().getLocalizedCategory(this.name);
        if (numItems == -1) {
            return name + " (...)"; //$NON-NLS-1$
        } else {
            return name + " (" + LocalizedFormat.format(numItems) + ")"; //$NON-NLS-1$ //$NON-NLS-2$
        }
        */
        return name;
    }

    @Override
    public FilterNode clone() {
        FilterNode clone = new FilterNode();
        clone.name = this.name;
        clone.prefix = this.prefix;
        clone.property = this.property;
        clone.value = this.value;
        clone.numItems = this.numItems;
        for (FilterNode child : this.getChildren()) {
            FilterNode newChild = child.clone();
            newChild.parent = clone;
            clone.children.add(newChild);
        }
        return clone;
    }
}
