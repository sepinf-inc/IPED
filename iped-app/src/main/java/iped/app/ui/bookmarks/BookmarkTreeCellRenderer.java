package iped.app.ui.bookmarks;

import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import iped.app.ui.App;
import iped.app.ui.BookmarksTreeModel;
import iped.app.ui.IconManager;
import iped.utils.LocalizedFormat;

public class BookmarkTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final long serialVersionUID = 1L;

    private static final Icon rootIcon = IconManager.getTreeIcon("bookmarks-root");
    private static final Icon noBookmarksIcon = IconManager.getTreeIcon("no-bookmarks");
    private static final Icon bookmarkIcon = IconManager.getTreeIcon("bookmark");

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value.equals(BookmarksTreeModel.ROOT)) {
            setIcon(rootIcon);
        }
        if (App.get().appCase != null) {
            String name = value.toString();

            String comment = App.get().appCase.getMultiBookmarks().getBookmarkComment(name);
            if (comment != null && !comment.trim().isEmpty()) {
                setToolTipText(comment.trim());
            } else {
                setToolTipText(null);
            }
            if (value.equals(BookmarksTreeModel.NO_BOOKMARKS)) {
                setIcon(noBookmarksIcon);
            } else if (!value.equals(BookmarksTreeModel.ROOT)) {
                setIcon(bookmarkIcon);
                int count = App.get().appCase.getMultiBookmarks().getBookmarkCount(name);
                if (count > 0) {
                    setText(value + " (" + LocalizedFormat.format(count) + ")");
                }
            }
        }
        return this;
    }
}
