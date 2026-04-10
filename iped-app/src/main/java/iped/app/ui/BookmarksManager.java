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
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.Position;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

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

import iped.app.ui.bookmarks.BookmarkNameField;
import iped.app.ui.bookmarks.BookmarkColorsUtil;
import iped.app.ui.bookmarks.BookmarkEditDialog;
import iped.app.ui.bookmarks.BookmarkTreeCellRenderer;
import iped.data.IItem;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.engine.data.Bookmarks;
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

public class BookmarksManager implements ActionListener, TreeSelectionListener, KeyListener {

    private static final Logger logger = LoggerFactory.getLogger(BookmarksManager.class);

    private static BookmarksManager instance = new BookmarksManager();

    // Controls how bookmarks names are shown on results table Bookmark column
    private static boolean SHORT_BOOKMARKS_NAMES = false;

    public static final int maxBookmarkNameLength = 512;

    JDialog dialog = new JDialog(App.get());
    JLabel msg = new JLabel(Messages.getString("BookmarksManager.Dataset")); //$NON-NLS-1$
    JRadioButton highlighted = new JRadioButton();
    JRadioButton checked = new JRadioButton();
    ButtonGroup group = new ButtonGroup();
    JCheckBox duplicates = new JCheckBox();
    JCheckBox shortNames = new JCheckBox();
    JButton butAdd = new JButton(Messages.getString("BookmarksManager.Add")); //$NON-NLS-1$
    JButton butRemove = new JButton(Messages.getString("BookmarksManager.Remove")); //$NON-NLS-1$
    JButton butEdit = new JButton(Messages.getString("BookmarksManager.Edit")); //$NON-NLS-1$
    BookmarkNameField newBookmark = new BookmarkNameField();
    JTextArea comments = new JTextArea();
    JButton butNew = new JButton(Messages.getString("BookmarksManager.New")); //$NON-NLS-1$
    JButton butUpdateComment = new JButton(Messages.getString("BookmarksManager.Update")); //$NON-NLS-1$
    JButton butDelete = new JButton(Messages.getString("BookmarksManager.Delete")); //$NON-NLS-1$
    JTree bookmarksTree = new JTree(new BookmarksTreeModel(false)) {
        @Override
        public TreePath getNextMatch(String prefix,
                                    int startingRow,
                                    Position.Bias bias) {
            // Disable type-to-select behavior
            return null;
        }
    };
    JScrollPane scrollTree = new JScrollPane(bookmarksTree);

    private HashMap<KeyStroke, String> keystrokeToBookmark = new HashMap<>();

    public static BookmarksManager get() {
        return instance;
    }

    public static boolean isVisible() {
        return instance.dialog.isVisible();
    }

    public static void setVisible() {
        instance.dialog.setVisible(true);
        // Select root by default if nothing selected
        if (instance.bookmarksTree.getSelectionPath() == null) {
            instance.bookmarksTree.setSelectionRow(0); // Select root
        }
        instance.newBookmark.clear();
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
        dialog.setSize(480, 560);

        group.add(highlighted);
        group.add(checked);
        highlighted.setSelected(true);
        duplicates.setText(Messages.getString("BookmarksManager.AddDuplicates")); //$NON-NLS-1$
        duplicates.setSelected(false);
        shortNames.setText(Messages.getString("BookmarksManager.ShortBookmarksNames")); //$NON-NLS-1$
        shortNames.setSelected(SHORT_BOOKMARKS_NAMES);
        shortNames.addActionListener(this);

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
        top.add(shortNames);

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

        // Configure tree
        bookmarksTree.setRootVisible(true);
        bookmarksTree.setShowsRootHandles(true);
        bookmarksTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        bookmarksTree.setCellRenderer(new BookmarkTreeCellRenderer());
        bookmarksTree.setDragEnabled(true);
        bookmarksTree.setDropMode(DropMode.ON_OR_INSERT);
        bookmarksTree.setTransferHandler(new BookmarkTreeTransferHandler());
        updateTree();
        
        JPanel center = new JPanel(new BorderLayout());
        JPanel bookmark = new JPanel(new BorderLayout());
        bookmark.add(newBookmark, BorderLayout.PAGE_START);
        bookmark.add(commentScroll, BorderLayout.CENTER);
        bookmark.add(shortcutBookmark, BorderLayout.PAGE_END);
        center.add(bookmark, BorderLayout.PAGE_START);
        center.add(scrollTree, BorderLayout.CENTER);

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

        bookmarksTree.addTreeSelectionListener(this);
        
        // Clear key listeners
        for (KeyListener kl : bookmarksTree.getKeyListeners()) {
            bookmarksTree.removeKeyListener(kl);
        }
        // Add listener to handle copy shortcuts
        bookmarksTree.addKeyListener(new BookmarkTreeKeyListener(bookmarksTree));
        // Add listener to handle bookmark shortcut key assignments
        bookmarksTree.addKeyListener(this);

        bookmarksTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    TreePath path = bookmarksTree.getSelectionPath();
                    if (path != null && path.getLastPathComponent() instanceof BookmarkNode) {
                        actionPerformed(new ActionEvent(butEdit, 0, ""));
                    }
                }
            }
        });

        dialog.setLocationRelativeTo(App.get());
    }

    public void updateTree() {
        // Expand all nodes by default
        BookmarksTreeModel model = (BookmarksTreeModel) bookmarksTree.getModel();
        BookmarkNode root = model.getBookmarkTree().getRoot();
        expandNodes(root, model);
        
        // Restore keystroke mappings
        keystrokeToBookmark.clear();
        Set<String> bookmarkSet = App.get().appCase.getMultiBookmarks().getBookmarkSet();
        for (String bookmark : bookmarkSet) {
            KeyStroke stroke = App.get().appCase.getMultiBookmarks().getBookmarkKeyStroke(bookmark);
            if (stroke != null) {
                keystrokeToBookmark.put(stroke, bookmark);
                keystrokeToBookmark.put(getRemoveKey(stroke), bookmark);
            }
        }        
    }

    /**
     * Expands all nodes in the tree starting from the given node
     */
    private void expandNodes(BookmarkNode node, BookmarksTreeModel model) {
        if (!node.isRoot() && node.hasChildren()) {
            TreePath treePath = model.getTreePath(node);
            if (treePath != null) {
                bookmarksTree.expandPath(treePath);
            }
        }
        for (BookmarkNode child : node.getChildren()) {
            expandNodes(child, model);
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
        if (evt.getSource() == shortNames) {
            setShortBookmarksNames(shortNames.isSelected());
        }

        if (evt.getSource() == butAdd || evt.getSource() == butRemove || evt.getSource() == butUpdateComment || evt.getSource() == butEdit || evt.getSource() == butDelete) {
            // Check if there is at least one bookmark selected
            if (bookmarksTree.getSelectionPath() == null) {
                showMessage(Messages.getString("BookmarksManager.AlertNoSelectedBookmarks"));
                return;
            }
        }

        if (evt.getSource() == butUpdateComment || evt.getSource() == butEdit) {
            // Check if there is more than one bookmark selected
            if (bookmarksTree.getSelectionPaths() != null && bookmarksTree.getSelectionPaths().length > 1) {
                showMessage(Messages.getString("BookmarksManager.AlertMultipleSelectedBookmarks"));
                return;
            }
        }

        IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
        if (evt.getSource() == butNew) {
            String inputPath = newBookmark.getFullPath().replace(Bookmarks.PATH_SEPARATOR_DISPLAY, Bookmarks.PATH_SEPARATOR);
            String comment = comments.getText().trim();
            
            if (inputPath.isEmpty()) {
                showMessage("Please enter a bookmark name");
                return;
            }
                    
            // Determine new bookmark full path (absolute or relative)
            String fullPath;
            TreePath selectedPath = bookmarksTree.getSelectionPath();            
            if (inputPath.startsWith(Bookmarks.PATH_SEPARATOR)) {
                // If starts with separator, use root as parent (absolute path)
                fullPath = BookmarkTree.normalizePath(inputPath);
            } else {
                // Use selected node as parent (relative path)
                inputPath = BookmarkTree.normalizePath(inputPath);
                if (selectedPath != null) {
                    Object selected = selectedPath.getLastPathComponent();
                    if (selected.equals(BookmarksTreeModel.ROOT)) {
                        fullPath = inputPath;
                    } else if (selected instanceof BookmarkNode) {
                        BookmarkNode parent = (BookmarkNode) selected;
                        fullPath = BookmarkTree.joinPath(parent.getFullPath(), inputPath);
                    } else {
                        fullPath = inputPath;
                    }
                } else {
                    fullPath = inputPath;
                }
            }
            
            // Validate path
            if (!isValidBookmarkPath(fullPath)) {
                showMessage("Invalid bookmark path");
                return;
            }
            
            // Check if bookmark already exists
            if (multiBookmarks.getBookmarkSet().contains(fullPath)) {
                showMessage(Messages.getString("BookmarksManager.AlreadyExists"));
                return;
            }

            BookmarksTreeModel model = (BookmarksTreeModel) bookmarksTree.getModel();
            BookmarkNode newNode = null;

            // Create the new bookmark (in case of a bookmark hierarchy, create intermediate level bookmarks if needed)
            // Iterates through each path segment so as to create intermediate level bookmarks nodes as needed as well
			String[] segments = BookmarkTree.splitPath(fullPath);
			StringBuilder currentPath = new StringBuilder();			
			for (int i = 0; i < segments.length; i++) {
                currentPath.append(i == 0 ? "" : Bookmarks.PATH_SEPARATOR).append(segments[i]);
				String pathStr = currentPath.toString();
				
				// Create bookmark if it doesn't exist
				if (!multiBookmarks.getBookmarkSet().contains(pathStr)) {
                    // Create bookmark
					multiBookmarks.newBookmark(pathStr);
                    // Only set comment on the final bookmark level
					if (i == segments.length - 1 && !comment.isEmpty()) {
						multiBookmarks.setBookmarkComment(pathStr, comment);
					}
					// Set bookmark color
					multiBookmarks.setBookmarkColor(pathStr, BookmarkColorsUtil.getInitialColor(multiBookmarks.getUsedColors(), pathStr));
                    // Save state
        			multiBookmarks.saveState();

                    // Add bookmark node to the model
                    newNode = model.addBookmarkNode(pathStr);
                    if (newNode == null) {
                        showMessage("Error creating bookmark node for bookmark:\n" + pathStr);
                        return;
                    }
                }
            }
            
            if (newNode != null) {
                // Expand all ancestors to make new node visible
                BookmarkNode parent = newNode.getParent();
                while (parent != null && !parent.isRoot()) {
                    TreePath parentPath = model.getTreePath(parent);
                    if (parentPath != null) {
                        bookmarksTree.expandPath(parentPath);
                    }
                    parent = parent.getParent();
                }
                
                // Notify tree that structure changed
                bookmarksTree.updateUI();
                
                // Select the new bookmark
                TreePath path = model.getTreePath(newNode);
                if (path != null) {
                    bookmarksTree.setSelectionPath(path);
                    bookmarksTree.scrollPathToVisible(path);
                }
                
                // Clear the input field
                newBookmark.clear();
            }
            else {
                showMessage("Error creating bookmark node for new bookmark:\n" + fullPath);
                return;
            }
        }
        
        if (evt.getSource() == butUpdateComment) {
            TreePath path = bookmarksTree.getSelectionPath();
            if (path != null && path.getLastPathComponent() instanceof BookmarkNode) {
                BookmarkNode node = (BookmarkNode) path.getLastPathComponent();
                node.setComment(comments.getText());
                String bookmarkName = node.getFullPath();
                multiBookmarks.setBookmarkComment(bookmarkName, comments.getText());
                multiBookmarks.saveState();
            }
        }

        if (evt.getSource() == butAdd || evt.getSource() == butRemove || evt.getSource() == butNew) {

            ArrayList<IItemId> uniqueSelectedIds = getUniqueSelectedIds();

            ArrayList<String> bookmarks = new ArrayList<String>();
            TreePath[] paths = bookmarksTree.getSelectionPaths();
            if (paths != null) {
                for (TreePath path : paths) {
                    Object obj = path.getLastPathComponent();
                    if (obj instanceof BookmarkNode) {
                        BookmarkNode node = (BookmarkNode) obj;
                        bookmarks.add(node.getFullPath());
                    }
                }
            }

            boolean insert = evt.getSource() == butAdd || evt.getSource() == butNew;
            bookmark(uniqueSelectedIds, bookmarks, insert, evt.getSource() == butNew);

        } else if (evt.getSource() == butDelete) {
            TreePath[] paths = bookmarksTree.getSelectionPaths();
            if (paths != null) {
                if (paths.length == 1 && paths[0].getLastPathComponent().equals(BookmarksTreeModel.ROOT)) {
                    showMessage("Cannot delete root node");
                    return;
                }

                int result = JOptionPane.showConfirmDialog(dialog, Messages.getString("BookmarksManager.ConfirmDelete"), //$NON-NLS-1$
                        Messages.getString("BookmarksManager.ConfirmDelTitle"), JOptionPane.YES_NO_OPTION); //$NON-NLS-1$                   
                if (result == JOptionPane.YES_OPTION) {
                    BookmarksTreeModel model = (BookmarksTreeModel) bookmarksTree.getModel();                    

                    // Collect paths to delete and check if any are under selected nodes in main tree
                    List<TreePath> pathsList = new ArrayList<>();  // Selected paths of BookmarkNodes only (ROOT is excluded)
                    Set<String> pathsToDelete = new HashSet<>();
                    boolean needsFilterUpdate = false;
                    for (TreePath path : paths) {
                        Object obj = path.getLastPathComponent();
                        if (obj instanceof BookmarkNode) {
                            pathsList.add(path);
                            BookmarkNode node = (BookmarkNode) obj;
                            pathsToDelete.add(node.getFullPath());
                            
                            // Delete this bookmark and all descendants
                            multiBookmarks.delBookmark(node.getFullPath());
                            if (node.hasChildren()) {
                                for (BookmarkNode desc : node.getAllDescendants()) {
                                    if (!desc.equals(node)) {
                                        multiBookmarks.delBookmark(desc.getFullPath());
                                        pathsToDelete.add(desc.getFullPath());
                                    }
                                }
                            }                            
                            // Remove from tree model
                            model.removeBookmarkNode(node.getFullPath());
                        }
                    }                    
                    multiBookmarks.saveState();
                    
                    // Check if any deleted paths were under selected nodes in bookmarks tab
                    JTree mainTree = App.get().bookmarksTree;
                    if (mainTree != null) {
                        TreePath[] selectedPaths = mainTree.getSelectionPaths();
                        if (selectedPaths != null) {
                            for (TreePath selectedPath : selectedPaths) {
                                Object selectedNode = selectedPath.getLastPathComponent();
                                if (selectedNode instanceof BookmarkNode) {
                                    BookmarkNode node = (BookmarkNode) selectedNode;
                                    String selectedFullPath = node.getFullPath();
                                    
                                    // Check if any deleted bookmark was under this selected node
                                    for (String deletedPath : pathsToDelete) {
                                        if (deletedPath.equals(selectedFullPath) || 
                                            deletedPath.startsWith(selectedFullPath + Bookmarks.PATH_SEPARATOR)) {
                                            needsFilterUpdate = true;
                                            break;
                                        }
                                    }

                                    if (needsFilterUpdate) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Select nearest (deepest) common ancestor
                    TreePath commonParent = model.nearestCommonAncestor(pathsList);
                    bookmarksTree.setSelectionPath(commonParent);
                    bookmarksTree.scrollPathToVisible(commonParent);
                    bookmarksTree.requestFocusInWindow();
                    
                    // Refresh tree display
                    bookmarksTree.updateUI();
                    BookmarksController.get().updateUI();
                    
                    // Force filter update if needed
                    if (needsFilterUpdate) {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            App.get().appletListener.updateFileListing();
                        });
                    }
                }
            }
        } else if (evt.getSource() == butEdit) {
            TreePath path = bookmarksTree.getSelectionPath();
            if (path != null) {
                Object selected = path.getLastPathComponent();
                
                // Can't edit root
                if (selected.equals(BookmarksTreeModel.ROOT)) {
                    showMessage("Cannot edit root node");
                    return;
                }
                
                if (selected instanceof BookmarkNode) {
                    BookmarkNode node = (BookmarkNode) selected;
                    
                    String currentName = node.getFullPath();
                    Color currentColor = multiBookmarks.getBookmarkColor(currentName);
                    
                    // Use only the node name (not full path) in edit dialog
                    BookmarkEditDialog editDialog = new BookmarkEditDialog(dialog, node.getName(), currentColor);
                    editDialog.setVisible(true);

                    boolean changed = false;
                    String newName = editDialog.getNewName();
                    if (newName != null && !newName.isEmpty()) {
                        // Build new full path
                        String newFullPath;
                        if (node.getParent() != null && !node.getParent().isRoot()) {
                            newFullPath = BookmarkTree.joinPath(node.getParent().getFullPath(), newName);
                        } else {
                            newFullPath = newName;
                        }
                        
                        if (!newFullPath.equals(currentName)) {
                            if (!isValidBookmarkPath(newFullPath)) {
                                showMessage("Invalid bookmark path");
                                return;
                            }
                            
                            if (!currentName.equalsIgnoreCase(newFullPath) && 
                                multiBookmarks.getBookmarkSet().contains(newFullPath)) {
                                JOptionPane.showMessageDialog(dialog, 
                                    Messages.getString("BookmarksManager.AlreadyExists"));
                                return;
                            } else {
                                currentName = newFullPath;

                                // Rename selected bookmark and its descendants
                                String oldPath = node.getFullPath();
                                String newPath = newFullPath;
                                for (BookmarkNode child : node.getAllDescendants()) {
                                    String childOldPath = child.getFullPath();
                                    String relativePath = childOldPath.substring(oldPath.length());
                                    String childNewPath = newPath + relativePath;
                                    multiBookmarks.renameBookmark(childOldPath, childNewPath);
                                }            
                                multiBookmarks.saveState();

                                // Rename bookmark tree node
                                BookmarksTreeModel model = (BookmarksTreeModel) bookmarksTree.getModel();                    
                                model.renameBookmarkNode(oldPath, newPath);

                                // Refresh tree display
                                bookmarksTree.repaint();
                                bookmarksTree.updateUI();

                                changed = true;
                            }
                        }
                    }                    
				
                    Color newColor = editDialog.getNewColor();
                    if (newColor != null && !newColor.equals(currentColor)) {
                        BookmarkColorsUtil.storeNameToColor(currentName, newColor);

                        multiBookmarks.setBookmarkColor(currentName, newColor);
                        multiBookmarks.saveState();
                        
                        // Update node color in tree
                        if (node != null) {
                            node.setColor(newColor);
                            bookmarksTree.repaint();
                            bookmarksTree.updateUI();
                        }

                        changed = true;
                    }

                    if (changed) {
                        BookmarksController.get().updateUI();
                    }
                }
            }
        }
    }

    private boolean isValidBookmarkPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String normalized = BookmarkTree.normalizePath(path);
        return !normalized.isEmpty();
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
    public void valueChanged(TreeSelectionEvent e) {
        TreePath path = bookmarksTree.getSelectionPath();
        if (path == null) {
            newBookmark.clear();
            comments.setText(null);
            return;
        }
        Object obj = path.getLastPathComponent();
        if (obj.equals(BookmarksTreeModel.ROOT)) {
            newBookmark.clear();
            comments.setText("");
            newBookmark.setParentPath(Bookmarks.PATH_SEPARATOR_DISPLAY);
        } else if (obj instanceof BookmarkNode) {
            BookmarkNode node = (BookmarkNode) obj;
            String comment = App.get().appCase.getMultiBookmarks().getBookmarkComment(node.getFullPath());
            newBookmark.clear();
            comments.setText(comment != null ? comment : "");
            newBookmark.setParentPath(node.getFullPath());
        }
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

        // Avoid conflict with CTRL+A (select all), CTRL+B (Open bookmarks manager window)
        if (e.isControlDown() && (e.getKeyCode() == 'B')) {
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
            if (e.getSource() == bookmarksTree) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert4"));
                e.consume();
            }
            return;
        }

        KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiersEx(), true);
        TreePath path = bookmarksTree.getSelectionPath();

        if (e.getSource() == bookmarksTree) {     
            if ((path == null) || (bookmarksTree.getSelectionCount() > 1) || !(path.getLastPathComponent() instanceof BookmarkNode)) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert1"));
                e.consume();
                return;
            }

            if ((e.getModifiersEx() & KeyEvent.ALT_DOWN_MASK) != 0) {
                showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert2"));
                e.consume();
                return;
            }

            BookmarkNode node = (BookmarkNode) path.getLastPathComponent();
            
            if (keystrokeToBookmark.containsKey(stroke)) {
                KeyStroke currentStroke = node.getKeyStroke();
                if (currentStroke != null && currentStroke.equals(stroke)) {
                    removeKeyStroke(node.getFullPath());
                    node.setKeyStroke(null);
                    bookmarksTree.repaint();
                } else {
                    showMessage(Messages.getString("BookmarksManager.KeyStrokeAlert3"));
                }
                e.consume();
                return;
            }

            node.setKeyStroke(stroke);
            bookmarksTree.repaint();
           
            String bookmarkPath = node.getFullPath();
            removeKeyStroke(bookmarkPath);

            setKeyStroke(stroke, bookmarkPath);
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
        bookmarksTree.updateUI();
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

    /**
     * Get the state of short bookmarks names configuration
     */
    public static boolean showShortBookmarksNames() {
        return SHORT_BOOKMARKS_NAMES;
    }

    /**
     * Updates the state of short bookmarks names configuration and refreshes the results table
     */
    public static void setShortBookmarksNames(boolean shortNames) {
        SHORT_BOOKMARKS_NAMES = shortNames;
        App app = App.get();
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (app.resultsTable != null) {
                app.resultsTable.repaint();
            }
        });
    }
}
