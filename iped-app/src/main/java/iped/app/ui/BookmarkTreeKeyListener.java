package iped.app.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JTree;
import javax.swing.tree.TreePath;

import iped.engine.data.Bookmarks;

public class BookmarkTreeKeyListener extends KeyAdapter {
    private JTree tree;

    public BookmarkTreeKeyListener(JTree tree) {
        this.tree = tree;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.isConsumed())
            return;

        // Copy bookmarks full path or hierarchy
        // - Ctrl+C: Alphabetical list of selected bookmark paths.
        // - Ctrl+Shift+C: Alphabetical indented tree showing paths from root to selection.
        // - Ctrl+D: Alphabetical list of all selected paths and their children.
        // - Ctrl+Shift+D: Alphabetical indented subtree of the selection.
        if (e.isControlDown()) {
            int code = e.getKeyCode();
            if (code == KeyEvent.VK_C) {
                if (!e.isShiftDown())
                    copyPathsUpwards(); // Ctrl+C
                else
                    copyIndentedUpwards(); // Ctrl+Shift+C
                e.consume();
            } else if (code == KeyEvent.VK_D) {
                if (!e.isShiftDown())
                    copyPathsDownwards(); // Ctrl+D
                else
                    copyIndentedDownwards(); // Ctrl+Shift+D
                e.consume();
            }
        }
    }

    /**
     * Copies the full path of selected bookmarks (upwards)
     */
    private void copyPathsUpwards() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null)
            return;

        // Collect the full path of selected bookmarks
        List<String> fullPaths = new ArrayList<>();
        for (TreePath path : paths) {
            Object last = path.getLastPathComponent();
            if (last instanceof BookmarkNode) {
                fullPaths.add(((BookmarkNode) last).getFullPath());
            }
        }
        if (fullPaths.isEmpty())
            return;

        // Format and copy data
        // Sort paths alphabetically
        Collections.sort(fullPaths, String.CASE_INSENSITIVE_ORDER);
        StringBuilder sb = new StringBuilder();
        for (String p : fullPaths) {
            sb.append(BookmarkTree.displayPath(p)).append("\n");
        }
        copyToClipboard(sb.toString().trim());
    }

    /**
     * Copies the hierarchy of selected bookmarks as indented text (upwards)
     */
    private void copyIndentedUpwards() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null)
            return;

        // Collect all ancestor paths and selected paths into a sorted set
        // Sort paths alphabetically
        Set<String> uniquePaths = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);        
        for (TreePath path : paths) {
            if (path.getLastPathComponent() instanceof BookmarkNode) {
                // A TreePath contains the full chain from root to leaf
                // Add every intermediate path to the set
                TreePath current = path;
                while (current != null && current.getPathCount() > 1) { // skip root
                    uniquePaths.add(convertPathToString(current));
                    current = current.getParentPath();
                }
            }
        }

        // Format and copy data
        // Sorted set with indentation based on depth
        StringBuilder sb = new StringBuilder();
        for (String pathString : uniquePaths) {
            int level = BookmarkTree.countAncestors(pathString);
            int i;
            for (i = 0; i < level; i++) {
                if (i > 0)
                    sb.append("  ");
            }
            if (i > 0)
                sb.append("- ");
            sb.append(BookmarkTree.getNameFromPath(pathString)).append("\n");
        }
        copyToClipboard(sb.toString().trim());
    }

    /**
     * Helper to convert a TreePath to the standard String path
     */
    private String convertPathToString(TreePath path) {
        StringBuilder sb = new StringBuilder();
        Object[] objs = path.getPath();
        for (int i = 1; i < objs.length; i++) { // skip root
            if (i > 1)
                sb.append(Bookmarks.PATH_SEPARATOR_DISPLAY_FORMATTED);
            sb.append(objs[i].toString());
        }
        return sb.toString();
    }

    /**
     * Copies the full path of selected bookmarks (downwards)
     */
    private void copyPathsDownwards() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) return;

        // Get top-most nodes of the selection to avoid branch overlap
        List<Object> topNodes = getTopSelectedNodes(paths);
        Set<String> allPaths = new LinkedHashSet<>(); // preserves selection order

        for (Object node : topNodes) {
            if (node instanceof BookmarkNode) {
                BookmarkNode bNode = (BookmarkNode) node;
                allPaths.add(bNode.getFullPath());
                collectDescendantPaths(bNode, allPaths);
            } else if (node.equals(BookmarksTreeModel.ROOT)) {
                BookmarksTreeModel model = (BookmarksTreeModel) tree.getModel();
                BookmarkNode rootNode = model.getBookmarkTree().getRoot();
                for (BookmarkNode child : rootNode.getChildren()) {
                    allPaths.add(child.getFullPath());
                    collectDescendantPaths(child, allPaths);
                }
            }
        }

        // Format and copy data
        StringBuilder sb = new StringBuilder();
        for (String p : allPaths) {
            sb.append(BookmarkTree.displayPath(p)).append("\n");
        }
        copyToClipboard(sb.toString().trim());
    }

    /**
     * Helper to collect selected bookmarks descendant paths
     */
    private void collectDescendantPaths(BookmarkNode node, Set<String> pathSet) {
        for (BookmarkNode child : node.getChildren()) {
            pathSet.add(child.getFullPath());
            collectDescendantPaths(child, pathSet);
        }
    }

    /**
     * Copies the hierarchy of selected bookmarks as indented text (downwards)
     */
    private void copyIndentedDownwards() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null)
            return;

        // Get top-most nodes of the selection to avoid branch overlap
        List<Object> topNodes = getTopSelectedNodes(paths);
        if (topNodes.isEmpty())
            return;

        // Format and copy data
        StringBuilder sb = new StringBuilder();
        for (Object node : topNodes) {
            if (node instanceof BookmarkNode) {
                appendIndentedRecursive((BookmarkNode) node, sb, 0);
            } else if (node.equals(BookmarksTreeModel.ROOT)) {
                // If ROOT is selected, instead of outputing it copy all top-level bookmarks
                BookmarksTreeModel model = (BookmarksTreeModel) tree.getModel();
                BookmarkNode rootNode = model.getBookmarkTree().getRoot();
                for (BookmarkNode child : rootNode.getChildren()) {
                    appendIndentedRecursive(child, sb, 0);
                }
            }
        }
        copyToClipboard(sb.toString().trim());
    }

    /**
     * Helper to filter selected paths so only the "roots" of selections remain
     */
    private List<Object> getTopSelectedNodes(TreePath[] paths) {
        List<Object> topNodes = new ArrayList<>();
        for (TreePath path : paths) {
            boolean isDescendantOfAnotherSelection = false;
            for (TreePath other : paths) {
                if (path != other && other.isDescendant(path)) {
                    isDescendantOfAnotherSelection = true;
                    break;
                }
            }
            if (!isDescendantOfAnotherSelection) {
                Object last = path.getLastPathComponent();
                if (last instanceof BookmarkNode || last.equals(BookmarksTreeModel.ROOT)) {
                    topNodes.add(last);
                }
            }
        }
        return topNodes;
    }

    /**
     * Helper to create the hierarchy of selected bookmarks as indented text (downwards)
     */
    private void appendIndentedRecursive(BookmarkNode node, StringBuilder sb, int level) {
        // Apply indentation based on depth
        int i;
        for (i = 0; i < level; i++) {
            if (i > 0)
                sb.append("  ");
        }
        if (i > 0)
            sb.append("- ");
        sb.append(node.getName()).append("\n");

        // Get a copy of the children and sort them alphabetically by name
        List<BookmarkNode> children = new ArrayList<>(node.getChildren());
        Collections.sort(children, (n1, n2) -> n1.getName().compareToIgnoreCase(n2.getName()));

        // Recurse into sorted children
        for (BookmarkNode child : children) {
            appendIndentedRecursive(child, sb, level + 1);
        }
    }

    /**
     * Helper to copy text to clipboard
     */
    private void copyToClipboard(String textToCopy) {
        if (textToCopy == null || textToCopy.isEmpty())
            return;
        StringSelection selection = new StringSelection(textToCopy);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }

}