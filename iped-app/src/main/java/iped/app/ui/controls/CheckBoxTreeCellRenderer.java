package iped.app.ui.controls;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventObject;
import java.util.function.Predicate;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.event.CellEditorListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellEditor;

import org.apache.tika.mime.MediaType;

import iped.app.ui.App;
import iped.app.ui.IconManager;
import iped.engine.data.Category;
import iped.viewers.api.IFilterer;
import iped.viewers.api.IMiniaturizable;

public class CheckBoxTreeCellRenderer extends DefaultTreeCellRenderer implements TreeCellEditor {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final Color ENABLED_BK_COLOR = new Color(255, 150, 150);

    Predicate<Object> checkedPredicate;
    Predicate<Object> visiblePredicate;

    public CheckBoxTreeCellRenderer(JTree tree, Predicate<Object> checkedPredicate) {
        this.checkedPredicate = checkedPredicate;
    }

    public CheckBoxTreeCellRenderer(JTree tree, Predicate<Object> checkedPredicate, Predicate<Object> visiblePredicate) {
        this(tree, checkedPredicate);
        this.visiblePredicate = visiblePredicate;
    }
    
    public String getValueString(Object value) {
        if (value instanceof IFilterer) {
            IFilterer filterer = ((IFilterer) value);
            return filterer.getFilterName();
        }
        return value.toString();        
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {

        JLabel label = new JLabel();
        if (row == -1) {
            label.setText("");
            return label;
        }

        Icon icon = null;
        if(value instanceof MediaType) {
            icon = IconManager.getFileIcon(value.toString().split("/")[0], "");
        }
        if(value instanceof Category) {
            icon = IconManager.getCategoryIcon(((Category)value).getName().toLowerCase());
        }
        if (value instanceof IMiniaturizable) {
            icon = new ImageIcon(((IMiniaturizable) value).getThumb().getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH));
        }

        label.setText(getValueString(value));
        label.setIcon(icon);

        if (visiblePredicate == null || visiblePredicate.test(value)) {
            JCheckBox checkbox = new JCheckBox();
            checkbox.setSelected(checkedPredicate.test(value));
            checkbox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ((IFilterer) value).clearFilter();
                    App.get().filtersPanel.updateUI();
                    App.get().getAppListener().updateFileListing();
                }
            });
            JPanel ckPanel = new JPanel(new BorderLayout());
            ckPanel.setBackground(checkbox.isSelected() ? ENABLED_BK_COLOR : Color.white);
            ckPanel.add(checkbox, BorderLayout.WEST);
            ckPanel.add(label, BorderLayout.CENTER);
            return ckPanel;
        } else {
            return label;
        }
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
        return true;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        return false;
    }

    @Override
    public boolean stopCellEditing() {
        return true;
    }

    @Override
    public void cancelCellEditing() {
        // TODO Auto-generated method stub
    }

    @Override
    public void addCellEditorListener(CellEditorListener l) {
        // TODO Auto-generated method stub
    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {
        // TODO Auto-generated method stub

    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
        return getTreeCellRendererComponent(tree, value, isSelected, expanded, leaf, row, true);
    }
}
