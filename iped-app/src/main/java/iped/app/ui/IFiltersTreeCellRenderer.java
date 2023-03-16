package iped.app.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.util.function.Predicate;

import javax.swing.JLabel;
import javax.swing.JTree;

import iped.app.ui.controls.CheckBoxTreeCellRenderer;
import iped.viewers.api.IFilterer;

public class IFiltersTreeCellRenderer extends CheckBoxTreeCellRenderer {

    public static final Color ENABLED_BK_COLOR = new Color(255,150,150);
    private Image resizedImage;

    public IFiltersTreeCellRenderer(JTree tree, Predicate<Object> checkedPredicate,
            Predicate<Object> visiblePredicate) {
        super(tree, checkedPredicate, visiblePredicate);
    }

    public IFiltersTreeCellRenderer(JTree tree, Predicate<Object> checkedPredicate) {
        super(tree, checkedPredicate);
    }
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        Component result= super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if(checkbox.isSelected()) {
            result.setBackground(ENABLED_BK_COLOR);
        }
        if(value instanceof IFilterer) {
            label.setText(((IFilterer)value).getName());
        }
        return result;
    }

}
