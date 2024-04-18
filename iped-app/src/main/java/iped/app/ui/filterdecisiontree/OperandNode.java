package iped.app.ui.filterdecisiontree;

import java.util.HashSet;
import java.util.Set;

import iped.app.ui.CombinedFilterTreeModel;
import iped.app.ui.Messages;

public class OperandNode extends DecisionNode {

    public enum Operand {
        AND, OR
    };

    Operand operand;

    static String ANDSTR = Messages.get("Operand.AND");
    static String ORSTR = Messages.get("Operand.OR");

    public OperandNode(Operand op, CombinedFilterTreeModel model) {
        super(model);
        this.operand = op;
    }

    @Override
    public String toString() {
        if (operand == Operand.AND) {
            return ANDSTR;
        }
        return ORSTR;
    }

    public void addOperand(Operand op) {
        OperandNode newOp = new OperandNode(op, model);
        newOp.parent = this;
        children.add(newOp);
    }

    public void addFilter(FilterNode filterNode) {
        Set<DecisionNode> nodes = model.getFiltersToNodeMap().get(filterNode.getFilter());
        if(nodes == null) {
            nodes = new HashSet<DecisionNode>();
            model.getFiltersToNodeMap().put(filterNode.getFilter(), nodes);
        }
        nodes.add(filterNode);
        filterNode.parent = this;
        children.add(filterNode);
    }

    public void addDecisionNode(DecisionNode dnode) {
        dnode.parent = this;
        children.add(dnode);
    }

    public Operand getOperand() {
        return operand;
    }
}
