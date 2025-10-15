package iped.engine.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class SimpleFilterNode implements Serializable, Cloneable {
    private static final long serialVersionUID = 197209091220L;

    @JsonAlias("name")
    private String name;

    @JsonAlias("prefix")
    private String prefix;

    @JsonAlias("property")
    private String property;

    @JsonAlias("value")
    private String value;

    @JsonAlias("dynamic")
    private boolean dynamic;

    @JsonAlias("addChildren")
    private boolean addChildren;

    @JsonAlias("children")
    private final List<SimpleFilterNode> children = new ArrayList<>();

    @JsonIgnore
    private SimpleFilterNode parent;

    @JsonIgnore
    private boolean dynamicChild;

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
        String s = property;
        if (s == null && parent != null) {
            s = parent.getProperty();
        }
        return s;
    }

    public String getValue() {
        return value == null ? name : value;
    }

    public boolean isDynamicChild() {
        return dynamicChild;
    }

    public boolean getDynamic() {
        return dynamic;
    }

    public boolean getAddChildren() {
        return addChildren;
    }

    public List<SimpleFilterNode> getChildren() {
        return children;
    }

    public SimpleFilterNode getParent() {
        return parent;
    }

    public int getNumItems() {
        return numItems;
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

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public void setAddChildren(boolean addChildren) {
        this.addChildren = addChildren;
    }

    public void setDynamicChild(boolean dynamicChild) {
        this.dynamicChild = dynamicChild;
    }

    public void setNumItems(int numItems) {
        this.numItems = numItems;
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
    public Object clone() {
        SimpleFilterNode clonedNode = new SimpleFilterNode();
        clonedNode.name = name;
        clonedNode.prefix = prefix;
        clonedNode.property = property;
        clonedNode.value = value;
        clonedNode.dynamic = dynamic;
        clonedNode.addChildren = addChildren;
        clonedNode.dynamicChild = dynamicChild;
        for (int i = 0; i < children.size(); i++) {
            SimpleFilterNode child = children.get(i);
            SimpleFilterNode clonedChild = (SimpleFilterNode) child.clone();
            clonedChild.parent = clonedNode;
            clonedNode.children.add(clonedChild);
        }
        return clonedNode;
    }
}
