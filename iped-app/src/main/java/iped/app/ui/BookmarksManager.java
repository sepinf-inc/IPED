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
package iped.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.ui.bookmarks.BookmarkAndKey;
import iped.app.ui.bookmarks.BookmarkColorsUtil;
import iped.app.ui.bookmarks.BookmarkEditDialog;
import iped.app.ui.bookmarks.BookmarkListRenderer;
import iped.app.ui.utils.JTextFieldLimited;
import iped.data.IItem;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.MultiSearchResult;
import iped.engine.task.index.IndexItem;
import iped.properties.BasicProps;
import iped.utils.LocalizedFormat;
import iped.viewers.util.ProgressDialog;

public class BookmarksManager implements ActionListener, ListSelectionListener, KeyListener {

    private static final Logger logger = LoggerFactory.getLogger(BookmarksManager.class);

    private static BookmarksManager instance = new BookmarksManager();

    public static final int maxBookmarkNameLength = 256;

    JDialog dialog = new JDialog(App.get());
    JLabel msg = new JLabel(Messages.getString("BookmarksManager.Dataset")); //$NON-NLS-1$
    JRadioButton highlighted = new JRadioButton();
    JRadioButton checked = new JRadioButton();
    ButtonGroup group = new ButtonGroup();
    JCheckBox duplicates = new JCheckBox();
    JButton butAdd = new JButton(Messages.getString("BookmarksManager.Add")); //$NON-NLS-1$
    JButton butRemove = new JButton(Messages.getString("BookmarksManager.Remove")); //$NON-NLS-1$
    JButton butEdit = new JButton(Messages.getString("BookmarksManager.Edit")); //$NON-NLS-1$
    JTextFieldLimited newBookmark = new JTextFieldLimited();
    JTextArea comments = new JTextArea();
    JButton butNew = new JButton(Messages.getString("BookmarksManager.New")); //$NON-NLS-1$
    JButton butUpdateComment = new JButton(Messages.getString("BookmarksManager.Update")); //$NON-NLS-1$
    JButton butDelete = new JButton(Messages.getString("BookmarksManager.Delete")); //$NON-NLS-1$
    DefaultListModel<BookmarkAndKey> listModel = new DefaultListModel<>();
    JList<BookmarkAndKey> list = new JList<>(listModel);
    JScrollPane scrollList = new JScrollPane(list);

    private HashMap<KeyStroke, String> keystrokeToBookmark = new HashMap<>();

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

        dialog.setTitle(Messages.getString("BookmarksManager.Title")); //$NON-NLS-1$
        dialog.setSize(480, 480);

        group.add(highlighted);
        group.add(checked);
        highlighted.setSelected(true);
        duplicates.setText(Messages.getString("BookmarksManager.AddDuplicates")); //$NON-NLS-1$
        duplicates.setSelected(false);

        updateList();

        newBookmark.setLimit(maxBookmarkNameLength);
        newBookmark.setToolTipText(Messages.getString("BookmarksManager.NewBookmark.Tip")); //$NON-NLS-1$
        comments.setToolTipText(Messages.getString("BookmarksManager.CommentsTooltip")); //$NON-NLS-1$
        butUpdateComment.setToolTipText(Messages.getString("BookmarksManager.UpdateTooltip")); //$NON-NLS-1$
        butNew.setToolTipText(Messages.getString("BookmarksManager.New.Tip")); //$NON-NLS-1$
        butAdd.setToolTipText(Messages.getString("BookmarksManager.Add.Tip")); //$NON-NLS-1$
        butRemove.setToolTipText(Messages.getString("BookmarksManager.Remove.Tip")); //$NON-NLS-1$
        butDelete.setToolTipText(Messages.getString("BookmarksManager.Delete.Tip")); //$NON-NLS-1$
        butEdit.setToolTipText(Messages.getString("BookmarksManager.Edit.Tip")); //$NON-NLS-1$

        JPanel top = new JPanel(new GridLayout(3, 2, 0, 5));
        top.add(msg);
        top.add(new JLabel());
        top.add(highlighted);
        top.add(checked);
        top.add(duplicates);

        butAdd.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        butRemove.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel left1 = new JPanel();
        left1.setLayout(new BoxLayout(left1, BoxLayout.PAGE_AXIS));
        JPanel left0 = new JPanel(new GridLayout(0, 1, 0, 0));
        left0.add(butNew);
        left0.add(butUpdateComment);
        left1.add(left0);
        left1.add(Box.createVerticalStrut(65));
        JPanel left3 = new JPanel(new GridLayout(0, 1, 0, 0));
        left3.add(butAdd);
        left3.add(butRemove);
        left1.add(left3);

        JPanel left2 = new JPanel(new GridLayout(0, 1, 0, 0));
        left2.add(butEdit);
        left2.add(butDelete);

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
        pane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        pane.add(top, BorderLayout.PAGE_START);
        pane.add(left, BorderLayout.LINE_START);
        pane.add(center, BorderLayout.CENTER);
        dialog.getContentPane().add(pane);

        butAdd.addActionListener(this);
        butUpdateComment.addActionListener(this);
        butRemove.addActionListener(this);
        butEdit.addActionListener(this);
        butNew.addActionListener(this);
        butDelete.addActionListener(this);

        list.addListSelectionListener(this);
        // disable selection by typing
        for (KeyListener kl : list.getKeyListeners()) {
            list.removeKeyListener(kl);
        }
        list.addKeyListener(this);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Double click in the list opens edit dialog
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    if (list.getSelectedIndices().length == 1) {
                        actionPerformed(new ActionEvent(butEdit, 0, ""));
                    }
                }
            }
        });
        list.setCellRenderer(new BookmarkListRenderer());

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
                if (bk.getName().equalsIgnoreCase(oldBookmark)) {
                    prevStroke = bk.getKey();
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
            bk.setKey(App.get().appCase.getMultiBookmarks().getBookmarkKeyStroke(bookmark));
        }
        Iterator<BookmarkAndKey> iterator = bookmarks.iterator();
        while (iterator.hasNext()) {
            BookmarkAndKey bk = iterator.next();
            if (prevStroke != null && bk.getName().equalsIgnoreCase(newBookmark)) {
                bk.setKey(prevStroke);
            }
            if (!bookmarkSet.contains(bk.getName())) {
                iterator.remove();
            }
        }
        keystrokeToBookmark.clear();
        for (BookmarkAndKey bk : bookmarks) {
            if (bk.getKey() != null) {
                keystrokeToBookmark.put(bk.getKey(), bk.getName());
                keystrokeToBookmark.put(getRemoveKey(bk.getKey()), bk.getName());
            }
        }
        listModel.clear();
        for (BookmarkAndKey b : bookmarks) {
            listModel.addElement(b);
        }
    }

    private List<String> getEmptyDataHashes() {
        byte[] emptyData = new byte[0];
        String emptyMD5 = DigestUtils.md5Hex(emptyData).toUpperCase();
        String emptySHA1 = DigestUtils.sha1Hex(emptyData).toUpperCase();
        String emptySHA256 = DigestUtils.sha256Hex(emptyData).toUpperCase();
        return Arrays.asList(emptyMD5, emptySHA1, emptySHA256);
    }

    private int getEmptyDataHashOrd(SortedDocValues sdv) throws IOException {
        List<String> emptyDataHashes = getEmptyDataHashes();
        int ord = sdv.lookupTerm(new BytesRef(emptyDataHashes.get(0)));
        if (ord < 0) {
            ord = sdv.lookupTerm(new BytesRef(emptyDataHashes.get(1)));
        }
        if (ord < 0) {
            ord = sdv.lookupTerm(new BytesRef(emptyDataHashes.get(2)));
        }
        return ord;
    }

    private boolean includeDuplicatesUsingHash(int countSelected, int countDocs) {
        // A case with 44M items take ~2600ms using the linear approach with BitSet.
        // The same case takes ~2200ms when selected 1000 items using the hash Lucene
        // search approach. So, we decide to use the hash approach if the estimated time
        // is lower than the linear approach time.
        return 2200L * countSelected / 1000 < 2600L * countDocs / 44_000_000;
    }

    private void includeDuplicates(ArrayList<IItemId> uniqueSelectedIds) {

        ProgressDialog progress = new ProgressDialog(App.get(), null);
        progress.setNote(Messages.getString("BookmarksManager.SearchingDuplicates")); //$NON-NLS-1$
        try {
            IPEDMultiSource ipedCase = App.get().appCase;
            LeafReader reader = ipedCase.getLeafReader();

            int duplicates = 0;
            boolean searchUsed;
            long t = System.currentTimeMillis();

            if (searchUsed = includeDuplicatesUsingHash(uniqueSelectedIds.size(), reader.maxDoc())) {

                progress.setIndeterminate(true);

                HashSet<String> hashes = new HashSet<>();
                HashSet<IItemId> selectedIdsSet = new HashSet<>();
                List<String> emptyDataHashes = getEmptyDataHashes();

                for (IItemId itemId : uniqueSelectedIds) {
                    IItem item = ipedCase.getItemByItemId(itemId);
                    if (item.getHash() != null && !item.getHash().isEmpty() && !emptyDataHashes.contains(item.getHash())) {
                        hashes.add(item.getHash().toLowerCase());
                        selectedIdsSet.add(itemId);
                    }
                }

                BooleanQuery.Builder query = new BooleanQuery.Builder();
                for (String hash : hashes) {
                    query.add(new TermQuery(new Term(IndexItem.HASH, hash)), Occur.SHOULD);
                }
                MultiSearchResult result = new IPEDSearcher(ipedCase, query.build()).multiSearch();

                for (IItemId dupItem : result.getIterator()) {
                    if (!selectedIdsSet.contains(dupItem)) {
                        uniqueSelectedIds.add(dupItem);
                        duplicates++;
                    }
                }

            } else {

                int i = 0;
                int max = uniqueSelectedIds.size() + reader.maxDoc();
                progress.setMaximum(max);

                SortedDocValues sdv = reader.getSortedDocValues(BasicProps.HASH);
                int emptyDataHashOrd = getEmptyDataHashOrd(sdv);
                int emptyValueOrd = sdv.lookupTerm(new BytesRef("")); //$NON-NLS-1$
                if (emptyValueOrd < 0) {
                    emptyValueOrd = -1;
                }

                BitSet hashOrd = new BitSet(sdv.getValueCount());
                BitSet luceneIds = new BitSet(reader.maxDoc());
                for (IItemId item : uniqueSelectedIds) {
                    int luceneId = ipedCase.getLuceneId(item);
                    int ord = DocValuesUtil.getOrd(sdv, luceneId);
                    if (ord > emptyValueOrd && ord != emptyDataHashOrd) {
                        hashOrd.set(ord);
                    }
                    luceneIds.set(luceneId);
                    if (max < 100 || (++i) % (max / 100) == 0) {
                        progress.setProgress(i);
                    }
                    if (progress.isCanceled())
                        return;
                }
                // must reset docValues to call getOrd again
                sdv = reader.getSortedDocValues(BasicProps.HASH);
                Iterator<Integer> docIt = ipedCase.getLuceneIdStream().iterator();
                while (docIt.hasNext()) {
                    int doc = docIt.next();
                    int ord = DocValuesUtil.getOrd(sdv, doc);
                    if (ord != -1 && hashOrd.get(ord) && !luceneIds.get(doc)) {
                        IItemId itemId = ipedCase.getItemId(doc);
                        uniqueSelectedIds.add(itemId);
                        duplicates++;
                    }
                    if (max < 100 || (++i) % (max / 100) == 0) {
                        progress.setProgress(i);
                    }
                    if (progress.isCanceled())
                        return;
                }
            }

            t = System.currentTimeMillis() - t;

            logger.info("{} duplicated {} found in {}ms", duplicates, searchUsed ? "items" : "docs", t);

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            progress.close();
        }

    }

    @Override
    public void actionPerformed(final ActionEvent evt) {
        if (evt.getSource() == butAdd || evt.getSource() == butRemove || evt.getSource() == butUpdateComment || evt.getSource() == butEdit || evt.getSource() == butDelete) {
            // Check if there is at least one bookmark selected
            if (list.getSelectedIndex() == -1) {
                showMessage(Messages.getString("BookmarksManager.AlertNoSelectedBookmarks"));
                return;
            }
        }

        if (evt.getSource() == butUpdateComment || evt.getSource() == butEdit) {
            // Check if there is more than one bookmark selected
            if (list.getSelectedIndices().length > 1) {
                showMessage(Messages.getString("BookmarksManager.AlertMultipleSelectedBookmarks"));
                return;
            }
        }

        IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
        if (evt.getSource() == butNew) {
            String name = newBookmark.getText().trim();
            String comment = comments.getText().trim();
            if (!name.isEmpty() && !listModel.contains(new BookmarkAndKey(name))) {
                multiBookmarks.newBookmark(name);
                multiBookmarks.setBookmarkComment(name, comment);
                multiBookmarks.setBookmarkColor(name, BookmarkColorsUtil.getInitialColor(multiBookmarks.getUsedColors(), name));
                updateList();
            }
            list.clearSelection();
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getName().equalsIgnoreCase(name)) {
                    list.setSelectedIndex(i);
                }
            }

        }
        if (evt.getSource() == butUpdateComment) {
            int idx = list.getSelectedIndex();
            String bookmarkName = list.getModel().getElementAt(idx).getName();
            multiBookmarks.setBookmarkComment(bookmarkName, comments.getText());
            multiBookmarks.saveState();
        }

        if (evt.getSource() == butAdd || evt.getSource() == butRemove || evt.getSource() == butNew) {

            ArrayList<IItemId> uniqueSelectedIds = getUniqueSelectedIds();

            ArrayList<String> bookmarks = new ArrayList<String>();
            for (int index : list.getSelectedIndices())
                bookmarks.add(list.getModel().getElementAt(index).getName());

            boolean insert = evt.getSource() == butAdd || evt.getSource() == butNew;
            bookmark(uniqueSelectedIds, bookmarks, insert, evt.getSource() == butNew);

        } else if (evt.getSource() == butDelete) {
            int result = JOptionPane.showConfirmDialog(dialog, Messages.getString("BookmarksManager.ConfirmDelete"), //$NON-NLS-1$
                    Messages.getString("BookmarksManager.ConfirmDelTitle"), JOptionPane.YES_NO_OPTION); //$NON-NLS-1$
            if (result == JOptionPane.YES_OPTION) {
                for (int index : list.getSelectedIndices()) {
                    String bookmark = list.getModel().getElementAt(index).getName();
                    multiBookmarks.delBookmark(bookmark);
                }
                updateList();
                multiBookmarks.saveState();
                BookmarksController.get().updateUI();

            }

        } else if (evt.getSource() == butEdit) {
            String currentName = list.getSelectedValue().getName();
            Color currentColor = multiBookmarks.getBookmarkColor(currentName);
            BookmarkEditDialog editDialog = new BookmarkEditDialog(dialog, currentName, currentColor);
            editDialog.setVisible(true);

            boolean changed = false;
            String newName = editDialog.getNewName();
            if (newName != null) {
                if (!newName.isEmpty() && !newName.equals(currentName)) {
                    if (!currentName.equalsIgnoreCase(newName) && listModel.contains(new BookmarkAndKey(newName))) {
                        JOptionPane.showMessageDialog(dialog, Messages.getString("BookmarksManager.AlreadyExists"));
                    } else {
                        multiBookmarks.renameBookmark(currentName, newName);
                        updateList(currentName, newName);
                        currentName = newName;
                        changed = true;
                    }
                }
            }
            Color newColor = editDialog.getNewColor();
            if (newColor != null && !newColor.equals(currentColor)) {
                multiBookmarks.setBookmarkColor(currentName, newColor);
                changed = true;
                BookmarkColorsUtil.storeNameToColor(currentName, newColor);
            }

            if (changed) {
                multiBookmarks.saveState();
                BookmarksController.get().updateUI();
                list.repaint();
            }
        }

    }

    private ArrayList<IItemId> getUniqueSelectedIds() {
        final ArrayList<IItemId> uniqueSelectedIds = new ArrayList<IItemId>();
        final App app = App.get();
        if (checked.isSelected()) {
            int sourceId = 0;
            for (IPEDSource source : app.appCase.getAtomicSources()) {
                BitSet ids = new BitSet();
                // we must add items in index order
                final int finalSourceId = sourceId;
                source.getLuceneIdStream().forEach(luceneId -> {
                    int id = source.getId(luceneId);
                    if (source.getBookmarks().isChecked(id) && !ids.get(id)) {
                        uniqueSelectedIds.add(new ItemId(finalSourceId, id));
                        ids.set(id);
                    }
                });
                sourceId++;
            }
        } else if (highlighted.isSelected()) {
            BitSet bitSet = new BitSet();
            for (int row : app.resultsTable.getSelectedRows()) {
                int rowModel = app.resultsTable.convertRowIndexToModel(row);
                bitSet.set(rowModel);
            }
            // we must add items in index order
            bitSet.stream().forEach(rowModel -> uniqueSelectedIds.add(app.ipedResult.getItem(rowModel)));
        }

        return uniqueSelectedIds;
    }

    private void bookmark(ArrayList<IItemId> uniqueSelectedIds, List<String> bookmarks, boolean insert, boolean isNew) {
        if (uniqueSelectedIds.isEmpty() && !isNew) {
            showMessage(Messages.getString(checked.isSelected() ? "BookmarksManager.AlertNoCheckedtems" : "BookmarksManager.AlertNoHighlightedItems"));
            return;
        }

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
        String bookmarkName = list.getModel().getElementAt(idx).getName();
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

        if (e.getKeyCode() == KeyEvent.VK_SHIFT || e.getKeyCode() == KeyEvent.VK_CONTROL || e.getKeyCode() == KeyEvent.VK_ALT) {
            return;
        }

        // Avoid conflict with CTRL+A (select all), CTRL+B (Open bookmarks manager
        // window) and CTRL+C (copy selected table cell content).
        if (e.isControlDown() && (e.getKeyCode() == 'B' || e.getKeyCode() == 'C')) {
            showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert4"));
            e.consume();
            return;
        }

        // Avoid conflict with keys used for selection/navigation in the bookmark list,
        // items table or items gallery.
        if ((e.isControlDown() && e.getKeyCode() == 'A') || e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_DOWN
                || e.getKeyCode() == KeyEvent.VK_HOME || e.getKeyCode() == KeyEvent.VK_END || e.getKeyCode() == KeyEvent.VK_PAGE_UP || e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
            return;
        }

        // Avoid conflict with keys that are used for item selection (space) and
        // recursive item selection (R), parents (P), references (F) and referenced by (D)
        if (e.getKeyCode() == KeyEvent.VK_SPACE || Arrays.asList('R', 'P', 'F', 'D').contains((char) e.getKeyCode())) {
            if (e.getSource() == list) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert4"));
                e.consume();
            }
            return;
        }

        KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiersEx(), true);

        if (e.getSource() == list) {
            if (list.getSelectedIndices().length != 1) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert1"));
                e.consume();
                return;
            }
            if ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert2"));
                e.consume();
                return;
            }

            int index = list.getSelectedIndex();

            if (keystrokeToBookmark.containsKey(stroke)) {
                if (list.getModel().getElementAt(index).getKey() == stroke) {
                    removeKeyStroke(list.getModel().getElementAt(index).getName());
                    list.getModel().getElementAt(index).setKey(null);
                    list.repaint();
                } else {
                    showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert3"));
                }
                e.consume();
                return;
            }

            list.getModel().getElementAt(index).setKey(stroke);
            list.repaint();

            String bookmarkStr = list.getModel().getElementAt(index).getName();
            removeKeyStroke(bookmarkStr);

            setKeyStroke(stroke, bookmarkStr);
            e.consume();

        } else {
            String bookmark = keystrokeToBookmark.get(stroke);
            if (bookmark == null) {
                return;
            }
            ArrayList<IItemId> uniqueSelectedIds = getUniqueSelectedIds();
            bookmark(uniqueSelectedIds, Collections.singletonList(bookmark), (e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) == 0, false);
            e.consume();
        }

    }

    private void setKeyStroke(KeyStroke stroke, String bookmarkStr) {
        keystrokeToBookmark.put(stroke, bookmarkStr);
        keystrokeToBookmark.put(getRemoveKey(stroke), bookmarkStr);

        App.get().appCase.getMultiBookmarks().setBookmarkKeyStroke(bookmarkStr, stroke);
        App.get().appCase.getMultiBookmarks().saveState();
    }

    private void removeKeyStroke(String bookmarkStr) {
        Iterator<KeyStroke> iterator = keystrokeToBookmark.keySet().iterator();
        while (iterator.hasNext()) {
            String bookmark = keystrokeToBookmark.get(iterator.next());
            if (bookmark.equalsIgnoreCase(bookmarkStr)) {
                iterator.remove();
            }
        }
        App.get().appCase.getMultiBookmarks().removeBookmarkKeyStroke(bookmarkStr);
        App.get().appCase.getMultiBookmarks().saveState();
    }

    // alt key remove from bookmark
    private KeyStroke getRemoveKey(KeyStroke k) {
        return KeyStroke.getKeyStroke(k.getKeyCode(), k.getModifiers() | KeyEvent.ALT_DOWN_MASK, true);
    }

    private void showMessage(String msg) {
        JOptionPane.showMessageDialog(dialog, msg, dialog.getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    public boolean hasSingleKeyShortcut() {
        for (KeyStroke k : keystrokeToBookmark.keySet()) {
            if ((k.getModifiers() & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) == 0) {
                int c = k.getKeyCode();
                if ((c >= KeyEvent.VK_0 && c <= KeyEvent.VK_9) || (c >= KeyEvent.VK_A && c <= KeyEvent.VK_Z)) {
                    return true;
                }
            }
        }
        return false;
    }
}
