package iped.app.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

import iped.engine.data.Bookmarks;

/**
 * Transfer handler for drag-and-drop operations on bookmark tree.
 * Allows dragging nodes (and their subtrees) to change their parent.
 * Supports multi-selection drag and drop with conflict resolution.
*/
public class BookmarkTreeTransferHandler extends TransferHandler {
    
    private DataFlavor nodesFlavor;
    private DataFlavor[] flavors = new DataFlavor[1];
    
    public BookmarkTreeTransferHandler() {
        try {
            String mimeType = DataFlavor.javaJVMLocalObjectMimeType +
                            ";class=\"[L" + BookmarkNode.class.getName() + ";\"";
                            // ";class=\"[Liped.app.ui.BookmarkNode;\"";
            nodesFlavor = new DataFlavor(mimeType);
            flavors[0] = nodesFlavor;
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFound: " + e.getMessage());
        }
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport support) {
        if (!support.isDrop()) {
            return false;
        }
        
        support.setShowDropLocation(true);
        
        if (!support.isDataFlavorSupported(nodesFlavor)) {
            return false;
        }
        
        // Get drop location
        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        TreePath dest = dl.getPath();
        
        if (dest == null) {
            return false;
        }
        
        // Get source nodes
        try {
            Transferable t = support.getTransferable();
            BookmarkNode[] sourceNodes = (BookmarkNode[]) t.getTransferData(nodesFlavor);
            
            Object destObj = dest.getLastPathComponent();
            
            // Can drop on root to make top-level bookmarks
            if (destObj.equals(BookmarksTreeModel.ROOT)) {
                // Allow drop on root to make it a top-level bookmark
                return true;
            }
            
            if (!(destObj instanceof BookmarkNode)) {
                return false;
            }
            
            BookmarkNode destNode = (BookmarkNode) destObj;
            
            // Check each source node
            for (BookmarkNode sourceNode : sourceNodes) {
                // Can't drop on itself
                if (sourceNode.equals(destNode)) {
                    return false;
                }
                
                // Can't drop on own descendant (would create cycle)
                BookmarkNode current = destNode;
                while (current != null) {
                    if (current.equals(sourceNode)) {
                        return false;
                    }
                    current = current.getParent();
                }
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        
        if (paths != null && paths.length > 0) {
            List<BookmarkNode> nodes = new ArrayList<>();
            
            for (TreePath path : paths) {
                Object obj = path.getLastPathComponent();
                
                // Can't drag root
                if (obj.equals(BookmarksTreeModel.ROOT)) {
                    continue;
                }
                
                if (obj instanceof BookmarkNode) {
                    nodes.add((BookmarkNode) obj);
                }
            }
            
            if (!nodes.isEmpty()) {
                return new BookmarkNodesTransferable(nodes.toArray(new BookmarkNode[0]));
														  
            }
        }
        
        return null;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }

        // Get nodes to transfer
        BookmarkNode[] sourceNodes;
        try {
            Transferable t = support.getTransferable();
            sourceNodes = (BookmarkNode[]) t.getTransferData(nodesFlavor);
        } catch (Exception e) {
            return false;
        }

        // Get drop location
        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        TreePath dest = dl.getPath();
        Object destObj = dest.getLastPathComponent();
        
        JTree tree = (JTree) support.getComponent();
        BookmarksTreeModel model = (BookmarksTreeModel) tree.getModel();
        
        // Determine new parent path
        String newParentPath;
        if (destObj.equals(BookmarksTreeModel.ROOT)) {
            newParentPath = "";
        } else if (destObj instanceof BookmarkNode) {
            newParentPath = ((BookmarkNode) destObj).getFullPath();
        } else {
            return false;
        }
        
        // Check for conflicts and build move list
        Set<String> conflicts = new HashSet<>();
        Set<String> siblingsNames = new HashSet<>();
        Map<String, String> moveOperations = new HashMap<>();

        for (BookmarkNode sourceNode : sourceNodes) {
            // Build new path
            String oldPath = sourceNode.getFullPath();
            String newPath;
            if (newParentPath.isEmpty()) {
                newPath = sourceNode.getName();
            } else {
                newPath = BookmarkTree.joinPath(newParentPath, sourceNode.getName());
            }
            
            // Skip if path hasn't changed
            if (oldPath.equals(newPath)) {
                continue;
            }
            
            // Check for duplicate destination names among moved nodes (siblings)
            if (!siblingsNames.add(sourceNode.getName())) {
                conflicts.add(sourceNode.getName());
            }

            // Check if destination already exists
            if (App.get().appCase.getMultiBookmarks().getBookmarkSet().contains(newPath)) {
                conflicts.add(sourceNode.getName());
            }
            
            // Store move operation paths
            moveOperations.put(oldPath, newPath);
        }
        
        // If conflicts exist show error and cancel move
        if (!conflicts.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append(Messages.getString("BookmarksManager.MoveErrorDuplication")).append(":\n");
            for (String conflict : conflicts) {
                message.append("  • ").append(conflict).append("\n");
            }
            JOptionPane.showMessageDialog(tree, 
                message.toString(), 
                Messages.getString("BookmarksManager.MoveError.Title"), 
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        if (moveOperations.isEmpty()) {
            return false;
        }
        
        // Perform the moves
        try {
            // Store modified bookmarks paths including their descendants paths
            List<Map.Entry<String, String>> modifiedBookmarksPaths = new ArrayList<>();

            // Sort oldPaths by depth (deepest paths first) to ensure children are processed before parents
            // Allows simultaneous moves of ancestor and descendant nodes without conflicts
            List<String> sortedOldPaths = new ArrayList<>(moveOperations.keySet());
            sortedOldPaths.sort((a, b) -> {
                int depthA = a.split(Bookmarks.PATH_SEPARATOR).length;
                int depthB = b.split(Bookmarks.PATH_SEPARATOR).length;
                return Integer.compare(depthB, depthA); // descending order (deepest first)
            });

            model = (BookmarksTreeModel) tree.getModel();

            // Store tree nodes expansion state
            Map<String, Boolean> expansionState = new HashMap<>();            

            // Rename selected bookmarks and their descendants in sort order
            for (String oldPath : sortedOldPaths) {
                String newPath = moveOperations.get(oldPath);
                BookmarkNode node = model.findBookmarkByPath(oldPath);
                for (BookmarkNode child : node.getAllDescendants()) {
                    String childOldPath = child.getFullPath();
                    String relativePath = childOldPath.substring(oldPath.length());
                    String childNewPath = newPath + relativePath;
                    modifiedBookmarksPaths.add(Map.entry(childOldPath, childNewPath));

                    TreePath path = model.getTreePath(child);
                    expansionState.put(childNewPath, tree.isExpanded(path));
                    
                    App.get().appCase.getMultiBookmarks().renameBookmark(childOldPath, childNewPath);
                    App.get().appCase.getMultiBookmarks().saveState();
                }
            }

            // Notify listener about the modified paths for eventual filter update
            BookmarksTreeListener.notifyBookmarkMoved(modifiedBookmarksPaths);
            
            // Moves bookmarks nodes in sort order
            for (String oldPath : sortedOldPaths) {
                String newPath = moveOperations.get(oldPath);
                model.moveBookmarkNode(oldPath, newPath);
            }
            tree.repaint();
            tree.updateUI();

            // Restore tree nodes expansion state
            restoreExpansionState(tree, expansionState);

            // Select the moved nodes in manager tree
            List<TreePath> pathsToSelect = new ArrayList<>();
            for (String oldPath : moveOperations.keySet()) {
                String newPath = moveOperations.get(oldPath);
                BookmarkNode node = model.findBookmarkByPath(newPath);
                if (node != null) {
                    TreePath newTreePath = model.getTreePath(node);
                    if (newTreePath != null) {
                        // tree.expandPath(newTreePath);
                        pathsToSelect.add(newTreePath);
                    }
                }
            }
            if (!pathsToSelect.isEmpty()) {
                tree.setSelectionPaths(pathsToSelect.toArray(new TreePath[0]));
                tree.scrollPathToVisible(pathsToSelect.get(0));
                tree.requestFocusInWindow();
            }

            // Update the UI (rebuilds bookmarks tab tree)
            BookmarksController.get().updateUI();
            
            return true;
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(tree,
                Messages.getString("BookmarksManager.MoveErrorException") + ": " + e.getMessage(),
                Messages.getString("BookmarksManager.MoveError.Title"),
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        // Nothing to clean up since we're doing MOVE operations
    }
    
    /**
     * Inner class for transferable bookmark nodes
     */
    private class BookmarkNodesTransferable implements Transferable {
        private BookmarkNode[] nodes;

        public BookmarkNodesTransferable(BookmarkNode[] nodes) {
            this.nodes = nodes;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return nodes;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return flavors;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return nodesFlavor.equals(flavor);
        }
    }

    /**
     * Restores the expansion state of moved nodes
     */
    private void restoreExpansionState(JTree tree, Map<String, Boolean> expansionState) {
        BookmarksTreeModel model = (BookmarksTreeModel) tree.getModel();

        if (expansionState.isEmpty()) {
            return;
        }

        // Sort paths by depth (deepest paths first) to ensure children are processed before parents
        List<String> sortedPaths = new ArrayList<>(expansionState.keySet());
        sortedPaths.sort((p1, p2) -> {
            int depth1 = p1.split(Bookmarks.PATH_SEPARATOR).length;
            int depth2 = p2.split(Bookmarks.PATH_SEPARATOR).length;
            return Integer.compare(depth2, depth1); // descending order (deepest first)
        });
        
        // Expand/collapse paths in order
        for (String path : sortedPaths) {
            BookmarkNode node = model.findBookmarkByPath(path);
            if (node != null) {
                TreePath treePath = model.getTreePath(node);
                if (treePath != null) {
                    if (expansionState.get(path)) {
                        tree.expandPath(treePath);
                    } else {
                        tree.collapsePath(treePath);
                    }
                }
            }
        }
    }
}