package iped.engine.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class SimpleFilterNode implements Serializable {
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
    private List<SimpleFilterNode> children = new ArrayList<>();

    @JsonIgnore
    private SimpleFilterNode parent;

    @JsonIgnore
    private int numItems = -1;

    public String getName() {
        return name;
    }

    public String getFullName() {
        String s = getPrefix();
        return s == null ? name : s + "." + name;
    }

    public String getPrefix() {
        String s = prefix;
        if (s == null && parent != null) {
            s = parent.getPrefix();
        }
        return s;
    }

    public String getProperty() {
        return property;
    }

    public String getValue() {
        return value;
    }

    public List<SimpleFilterNode> getChildren() {
        return children;
    }

    public SimpleFilterNode getParent() {
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

    public void setParent(SimpleFilterNode parent) {
        this.parent = parent;
    }

    public int getIndexOfChild(SimpleFilterNode child) {
        int idx = 0;
        for (SimpleFilterNode n : children) {
            if (n.equals(child)) {
                return idx;
            }
            idx++;
        }
        return -1;
    }

    @Override
    public SimpleFilterNode clone() {
        SimpleFilterNode clone = new SimpleFilterNode();
        clone.name = this.name;
        clone.prefix = this.prefix;
        clone.property = this.property;
        clone.value = this.value;
        clone.numItems = this.numItems;
        for (SimpleFilterNode child : this.getChildren()) {
            SimpleFilterNode newChild = child.clone();
            newChild.parent = clone;
            clone.children.add(newChild);
        }
        return clone;
    }
}
