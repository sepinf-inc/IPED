/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;

import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.lucene.DocValuesUtil;
import dpf.sp.gpinf.indexer.util.LocalizedFormat;
import iped3.IItemId;
import iped3.desktop.ProgressDialog;
import iped3.util.BasicProps;

public class BookmarksManager implements ActionListener, ListSelectionListener, KeyListener {

    private static BookmarksManager instance = new BookmarksManager();

    JDialog dialog = new JDialog(App.get());
    JLabel msg = new JLabel(Messages.getString("BookmarksManager.Dataset")); //$NON-NLS-1$
    JRadioButton highlighted = new JRadioButton();
    JRadioButton checked = new JRadioButton();
    ButtonGroup group = new ButtonGroup();
    JCheckBox duplicates = new JCheckBox();
    JButton add = new JButton(Messages.getString("BookmarksManager.Add")); //$NON-NLS-1$
    JButton remove = new JButton(Messages.getString("BookmarksManager.Remove")); //$NON-NLS-1$
    JButton rename = new JButton(Messages.getString("BookmarksManager.Rename")); //$NON-NLS-1$
    JTextField newBookmark = new JTextField();
    JTextArea comments = new JTextArea();
    JButton newButton = new JButton(Messages.getString("BookmarksManager.New")); //$NON-NLS-1$
    JButton updateComment = new JButton(Messages.getString("BookmarksManager.Update")); //$NON-NLS-1$
    JButton delete = new JButton(Messages.getString("BookmarksManager.Delete")); //$NON-NLS-1$
    DefaultListModel<BookmarkAndKey> listModel = new DefaultListModel<>();
    JList<BookmarkAndKey> list = new JList<>(listModel);
    JScrollPane scrollList = new JScrollPane(list);

    private HashMap<KeyStroke, String> keystrokeToBookmark = new HashMap<>();

    private final Collator collator;
    
    private class BookmarkAndKey implements Comparable<BookmarkAndKey> {
        String bookmark;
        KeyStroke key;

        public BookmarkAndKey(String bookmark) {
            this.bookmark = bookmark;
        }

        public boolean equals(Object obj) {
            if (obj instanceof BookmarkAndKey) {
                return ((BookmarkAndKey) obj).bookmark.equalsIgnoreCase(bookmark);
            } else if (obj instanceof String) {
                return ((String) obj).equalsIgnoreCase(bookmark);
            }
            return false;
        }

        public String toString() {
            return bookmark + (key != null ? " (" + key.toString().replace("released ", "") + ")" : "");
        }

        @Override
        public int compareTo(BookmarkAndKey other) {
            return collator.compare(bookmark, other.bookmark);
        }
    }

    public static BookmarksManager get() {
        return instance;
    }

    public static boolean isVisible() {
        return instance.dialog.isVisible();
    }

    public static void setVisible() {
        instance.dialog.setVisible(true);
        instance.list.clearSelection();
        instance.newBookmark.setText("");
        instance.comments.setText("");
    }

    public static void updateCounters() {
        instance.highlighted.setText(Messages.getString("BookmarksManager.Highlighted") + " (" //$NON-NLS-1$ //$NON-NLS-2$
                + LocalizedFormat.format(App.get().resultsTable.getSelectedRowCount()) + ")"); //$NON-NLS-1$
        instance.checked.setText(Messages.getString("BookmarksManager.Checked") + " (" //$NON-NLS-1$ //$NON-NLS-2$
                + LocalizedFormat.format(App.get().appCase.getMultiBookmarks().getTotalChecked()) + ")"); //$NON-NLS-1$
    }

    private BookmarksManager() {

        collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        
        dialog.setTitle(Messages.getString("BookmarksManager.Title")); //$NON-NLS-1$
        dialog.setBounds(0, 0, 500, 500);

        group.add(highlighted);
        group.add(checked);
        highlighted.setSelected(true);
        duplicates.setText(Messages.getString("BookmarksManager.AddDuplicates")); //$NON-NLS-1$
        duplicates.setSelected(false);

        updateList();

        newBookmark.setToolTipText(Messages.getString("BookmarksManager.NewBookmark.Tip")); //$NON-NLS-1$
        comments.setToolTipText(Messages.getString("BookmarksManager.CommentsTooltip")); //$NON-NLS-1$
        updateComment.setToolTipText(Messages.getString("BookmarksManager.UpdateTooltip")); //$NON-NLS-1$
        newButton.setToolTipText(Messages.getString("BookmarksManager.New.Tip")); //$NON-NLS-1$
        add.setToolTipText(Messages.getString("BookmarksManager.Add.Tip")); //$NON-NLS-1$
        remove.setToolTipText(Messages.getString("BookmarksManager.Remove.Tip")); //$NON-NLS-1$
        delete.setToolTipText(Messages.getString("BookmarksManager.Delete.Tip")); //$NON-NLS-1$

        JPanel top = new JPanel(new GridLayout(3, 2, 0, 5));
        top.add(msg);
        top.add(new JLabel());
        top.add(highlighted);
        top.add(checked);
        top.add(duplicates);

        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        remove.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel left1 = new JPanel();
        left1.setLayout(new BoxLayout(left1, BoxLayout.PAGE_AXIS));
        JPanel left0 = new JPanel(new GridLayout(0, 1, 0, 0));
        left0.add(newButton);
        left0.add(updateComment);
        left1.add(left0);
        left1.add(Box.createVerticalStrut(65));
        JPanel left3 = new JPanel(new GridLayout(0, 1, 0, 0));
        left3.add(add);
        left3.add(remove);
        left1.add(left3);

        JPanel left2 = new JPanel(new GridLayout(0, 1, 0, 0));
        left2.add(rename);
        left2.add(delete);

        JPanel left = new JPanel(new BorderLayout());
        left.add(left1, BorderLayout.PAGE_START);
        left.add(left2, BorderLayout.PAGE_END);

        comments.setLineWrap(true);
        comments.setWrapStyleWord(true);
        JScrollPane commentScroll = new JScrollPane(comments);
        commentScroll.setPreferredSize(new Dimension(300, 50));

        JLabel shortcutBookmark = new JLabel(Messages.getString("BookmarksManager.KeyStrokeBookmark"));
        shortcutBookmark.setBorder(BorderFactory.createEmptyBorder(6, 1, 0, 0));

        JPanel center = new JPanel(new BorderLayout());
        JPanel bookmark = new JPanel(new BorderLayout());
        bookmark.add(newBookmark, BorderLayout.PAGE_START);
        bookmark.add(commentScroll, BorderLayout.CENTER);
        bookmark.add(shortcutBookmark, BorderLayout.PAGE_END);
        center.add(bookmark, BorderLayout.PAGE_START);
        center.add(scrollList, BorderLayout.CENTER);

        JPanel pane = new JPanel(new BorderLayout(10, 10));
        pane.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        pane.add(top, BorderLayout.PAGE_START);
        pane.add(left, BorderLayout.LINE_START);
        pane.add(center, BorderLayout.CENTER);
        dialog.getContentPane().add(pane);

        add.addActionListener(this);
        updateComment.addActionListener(this);
        remove.addActionListener(this);
        rename.addActionListener(this);
        newButton.addActionListener(this);
        delete.addActionListener(this);

        list.addListSelectionListener(this);
        // disable selection by typing
        for (KeyListener kl : list.getKeyListeners()) {
            list.removeKeyListener(kl);
        }
        list.addKeyListener(this);

        dialog.setLocationRelativeTo(App.get());

    }

    public void updateList() {
        updateList(null, null);
    }

    private void updateList(String oldBookmark, String newBookmark) {
        TreeSet<BookmarkAndKey> bookmarks = new TreeSet<>();
        KeyStroke prevStroke = null;
        if (!listModel.isEmpty()) {
            for (int i = 0; i < listModel.size(); i++) {
                BookmarkAndKey bk = listModel.get(i);
                if (bk.bookmark.equalsIgnoreCase(oldBookmark)) {
                    prevStroke = bk.key;
                } else {
                    bookmarks.add(bk);
                }
            }
        }
        Set<String> bookmarkSet = App.get().appCase.getMultiBookmarks().getBookmarkSet();
        for (String bookmark : bookmarkSet) {
            BookmarkAndKey bk = new BookmarkAndKey(bookmark);
            if (!bookmarks.contains(bk)) {
                bookmarks.add(bk);
            }
            bk.key = App.get().appCase.getMultiBookmarks().getBookmarkKeyStroke(bookmark);
        }
        Iterator<BookmarkAndKey> iterator = bookmarks.iterator();
        while (iterator.hasNext()) {
            BookmarkAndKey bk = iterator.next();
            if (prevStroke != null && bk.bookmark.equalsIgnoreCase(newBookmark)) {
                bk.key = prevStroke;
            }
            if (!bookmarkSet.contains(bk.bookmark)) {
                iterator.remove();
            }
        }
        keystrokeToBookmark.clear();
        for (BookmarkAndKey bk : bookmarks) {
            if (bk.key != null) {
                keystrokeToBookmark.put(bk.key, bk.bookmark);
                keystrokeToBookmark.put(getRemoveKey(bk.key), bk.bookmark);
            }
        }
        listModel.clear();
        for (BookmarkAndKey b : bookmarks) {
            listModel.addElement(b);
        }
    }

    private int getEmptyDataHashOrd(SortedDocValues sdv) throws IOException {
        byte[] emptyData = new byte[0];
        String emptyMD5 = DigestUtils.md5Hex(emptyData).toUpperCase();
        int ord = sdv.lookupTerm(new BytesRef(emptyMD5));
        if (ord < 0) {
            String emptySHA1 = DigestUtils.sha1Hex(emptyData).toUpperCase();
            ord = sdv.lookupTerm(new BytesRef(emptySHA1));
        }
        if (ord < 0) {
            String emptySHA256 = DigestUtils.sha256Hex(emptyData).toUpperCase();
            ord = sdv.lookupTerm(new BytesRef(emptySHA256));
        }
        return ord;
    }

    private void includeDuplicates(ArrayList<IItemId> uniqueSelectedIds) {

        ProgressDialog progress = new ProgressDialog(App.get(), null);
        progress.setNote(Messages.getString("BookmarksManager.SearchingDuplicates")); //$NON-NLS-1$
        try {
            LeafReader reader = App.get().appCase.getLeafReader();
            SortedDocValues sdv = reader.getSortedDocValues(BasicProps.HASH);

            progress.setMaximum(uniqueSelectedIds.size() + reader.maxDoc());
            int i = 0;

            int emptyDataHashOrd = getEmptyDataHashOrd(sdv);
            int emptyValueOrd = sdv.lookupTerm(new BytesRef("")); //$NON-NLS-1$
            if (emptyValueOrd < 0) {
                emptyValueOrd = -1;
            }

            IPEDMultiSource ipedCase = App.get().appCase;
            BitSet hashOrd = new BitSet(sdv.getValueCount());
            BitSet luceneIds = new BitSet(reader.maxDoc());
            for (IItemId item : uniqueSelectedIds) {
                int luceneId = ipedCase.getLuceneId(item);
                int ord = DocValuesUtil.getOrd(sdv, luceneId);
                if (ord > emptyValueOrd && ord != emptyDataHashOrd) {
                    hashOrd.set(ord);
                }
                luceneIds.set(luceneId);
                progress.setProgress(++i);
                if (progress.isCanceled())
                    return;
            }
            int duplicates = 0;
            // must reset docValues to call getOrd again
            sdv = reader.getSortedDocValues(BasicProps.HASH);
            for (int doc = 0; doc < reader.maxDoc(); doc++) {
                int ord = DocValuesUtil.getOrd(sdv, doc);
                if (ord != -1 && hashOrd.get(ord) && !luceneIds.get(doc)) {
                    IItemId itemId = ipedCase.getItemId(doc);
                    uniqueSelectedIds.add(itemId);
                    duplicates++;
                }
                progress.setProgress(++i);
                if (progress.isCanceled())
                    return;
            }
            System.out.println(
                    Messages.getString("BookmarksManager.DuplicatesAdded") + " " + duplicates + " (luceneIds)"); //$NON-NLS-1$ //$NON-NLS-2$

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            progress.close();
        }

    }

    @Override
    public void actionPerformed(final ActionEvent evt) {

        if (evt.getSource() == newButton) {
            String texto = newBookmark.getText().trim();
            String comment = comments.getText().trim();
            if (!texto.isEmpty() && !listModel.contains(new BookmarkAndKey(texto))) {
                App.get().appCase.getMultiBookmarks().newBookmark(texto);
                App.get().appCase.getMultiBookmarks().setBookmarkComment(texto, comment);
                updateList();
            }
            list.clearSelection();
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).bookmark.equalsIgnoreCase(texto)) {
                    list.setSelectedIndex(i);
                }
            }

        }
        if (evt.getSource() == updateComment) {
            int idx = list.getSelectedIndex();
            if (idx != -1) {
                String bookmarkName = list.getModel().getElementAt(idx).bookmark;
                App.get().appCase.getMultiBookmarks().setBookmarkComment(bookmarkName, comments.getText());
                App.get().appCase.getMultiBookmarks().saveState();
            }
        }

        if (evt.getSource() == add || evt.getSource() == remove || evt.getSource() == newButton) {

            ArrayList<IItemId> uniqueSelectedIds = getUniqueSelectedIds();

            ArrayList<String> bookmarks = new ArrayList<String>();
            for (int index : list.getSelectedIndices())
                bookmarks.add(list.getModel().getElementAt(index).bookmark);

            boolean insert = evt.getSource() == add || evt.getSource() == newButton;
            bookmark(uniqueSelectedIds, bookmarks, insert);

        } else if (evt.getSource() == delete) {
            int result = JOptionPane.showConfirmDialog(dialog, Messages.getString("BookmarksManager.ConfirmDelete"), //$NON-NLS-1$
                    Messages.getString("BookmarksManager.ConfirmDelTitle"), JOptionPane.YES_NO_OPTION); //$NON-NLS-1$
            if (result == JOptionPane.YES_OPTION) {
                for (int index : list.getSelectedIndices()) {
                    String bookmark = list.getModel().getElementAt(index).bookmark;
                    App.get().appCase.getMultiBookmarks().delBookmark(bookmark);
                }
                updateList();
                App.get().appCase.getMultiBookmarks().saveState();
                BookmarksController.get().updateUI();

            }

        } else if (evt.getSource() == rename) {
            String newBookmark = JOptionPane.showInputDialog(dialog, Messages.getString("BookmarksManager.NewName"), //$NON-NLS-1$
                    list.getSelectedValue().bookmark);
            if (newBookmark != null && !newBookmark.trim().isEmpty()) {
                newBookmark = newBookmark.trim();
                int selIdx = list.getSelectedIndex();
                if (selIdx != -1) {
                    String bookmark = list.getModel().getElementAt(selIdx).bookmark;
                    if (!bookmark.equalsIgnoreCase(newBookmark)
                            && listModel.contains(new BookmarkAndKey(newBookmark))) {
                        JOptionPane.showMessageDialog(dialog, Messages.getString("BookmarksManager.AlreadyExists"));
                        return;
                    }
                    App.get().appCase.getMultiBookmarks().renameBookmark(bookmark, newBookmark);
                    updateList(bookmark, newBookmark);
                    App.get().appCase.getMultiBookmarks().saveState();
                    BookmarksController.get().updateUI();
                }
            }
        }

    }

    private ArrayList<IItemId> getUniqueSelectedIds() {
        ArrayList<IItemId> uniqueSelectedIds = new ArrayList<IItemId>();

        if (checked.isSelected()) {
            for (IPEDSource source : App.get().appCase.getAtomicSources()) {
                for (int id = 0; id <= source.getLastId(); id++) {
                    if (source.getBookmarks().isChecked(id)) {
                        uniqueSelectedIds.add(new ItemId(source.getSourceId(), id));
                    }
                }
            }

        } else if (highlighted.isSelected()) {
            App app = App.get();
            for (Integer row : App.get().resultsTable.getSelectedRows()) {
                int rowModel = App.get().resultsTable.convertRowIndexToModel(row);
                IItemId id = app.ipedResult.getItem(rowModel);
                uniqueSelectedIds.add(id);
            }
        }

        return uniqueSelectedIds;
    }

    private void bookmark(ArrayList<IItemId> uniqueSelectedIds, List<String> bookmarks, boolean insert) {
        new Thread() {
            public void run() {
                if (duplicates.isSelected())
                    includeDuplicates(uniqueSelectedIds);

                for (String bookmark : bookmarks) {
                    if (insert) {
                        App.get().appCase.getMultiBookmarks().addBookmark(uniqueSelectedIds, bookmark);
                    } else {
                        App.get().appCase.getMultiBookmarks().removeBookmark(uniqueSelectedIds, bookmark);
                    }
                }
                App.get().appCase.getMultiBookmarks().saveState();
                BookmarksController.get().updateUI();
            }
        }.start();
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        int idx = list.getSelectedIndex();
        if (idx == -1) {
            comments.setText(null);
            newBookmark.setText(null);
            return;
        }
        String bookmarkName = list.getModel().getElementAt(idx).bookmark;
        String comment = App.get().appCase.getMultiBookmarks().getBookmarkComment(bookmarkName);
        newBookmark.setText(bookmarkName);
        comments.setText(comment);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.isConsumed())
            return;
        
        if (e.getKeyCode() == KeyEvent.VK_SHIFT || e.getKeyCode() == KeyEvent.VK_CONTROL
                || e.getKeyCode() == KeyEvent.VK_ALT) {
            return;
        }

        //Avoid conflict with CTRL+A (select all), CTRL+B (Open bookmarks manager window)
        //and CTRL+C (copy selected table cell content).
        if (e.isControlDown() && (e.getKeyCode() == 'B' || e.getKeyCode() == 'C')) {
            showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert4"));
            e.consume();
            return;
        }
        
        //Avoid conflict with keys used for selection/navigation in the bookmark list,
        //items table or items gallery.
        if ((e.isControlDown() && e.getKeyCode() == 'A')
                || e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT
                || e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN
                || e.getKeyCode() == KeyEvent.VK_HOME || e.getKeyCode() == KeyEvent.VK_END
                || e.getKeyCode() == KeyEvent.VK_PAGE_UP || e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
            return;
        }

        //Avoid conflict with keys that are used for item selection (space) and
        //recursive item selection (R).
        if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == 'R') {
            if (e.getSource() == list) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert4"));
                e.consume();
            }
            return;
        }
        
        KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers(), true);

        if (e.getSource() == list) {
            if (list.getSelectedIndices().length != 1) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert1"));
                e.consume();
                return;
            }
            if ((e.getModifiers() & KeyEvent.ALT_MASK) != 0) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert2"));
                e.consume();
                return;
            }
            if (keystrokeToBookmark.containsKey(stroke)) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert3"));
                e.consume();
                return;
            }
            int index = list.getSelectedIndex();
            list.getModel().getElementAt(index).key = stroke;
            list.repaint();

            String bookmarkStr = list.getModel().getElementAt(index).bookmark;
            Iterator<KeyStroke> iterator = keystrokeToBookmark.keySet().iterator();
            while (iterator.hasNext()) {
                String bookmark = keystrokeToBookmark.get(iterator.next());
                if (bookmark.equalsIgnoreCase(bookmarkStr)) {
                    iterator.remove();
                }
            }

            keystrokeToBookmark.put(stroke, bookmarkStr);
            keystrokeToBookmark.put(getRemoveKey(stroke), bookmarkStr);

            App.get().appCase.getMultiBookmarks().setBookmarkKeyStroke(bookmarkStr, stroke);
            App.get().appCase.getMultiBookmarks().saveState();
            e.consume();
            
        } else {
            String bookmark = keystrokeToBookmark.get(stroke);
            if (bookmark == null) {
                return;
            }
            ArrayList<IItemId> uniqueSelectedIds = getUniqueSelectedIds();
            bookmark(uniqueSelectedIds, Collections.singletonList(bookmark),
                    (e.getModifiers() & KeyEvent.ALT_MASK) == 0);
            e.consume();
        }

    }

    // alt key remove from bookmark
    private KeyStroke getRemoveKey(KeyStroke k) {
        return KeyStroke.getKeyStroke(k.getKeyCode(), KeyEvent.ALT_MASK, true);
    }

    private void showMessage(String msg) {
        JOptionPane.showMessageDialog(dialog, msg, dialog.getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }
    
    public boolean hasSingleKeyShortcut() {
       for (KeyStroke k : keystrokeToBookmark.keySet()) {
           if ((k.getModifiers() & (InputEvent.CTRL_MASK | InputEvent.ALT_MASK | InputEvent.SHIFT_MASK)) == 0) {
               int c = k.getKeyCode();
               if ((c >= KeyEvent.VK_0 && c <= KeyEvent.VK_9) || (c >= KeyEvent.VK_A && c <= KeyEvent.VK_Z)) {
                   return true;
               }
           }
       }
       return false; 
    }
}