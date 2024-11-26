package iped.app.ui.filterdecisiontree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import iped.app.ui.CombinedFilterTreeModel;

public class DecisionNode {
    DecisionNode parent;
    List<DecisionNode> children = new ArrayList<DecisionNode>();
    boolean inverted = false;
    protected CombinedFilterTreeModel model;

    public DecisionNode(CombinedFilterTreeModel model) {
        this.model = model;
    }

    public DecisionNode getParent() {
        return parent;
    }

    public List<DecisionNode> getChildren() {
        return children;
    }

    public void remove(DecisionNode value) {
        if (value instanceof FilterNode) {
            FilterNode filterNode = (FilterNode) value;
            Set<DecisionNode> nodes = model.getFiltersToNodeMap().get(filterNode.getFilter());
            if (nodes != null) {
                nodes.remove(filterNode);
                if (nodes.size() == 0) {
                    model.getFiltersToNodeMap().remove(filterNode.getFilter());
                }
            }
        }
        children.remove(value);
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
    }

    public void invert() {
        this.inverted = !inverted;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

}
