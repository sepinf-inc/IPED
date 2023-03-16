package iped.app.ui.filterdecisiontree;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

import iped.app.ui.App;
import iped.app.ui.Messages;
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
    
    static final String INVERT_FILTER_STR = Messages.get("OperandMenu.invertFilter");
    static final String CHANGE_STR = Messages.get("OperandMenu.changeOperand");
    static final String ADD_AND_OPERAND = Messages.get("OperandMenu.addAnd");
    static final String ADD_OR_OPERAND = Messages.get("OperandMenu.addOr");
    static final String REMOVE_NODE_STR = Messages.get("OperandMenu.removeNode");

    private JMenuItem removeMenuitem;

    public OperandPopupMenu(JTree filtersTree, CombinedFilterer logicFilterer){
        this.logicFilterer = logicFilterer;
        this.filtersTree = filtersTree;

        inverMenuitem = new JMenuItem(INVERT_FILTER_STR);
        inverMenuitem.addActionListener(this);
        this.add(inverMenuitem);

        changeOperandMenuitem = new JMenuItem("");
        changeOperandMenuitem.addActionListener(this);
        this.add(changeOperandMenuitem);

        orMenuitem = new JMenuItem(ADD_OR_OPERAND);
        orMenuitem.addActionListener(this);
        this.add(orMenuitem);

        andMenuitem = new JMenuItem(ADD_AND_OPERAND);
        andMenuitem.addActionListener(this);
        this.add(andMenuitem);

        removeMenuitem = new JMenuItem(REMOVE_NODE_STR);
        removeMenuitem.addActionListener(this);
        this.add(removeMenuitem);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if(e.getSource()==orMenuitem) {
            ((OperandNode)op).addOperand(Operand.OR);
            filtersTree.updateUI();
            return;
        }
        if(e.getSource()==andMenuitem) {
            ((OperandNode)op).addOperand(Operand.AND);
            filtersTree.updateUI();
            return;
        }
        
        if(e.getSource()==removeMenuitem) {
            ((DecisionNode)op).getParent().remove(op);
            if(op instanceof FilterNode) {
                logicFilterer.removePreCachedFilter((IFilter)((FilterNode)op).getFilter());
            }
        }
        if(e.getSource()==inverMenuitem) {
            ((DecisionNode) op).invert();
            if(op instanceof FilterNode) {
                logicFilterer.invertPreCached(((FilterNode) op).getFilter());
            }
            logicFilterer.invalidateCache();
        }
        if(e.getSource()==changeOperandMenuitem) {
            if(((OperandNode)op).operand==Operand.OR) {
                ((OperandNode)op).operand=Operand.AND;
            }else {
                ((OperandNode)op).operand=Operand.OR;
            }
        }

        filtersTree.updateUI();
        logicFilterer.startSearchResult(App.get().getResults());

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
            inverMenuitem.setVisible(true);
            orMenuitem.setVisible(true);
            andMenuitem.setVisible(true);
            changeOperandMenuitem.setVisible(true);
            if((op instanceof OperandNode)&&(((OperandNode)op).operand==Operand.OR)) {
                changeOperandMenuitem.setText(CHANGE_STR+" "+OperandNode.ANDSTR);
            }else {
                changeOperandMenuitem.setText(CHANGE_STR+" "+OperandNode.ORSTR);
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