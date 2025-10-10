package iped.app.ui.ai;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import iped.app.ui.IconManager;
import iped.engine.data.SimpleFilterNode;
import iped.utils.LocalizedFormat;

public class AIFiltersTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final long serialVersionUID = 1L;
    private Color colorEnabled, colorSelEnabled, colorDisabled;

    public AIFiltersTreeCellRenderer() {
        super();
        updateUI();
    }

    public void updateUI() {
        super.updateUI();
        Color c = UIManager.getColor("Label.foreground");
        if (c != null) {
            colorEnabled = new Color(c.getRGB());
        }
        c = UIManager.getColor("nimbusSelectedText");
        if (c != null) {
            colorSelEnabled = new Color(c.getRGB());
        }
        c = UIManager.getColor("Label.disabledText");
        if (c != null) {
            colorDisabled = new Color(c.getRGB());
        }
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof SimpleFilterNode) {
            SimpleFilterNode node = (SimpleFilterNode) value;
            int numItems = node.getNumItems();
            setIcon(IconManager.getFilterIcon(node, numItems != 0));
            String text = AIFiltersLocalization.get(node);
            if (numItems >= 0) {
                text += " (" + LocalizedFormat.format(numItems) + ")";
            }
            setForeground(numItems == 0 ? colorDisabled : sel ? colorSelEnabled : colorEnabled);
            setText(text);
        }
        return this;
    }
}
