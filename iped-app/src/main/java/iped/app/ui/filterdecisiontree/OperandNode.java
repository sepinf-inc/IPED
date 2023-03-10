package iped.app.ui.filterdecisiontree;

import java.util.ArrayList;
import java.util.List;

public class OperandNode extends DecisionNode {

    public enum Operand {AND,OR};
    Operand operand;
    
    public OperandNode(Operand op) {
        this.operand = op;
    }
    
    @Override
    public String toString() {
        if(operand==Operand.AND) {
            return "AND";
        }
        return "OR";
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
