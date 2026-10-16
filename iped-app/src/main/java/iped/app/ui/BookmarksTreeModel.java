package iped.app.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import iped.data.IMultiBookmarks;

/**
 * TreeModel for hierarchical bookmarks with <BookmarksTree.PATH_SEPARATOR> as separator.
 * All nodes (including intermediate ones) are regular bookmarks.
 */
public class BookmarksTreeModel implements TreeModel {

    public static final String ROOT_NAME = Messages.getString("BookmarksTreeModel.RootName");
    public static final String NO_BOOKMARKS_NAME = Messages.getString("BookmarksTreeModel.NoBookmarks");

    public static final BookmarkRoot ROOT = new BookmarkRoot();
    public static final NoBookmarks NO_BOOKMARKS = new NoBookmarks();

    private BookmarkTree bookmarkTree;
    private boolean filterTree; // If true, shows NO_BOOKMARKS for filtering

    private static class BookmarkRoot {
        public String toString() {
            return ROOT_NAME;
        }
    }

    private static class NoBookmarks {
        public String toString() {
            return NO_BOOKMARKS_NAME;
        }
    }

    public BookmarksTreeModel() {
        this(true);
    }

    public BookmarksTreeModel(boolean filterTree) {
        this.filterTree = filterTree;
        rebuildTree();
    }

    public boolean isFilterTree() {
        return filterTree;
    }
    
    /**
     * Adds a new bookmark node to the existing tree without rebuilding
     */
    public BookmarkNode addBookmarkNode(String fullPath) {
        if (bookmarkTree == null) {
            rebuildTree();
        }
        
        // Add the node to the tree structure
        BookmarkNode newNode = bookmarkTree.addBookmark(fullPath);
        
        if (newNode != null) {
            // Load properties from backend
            IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
            newNode.setColor(multiBookmarks.getBookmarkColor(fullPath));
            newNode.setComment(multiBookmarks.getBookmarkComment(fullPath));
            newNode.setCount(multiBookmarks.getBookmarkCount(fullPath));
            newNode.setKeyStroke(multiBookmarks.getBookmarkKeyStroke(fullPath));
            
            // Recalculate subtree counts from this node up to root
            bookmarkTree.getRoot().recalculateSubtreeCount();
        }
        
        return newNode;
    }
    
    /**
     * Moves a bookmark node within the existing tree without rebuilding
     */
    public void moveBookmarkNode(String oldPath, String newPath) {
        if (bookmarkTree == null) {
            return;
        }

        bookmarkTree.moveBookmark(oldPath, newPath);

        // Recalculate subtree counts
        bookmarkTree.getRoot().recalculateSubtreeCount();
    }
    
    /**
     * Renames a bookmark node within the existing tree without rebuilding
     */
    public void renameBookmarkNode(String oldPath, String newPath) {
        if (bookmarkTree == null) {
            return;
        }

        bookmarkTree.renameBookmark(oldPath, newPath);
    }
    
    /**
     * Removes a bookmark node from the existing tree without rebuilding
     */
    public void removeBookmarkNode(String fullPath) {
        if (bookmarkTree == null) {
            return;
        }
        
        bookmarkTree.removeBookmark(fullPath);
        
        // Recalculate subtree counts
        bookmarkTree.getRoot().recalculateSubtreeCount();
    }
    
    /**
     * Updates an existing bookmark node's properties
     */
    public void updateBookmarkNode(String fullPath) {
        if (bookmarkTree == null) {
            return;
        }
        
        BookmarkNode node = bookmarkTree.findByPath(fullPath);
        if (node != null) {
            IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
            node.setColor(multiBookmarks.getBookmarkColor(fullPath));
            node.setComment(multiBookmarks.getBookmarkComment(fullPath));
            node.setCount(multiBookmarks.getBookmarkCount(fullPath));
            node.setKeyStroke(multiBookmarks.getBookmarkKeyStroke(fullPath));
            
            // Recalculate subtree counts
            bookmarkTree.getRoot().recalculateSubtreeCount();
        }
    }

    /**
     * Rebuilds the tree from the current bookmark set
     */
    public void rebuildTree() {
        if (App.get().appCase == null) {
            bookmarkTree = new BookmarkTree();
            return;
        }

        // Get bookmarks from backend and build tree
        IMultiBookmarks multiBookmarks = App.get().appCase.getMultiBookmarks();
        bookmarkTree = new BookmarkTree();
        bookmarkTree.buildFromBookmarkSet(multiBookmarks.getBookmarkSet());

        // Load properties for all nodes (including intermediate ones)
        for (BookmarkNode node : bookmarkTree.getAllNodes()) {
            if (!node.isRoot()) {
                String path = node.getFullPath();
                node.setColor(multiBookmarks.getBookmarkColor(path));
                node.setComment(multiBookmarks.getBookmarkComment(path));
                node.setCount(multiBookmarks.getBookmarkCount(path));
                node.setKeyStroke(multiBookmarks.getBookmarkKeyStroke(path));
            }
        }
        
        // Recalculate subtree counts for all nodes after loading
        bookmarkTree.getRoot().recalculateSubtreeCount();
    }

    public BookmarkTree getBookmarkTree() {
        return bookmarkTree;
    }

    @Override
    public Object getRoot() {
        return ROOT;
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (ROOT.equals(parent)) {
            if (bookmarkTree == null) {
                rebuildTree();
            }

            BookmarkNode rootNode = bookmarkTree.getRoot();

            // If filterTree is true, NO_BOOKMARKS is first child
            if (filterTree) {
                if (index == 0) {
                    return NO_BOOKMARKS;
                }
                List<BookmarkNode> children = getSortedChildren(rootNode);
                int adjustedIndex = index - 1;
                return adjustedIndex < children.size() ? children.get(adjustedIndex) : null;
            } else {
                // No NO_BOOKMARKS, just show regular children
                List<BookmarkNode> children = getSortedChildren(rootNode);
                return index < children.size() ? children.get(index) : null;
            }
        } 
        else if (parent instanceof BookmarkNode) {
            BookmarkNode node = (BookmarkNode) parent;
            List<BookmarkNode> children = getSortedChildren(node);
            return index < children.size() ? children.get(index) : null;
        }        
        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (ROOT.equals(parent)) {
            if (App.get().appCase == null) {
                return 0;
            }
            
            if (bookmarkTree == null) {
                rebuildTree();
            }

            int count = bookmarkTree.getRoot().getChildCount();            
            // Adjust count if filterTree (NO_BOOKMARKS node exists)
            if (filterTree)
                count++;
            return count;
        } 
        else if (parent instanceof BookmarkNode) {
            BookmarkNode node = (BookmarkNode) parent;
            return node.getChildCount();
        }        
        return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
        if (ROOT.equals(node)) {
            return false;
        }
        if (NO_BOOKMARKS.equals(node)) {
            return true;
        }
        if (node instanceof BookmarkNode) {
            return ((BookmarkNode) node).isLeaf();
        }
        return true;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // TODO Auto-generated method stub

    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (child == null || parent == null) {
            return -1;
        }

        if (ROOT.equals(parent)) {
            if (NO_BOOKMARKS.equals(child)) {
                return filterTree ? 0 : -1;
            }
            
            if (bookmarkTree == null) {
                rebuildTree();
            }

            BookmarkNode rootNode = bookmarkTree.getRoot();
            List<BookmarkNode> children = getSortedChildren(rootNode);
            int idx = children.indexOf(child);

            // Adjust index if filterTree (NO_BOOKMARKS takes index 0)
            if (filterTree && idx >= 0) {
                idx++;
            }
            return idx;
        } 
        else if (parent instanceof BookmarkNode) {
            BookmarkNode parentNode = (BookmarkNode) parent;
            List<BookmarkNode> children = getSortedChildren(parentNode);
            return children.indexOf(child);
        }
        return -1;
    }

    /**
     * Gets sorted children of a node
     */
    private List<BookmarkNode> getSortedChildren(BookmarkNode node) {
        List<BookmarkNode> children = new ArrayList<>(node.getChildren());
        Collections.sort(children);
        return children;
    }

    /**
     * Finds a bookmark node by its full path
     */
    public BookmarkNode findBookmarkByPath(String fullPath) {
        if (bookmarkTree == null) {
            rebuildTree();
        }
        return bookmarkTree.findByPath(fullPath);
    }

    /**
     * Gets the tree path for a bookmark node
     */
    public TreePath getTreePath(BookmarkNode node) {
        if (node == null) {
            return null;
        }

        List<Object> path = new ArrayList<>();
        path.add(ROOT);

        // Build full path from root to node
        Stack<BookmarkNode> stack = new Stack<>();
        BookmarkNode current = node;
        while (current != null && !current.isRoot()) {
            stack.push(current);
            current = current.getParent();
        }
        while (!stack.isEmpty()) {
            path.add(stack.pop());
        }
        return new TreePath(path.toArray());
    }
    
    /**
     * Gets the nearest (deepest) common ancestor
     */
    public TreePath nearestCommonAncestor(List<TreePath> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }

        // Special case: only one node, then return its parent
        if (paths.size() == 1) {
            return paths.get(0).getParentPath();
        }

        // First path is set as reference
        Object[] base = paths.get(0).getPath();
        int maxDepth = base.length;

        // Find longest common prefix
        for (int i = 1; i < paths.size(); i++) {
            Object[] current = paths.get(i).getPath();
            maxDepth = Math.min(maxDepth, current.length);

            for (int j = 0; j < maxDepth; j++) {
                if (!base[j].equals(current[j])) {
                    maxDepth = j;
                    break;
                }
            }
        }

        if (maxDepth == 0) {
            return new TreePath(bookmarkTree.getRoot()); // return ROOT if no proper common ancestor
        }

        TreePath candidate = new TreePath(Arrays.copyOf(base, maxDepth));

        // If the candidate equals ANY input path, step up once
        for (TreePath p : paths) {
            if (candidate.equals(p)) {
                return candidate.getParentPath() != null ? candidate.getParentPath() : new TreePath(bookmarkTree.getRoot());
            }
        }

        return candidate != null ? candidate : new TreePath(bookmarkTree.getRoot());
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        // treeModelListeners.addElement(l);

    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        // treeModelListeners.removeElement(l);

    }

}
