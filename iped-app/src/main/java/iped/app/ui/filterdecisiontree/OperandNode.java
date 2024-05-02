package iped.app.ui.filterdecisiontree;

import iped.app.ui.Messages;

public class OperandNode extends DecisionNode {

    public enum Operand {
        AND, OR
    };

    Operand operand;

    static String ANDSTR = Messages.get("Operand.AND");
    static String ORSTR = Messages.get("Operand.OR");

    public OperandNode(Operand op) {
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
        OperandNode newOp = new OperandNode(op);
        newOp.parent = this;
        children.add(newOp);
    }

    public void addFilter(FilterNode filterNode) {
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
