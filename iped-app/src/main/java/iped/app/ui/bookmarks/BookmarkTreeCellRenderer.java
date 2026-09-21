package iped.app.ui.bookmarks;

import java.awt.Color;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import iped.app.ui.App;
import iped.app.ui.BookmarksMismatchChecker;
import iped.app.ui.BookmarksTreeModel;
import iped.app.ui.IconManager;
import iped.app.ui.Messages;
import iped.app.ui.utils.AlertIcon;
import iped.data.IMultiBookmarks;
import iped.utils.LocalizedFormat;

public class BookmarkTreeCellRenderer extends DefaultTreeCellRenderer {
    private static final long serialVersionUID = 1L;

    private static final Icon rootIcon = IconManager.getTreeIcon("bookmarks-root");

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf,
            int row, boolean hasFocus) {

        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value.equals(BookmarksTreeModel.ROOT)) {
            setIcon(rootIcon);
        }
        if (App.get().appCase != null) {
            String name = value.toString();

            IMultiBookmarks bookmarks = App.get().appCase.getMultiBookmarks();
            String comment = bookmarks.getBookmarkComment(name);
            if (comment != null && !comment.trim().isEmpty()) {
                setToolTipText(comment.trim());
            } else {
                setToolTipText(null);
            }
            if (value.equals(BookmarksTreeModel.NO_BOOKMARKS)) {
                setIcon(BookmarkIcon.getIcon(BookmarkStandardColors.noBookmarksColor));
            } else if (!value.equals(BookmarksTreeModel.ROOT)) {
                Color color = bookmarks.getBookmarkColor(name);
                Icon baseIcon = BookmarkIcon.getIcon(color == null ? BookmarkStandardColors.defaultColor : color);
                if (!BookmarksMismatchChecker.get().queryMismatch(name)) {
                    setIcon(baseIcon);
                } else {
                    setIcon(new AlertIcon(baseIcon));
                    setToolTipText(Messages.getString("BookmarksManager.MismatchTooltip"));
                }                

                int count = bookmarks.getBookmarkCount(name);
                if (count > 0) {
                    setText(value + " (" + LocalizedFormat.format(count) + ")");
                }
            }
        }
        return this;
    }
}
