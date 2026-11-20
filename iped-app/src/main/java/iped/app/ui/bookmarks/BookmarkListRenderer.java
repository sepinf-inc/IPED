package iped.app.ui.bookmarks;

import java.awt.Color;
import java.awt.Component;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

import iped.app.ui.App;
import iped.app.ui.BookmarksMismatchChecker;
import iped.app.ui.utils.AlertIcon;

public class BookmarkListRenderer extends JLabel implements ListCellRenderer<BookmarkAndKey> {
    private static final long serialVersionUID = 19720909122009L;
    private Color background, foreground, selBackground, selForeground;

    @Override
    public void updateUI() {
        super.updateUI();

        Color c = UIManager.getColor("List.background");
        if (c != null)
            background = new Color(c.getRGB());

        c = UIManager.getColor("List.foreground");
        if (c != null)
            foreground = new Color(c.getRGB());

        c = UIManager.getColor("nimbusSelectionBackground");
        if (c != null)
            selBackground = new Color(c.getRGB());

        c = UIManager.getColor("nimbusSelectedText");
        if (c != null)
            selForeground = new Color(c.getRGB());
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends BookmarkAndKey> list, BookmarkAndKey value, int index,
            boolean isSelected, boolean cellHasFocus) {
        BookmarkAndKey bk = (BookmarkAndKey) value;
        setText(bk.toString());

        // Get the bookmark color
        Color color = App.get().appCase.getMultiBookmarks().getBookmarkColor(bk.getName());
        Icon baseIcon = BookmarkIcon.getIcon(color);

        // Check if there is a query mismatch and wrap with alert icon if that is the case
        if (BookmarksMismatchChecker.get().queryMismatch(bk.getName())) {
            setIcon(new AlertIcon(baseIcon));
        } else {
            setIcon(baseIcon);
        }

        setOpaque(true);

        if (isSelected) {
            setBackground(selBackground);
            setForeground(selForeground);
        } else {
            setBackground(background);
            setForeground(foreground);
        }

        return this;
    }
}
