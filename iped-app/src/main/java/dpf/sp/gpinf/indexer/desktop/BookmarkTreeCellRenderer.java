package dpf.sp.gpinf.indexer.desktop;

import java.awt.Component;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

public class BookmarkTreeCellRenderer extends DefaultTreeCellRenderer {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {

        if (App.get().appCase != null) {
            String name = (String) value;
            
            String comment = App.get().appCase.getMultiMarcadores().getLabelComment(name);
            if (comment != null && !comment.trim().isEmpty())
                setToolTipText(comment.trim());
            else
                setToolTipText(null);

            if (name != null && !name.equals(BookmarksTreeModel.ROOT) && !name.equals(BookmarksTreeModel.NO_BOOKMARKS)) {
                int count = App.get().appCase.getMultiMarcadores().getLabelCount(name);
                if (count > 0) {
                    value = (String) value + " (" + count + ")";
                }
            }
        }
        return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
    }
}