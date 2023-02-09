package iped.app.home.configurables;

import java.awt.Component;

import javax.swing.JCheckBox;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeSelectionModel;

import com.google.common.base.Predicate;

public class CheckBoxTreeCellRenderer implements TreeCellRenderer{
    JCheckBox checkbox=new JCheckBox();;
    private JTree tree;
    Predicate<Object> predicate;

    public CheckBoxTreeCellRenderer(JTree tree, Predicate<Object> predicate) {
        this.tree = tree;
        this.predicate = predicate;
        
        TreeSelectionModel selModel = tree.getSelectionModel();
        
        selModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        
        selModel.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                if(e.isAddedPath()) {
                    checkbox.setSelected(!checkbox.isSelected());
                    tree.getSelectionModel().clearSelection();
                }
            }
        });
        
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        checkbox.setText(value.toString());
        checkbox.setSelected(predicate.apply(value));
        
        return checkbox;
    }

}
