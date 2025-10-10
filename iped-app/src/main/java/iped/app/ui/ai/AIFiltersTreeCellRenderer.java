package iped.app.ui.ai;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import iped.app.ui.IconManager;
import iped.engine.data.SimpleFilterNode;
import iped.utils.LocalizedFormat;

public class AIFiltersTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final long serialVersionUID = 1L;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof SimpleFilterNode) {
            SimpleFilterNode node = (SimpleFilterNode) value;
            int numItems = node.getNumItems();
            setIcon(IconManager.getFilterIcon(node));
            String text = AIFiltersLocalization.get(node);
            if (numItems >= 0) {
                text += " (" + LocalizedFormat.format(numItems) + ")";
            }
            setText(text);
        }
        return this;
    }
}
