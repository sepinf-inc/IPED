package iped.app.ui.filterdecisiontree;

import java.util.ArrayList;
import java.util.List;

public class DecisionNode {
    DecisionNode parent;
    List<DecisionNode> children = new ArrayList<DecisionNode>();
    
    public DecisionNode getParent() {
        return parent;
    }
    
    public List<DecisionNode> getChildren() {
        return children;
    }

    public void remove(DecisionNode value) {
        children.remove(value);
    }

}
