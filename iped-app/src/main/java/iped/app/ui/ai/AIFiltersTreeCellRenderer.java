package iped.app.ui.ai;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import iped.app.ui.IconManager;
import iped.engine.data.SimpleFilterNode;

public class AIFiltersTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        Icon icon = IconManager.getFilterIcon(value instanceof SimpleFilterNode ? (SimpleFilterNode) value : null);
        setIcon(icon);
        return this;
    }
}
