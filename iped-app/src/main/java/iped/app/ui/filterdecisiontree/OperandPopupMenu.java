package iped.app.ui.filterdecisiontree;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

import iped.app.ui.App;
import iped.app.ui.filterdecisiontree.OperandNode.Operand;
import iped.viewers.api.IFilter;

public class OperandPopupMenu extends JPopupMenu implements ActionListener{
    private JMenuItem orMenuitem;
    private JMenuItem andMenuitem;
    private JMenuItem changeOperandMenuitem;
    private JMenuItem inverMenuitem;
    CombinedFilterer logicFilterer;
    JTree filtersTree;
    DecisionNode op;

    private JMenuItem removeMenuitem;

    public OperandPopupMenu(JTree filtersTree, CombinedFilterer logicFilterer){
        this.logicFilterer = logicFilterer;
        this.filtersTree = filtersTree;

        inverMenuitem = new JMenuItem("Invert filter");
        inverMenuitem.addActionListener(this);
        this.add(inverMenuitem);

        changeOperandMenuitem = new JMenuItem("Change");
        changeOperandMenuitem.addActionListener(this);
        this.add(changeOperandMenuitem);

        orMenuitem = new JMenuItem("Add OR node");
        orMenuitem.addActionListener(this);
        this.add(orMenuitem);

        andMenuitem = new JMenuItem("Add AND node");
        andMenuitem.addActionListener(this);
        this.add(andMenuitem);

        removeMenuitem = new JMenuItem("Remove node");
        removeMenuitem.addActionListener(this);
        this.add(removeMenuitem);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource()==orMenuitem) {
            ((OperandNode)op).addOperand(Operand.OR);
        }
        if(e.getSource()==andMenuitem) {
            ((OperandNode)op).addOperand(Operand.AND);
        }        
        if(e.getSource()==removeMenuitem) {
            ((DecisionNode)op).getParent().remove(op);
        }
        if(e.getSource()==inverMenuitem) {
            ((FilterNode) op).invert();
        }
        if(e.getSource()==changeOperandMenuitem) {
            if(((OperandNode)op).operand==Operand.OR) {
                ((OperandNode)op).operand=Operand.AND;
            }else {
                ((OperandNode)op).operand=Operand.OR;
            }
        }

        filtersTree.updateUI();

        if(App.get().getFilterManager().isFiltererEnabled(logicFilterer)) {
            App.get().getAppListener().updateFileListing();
        }
    }
    
    public void setDecisionNode(DecisionNode op) {
        if(op instanceof FilterNode) {
            orMenuitem.setVisible(false);
            andMenuitem.setVisible(false);
            changeOperandMenuitem.setVisible(false);
            inverMenuitem.setVisible(true);
        }else {
            inverMenuitem.setVisible(false);
            orMenuitem.setVisible(true);
            andMenuitem.setVisible(true);
            changeOperandMenuitem.setVisible(true);
            if((op instanceof OperandNode)&&(((OperandNode)op).operand==Operand.OR)) {
                changeOperandMenuitem.setText("Change to AND");
            }else {
                changeOperandMenuitem.setText("Change to OR");
            }
        }
        this.op = op;
    }

    public void disableRemove() {
        removeMenuitem.setVisible(false);
    }

    public void enableRemove() {
        removeMenuitem.setVisible(true);
    }
}