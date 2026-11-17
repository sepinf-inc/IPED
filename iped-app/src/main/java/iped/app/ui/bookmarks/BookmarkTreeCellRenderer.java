package iped.app.ui.bookmarks;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultTreeCellRenderer;

import iped.app.ui.App;
import iped.app.ui.BookmarkNode;
import iped.app.ui.BookmarksTreeModel;
import iped.app.ui.IconManager;
import iped.utils.LocalizedFormat;

/**
 * Renderer for hierarchical bookmarks.
 * All nodes (including intermediate ones) are displayed as regular bookmarks.
 */
public class BookmarkTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final long serialVersionUID = 1L;

    private static final Icon rootIcon = IconManager.getTreeIcon("bookmarks-root");

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        
        if (value.equals(BookmarksTreeModel.ROOT)) {
            setIcon(rootIcon);
            setToolTipText(null);
            return this;
        }
        
        boolean isFilterTree = ((BookmarksTreeModel) tree.getModel()).isFilterTree();
        if (value.equals(BookmarksTreeModel.NO_BOOKMARKS)) {
            if (App.get().appCase != null) {
                setIcon(BookmarkIcon.getIcon(BookmarkStandardColors.noBookmarksColor));
                setToolTipText(null);
            }
            return this;
        }
        
        if (App.get().appCase == null) {
            return this;
        }

        // Handle BookmarkNode - all nodes are regular bookmarks
        if (value instanceof BookmarkNode) {
            BookmarkNode node = (BookmarkNode) value;
            String displayName = node.getName();
            
            // All nodes get bookmark color and properties
            Color color = node.getColor();
            if (color == null) {
                color = BookmarkStandardColors.defaultColor;
            }
            setIcon(BookmarkIcon.getIcon(color));
            
            if (isFilterTree) {
                int nodeCount = node.getCount();
                if (nodeCount > 0) {
                    displayName = displayName + " (" + LocalizedFormat.format(nodeCount) + ")";
                }
            }
            else {
                KeyStroke keyStroke = node.getKeyStroke();
                if (keyStroke != null) {
                    String keyText = formatKeyStroke(keyStroke);
                    displayName = displayName + " [" + keyText + "]";
                }
            }
            setText(displayName);
                        
            String comment = node.getComment();
            if (comment != null && !comment.trim().isEmpty()) {
                setToolTipText(comment.trim());
            }
        }
        
        return this;
    }
    
    private String formatKeyStroke(KeyStroke keyStroke) {
        StringBuilder sb = new StringBuilder();
        
        int modifiers = keyStroke.getModifiers();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            sb.append("Ctrl+");
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            sb.append("Alt+");
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            sb.append("Shift+");
        }
        
        int keyCode = keyStroke.getKeyCode();
        sb.append(KeyEvent.getKeyText(keyCode));
        
        return sb.toString();
    }
}