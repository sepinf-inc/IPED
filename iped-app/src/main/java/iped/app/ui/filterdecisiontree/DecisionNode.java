package iped.app.ui.filterdecisiontree;

import java.util.ArrayList;
import java.util.List;

public class DecisionNode {
    DecisionNode parent;
    List<DecisionNode> children = new ArrayList<DecisionNode>();
    boolean inverted = false;

    public DecisionNode getParent() {
        return parent;
    }

    public List<DecisionNode> getChildren() {
        return children;
    }

    public void remove(DecisionNode value) {
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

}
