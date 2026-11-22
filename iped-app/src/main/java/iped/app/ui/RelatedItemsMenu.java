package iped.app.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.TreeSet;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;

import iped.app.ui.bookmarks.BookmarkAndKey;
import iped.app.ui.bookmarks.BookmarkIcon;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.utils.LocalizedFormat;

/**
 * Context menu of the related items panels (subitems, parent, duplicates,
 * references and referenced by). All actions are applied to the items
 * highlighted in the panel where the menu was opened.
 */
public class RelatedItemsMenu extends JPopupMenu implements ActionListener {

    private static final long serialVersionUID = 1L;

    private final BaseTableModel model;
    private final JTable table;
    private final ArrayList<IItemId> highlightedIds;

    private final JMenuItem checkHighlighted, uncheckHighlighted, exportHighlighted, openExternally;

    public RelatedItemsMenu(BaseTableModel model, JTable table) {
        this.model = model;
        this.table = table;
        this.highlightedIds = model.getSelectedItemIds(table);

        checkHighlighted = createItem(highlightedLabel("MenuClass.CheckHighlighted"));
        uncheckHighlighted = createItem(highlightedLabel("MenuClass.UnCheckHighlighted"));
        addSeparator();
        add(createBookmarksSubmenu(true));
        add(createBookmarksSubmenu(false));
        addSeparator();
        exportHighlighted = createItem(highlightedLabel("MenuClass.ExportHighlighted"));
        addSeparator();
        openExternally = createItem(Messages.getString("RelatedItemsMenu.OpenExternally"));
        openExternally.setEnabled(table.getSelectedRowCount() == 1);
    }

    /**
     * Returns the label of an action applied to the highlighted items, followed by
     * the number of highlighted items, e.g. "Export Highlighted items (6)".
     */
    private String highlightedLabel(String messageKey) {
        return Messages.getString(messageKey) + " (" + LocalizedFormat.format(highlightedIds.size()) + ")";
    }

    private JMenuItem createItem(String label) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(this);
        add(item);
        return item;
    }

    /**
     * Creates the submenu listing the bookmarks the highlighted items can be added
     * to (all bookmarks) or removed from (only the bookmarks containing at least
     * one of the highlighted items).
     */
    private JMenu createBookmarksSubmenu(boolean add) {
        IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
        JMenu submenu = new JMenu(highlightedLabel(add ? "RelatedItemsMenu.AddToBookmark" : "RelatedItemsMenu.RemoveFromBookmark"));

        // same ordering used by the bookmark manager dialog
        TreeSet<BookmarkAndKey> bookmarks = new TreeSet<>();
        if (add) {
            for (String bookmark : multiBookmarks.getBookmarkSet()) {
                bookmarks.add(new BookmarkAndKey(bookmark));
            }
        } else {
            for (IItemId id : highlightedIds) {
                for (String bookmark : multiBookmarks.getBookmarkList(id)) {
                    bookmarks.add(new BookmarkAndKey(bookmark));
                }
            }
        }

        for (BookmarkAndKey bk : bookmarks) {
            String bookmark = bk.getName();
            JMenuItem item = new JMenuItem(bookmark, BookmarkIcon.getIcon(multiBookmarks, bookmark));
            KeyStroke key = multiBookmarks.getBookmarkKeyStroke(bookmark);
            if (key != null) {
                // just shows the bookmark shortcut (Alt + shortcut removes from bookmark)
                int modifiers = key.getModifiers() | (add ? 0 : InputEvent.ALT_DOWN_MASK);
                item.setAccelerator(KeyStroke.getKeyStroke(key.getKeyCode(), modifiers));
            }
            item.addActionListener(e -> BookmarksManager.get().bookmarkItems(highlightedIds, bookmark, add));
            submenu.add(item);
        }

        if (bookmarks.isEmpty()) {
            JMenuItem empty = new JMenuItem(Messages.getString(add ? "RelatedItemsMenu.NoBookmarks" : "RelatedItemsMenu.NoBookmarksInItems"));
            empty.setEnabled(false);
            submenu.add(empty);
        }
        return submenu;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == checkHighlighted || e.getSource() == uncheckHighlighted) {
            setChecked(e.getSource() == checkHighlighted);

        } else if (e.getSource() == exportHighlighted) {
            File dir = new MenuListener(null).chooseExportFolder();
            if (dir != null) {
                new CopyFiles(dir, highlightedIds).execute();
            }

        } else if (e.getSource() == openExternally) {
            int row = table.getSelectedRow();
            if (row != -1) {
                ExternalFileOpen.open(model.getLuceneId(table.convertRowIndexToModel(row)));
            }
        }
    }

    private void setChecked(boolean value) {
        IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
        for (IItemId id : highlightedIds) {
            multiBookmarks.setChecked(value, id);
        }
        multiBookmarks.saveState();
        BookmarksController.get().updateUI();
    }
}
