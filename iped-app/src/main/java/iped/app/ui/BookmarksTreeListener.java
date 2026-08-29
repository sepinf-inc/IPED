package iped.app.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.roaringbitmap.RoaringBitmap;

import iped.data.IMultiBookmarks;
import iped.engine.data.Bookmarks;
import iped.engine.data.MultiBitmapBookmarks;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IBitmapFilter;
import iped.viewers.api.IFilter;
import iped.viewers.api.IMutableFilter;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

/**
 * Listener for hierarchical bookmark selection.
 * Selecting a node with children includes all descendant bookmarks.
 */
public class BookmarksTreeListener implements TreeSelectionListener, TreeExpansionListener, IResultSetFilterer {
    private HashSet<Object> selection = new HashSet<>();
    private volatile boolean updatingSelection = false;
    private long collapsed = 0;
    private boolean clearing = false;

    HashMap<Object, IFilter> definedFilters = new HashMap<Object, IFilter>();
    
    // Track last moved/renamed bookmarks to force filter update
    private static List<Map.Entry<String, String>> modifiedBookmarksPaths = new ArrayList<>();

    /**
     * Called when a bookmark is moved/renamed to trigger filter update if needed
     */
    public static void notifyBookmarkMoved(List<Map.Entry<String, String>> newModifiedBookmarksPaths) {
        modifiedBookmarksPaths = newModifiedBookmarksPaths;
    }

    /**
     * Gets selected bookmark names.
     * If a node has children, includes all descendant paths.
     */
    public Set<String> getSelectedBookmarkNames() {
        Set<String> names = new HashSet<>();
        
        for (Object obj : selection) {
            if (obj == BookmarksTreeModel.ROOT || obj == BookmarksTreeModel.NO_BOOKMARKS) {
                continue;
            }
            
            if (obj instanceof BookmarkNode) {
                BookmarkNode node = (BookmarkNode) obj;
                // Add this node
                names.add(node.getFullPath());
                // Add all descendants if any
                if (node.hasChildren()) {
                    for (BookmarkNode desc : node.getAllDescendants()) {
                        if (!desc.equals(node)) {
                            names.add(desc.getFullPath());
                        }
                    }
                }
            }
        }
        
        return names;
    }

    public boolean isRootSelected() {
        return selection.contains(BookmarksTreeModel.ROOT);
    }

    public boolean isNoBookmarksSelected() {
        return selection.contains(BookmarksTreeModel.NO_BOOKMARKS);
    }

    @Override
    public void valueChanged(TreeSelectionEvent evt) {
        if (updatingSelection) {
            return;
        }

        if (System.currentTimeMillis() - collapsed < 500) {
            if (evt.getPath().getLastPathComponent().equals(BookmarksTreeModel.ROOT)) {
                updateModelAndSelection();
            }
            return;
        }

        for (TreePath path : evt.getPaths()) {
            Object component = path.getLastPathComponent();
            
            if (selection.contains(component)) {
                selection.remove(component);
                definedFilters.remove(component);
            } else {
                selection.add(component);
                
                if (component instanceof BookmarkNode) {
                    BookmarkNode node = (BookmarkNode) component;
                    Set<String> bookmarkNames = new HashSet<>();
                    
                    // Add this node and all descendants
                    bookmarkNames.add(node.getFullPath());
                    if (node.hasChildren()) {
                        for (BookmarkNode desc : node.getAllDescendants()) {
                            if (!desc.equals(node)) {
                                bookmarkNames.add(desc.getFullPath());
                            }
                        }
                    }
                    
                    if (!bookmarkNames.isEmpty()) {
                        definedFilters.put(component, new BookMarkFilter(bookmarkNames));
                    }
                } else if (!component.equals(BookmarksTreeModel.ROOT) && 
                           !component.equals(BookmarksTreeModel.NO_BOOKMARKS)) {
                    HashSet<String> oneBookmark = new HashSet<>();
                    oneBookmark.add(component.toString());
                    definedFilters.put(component, new BookMarkFilter(oneBookmark));
                }
            }
        }

        if (!clearing) {
            App.get().appletListener.updateFileListing();
        }

        if (selection.contains(BookmarksTreeModel.ROOT) || selection.isEmpty()) {
            App.get().setBookmarksDefaultColor(true);
        } else {
            App.get().setBookmarksDefaultColor(false);
        }
    }

    public void updateModelAndSelection() {
        updatingSelection = true;
        
        BookmarksTreeModel model = (BookmarksTreeModel) App.get().bookmarksTree.getModel();
        
        // Save expansion state before updating
        Map<String, Boolean> expansionState = new HashMap<>();
        if (model.getBookmarkTree() != null) {
            saveExpansionState(model, expansionState);
        }
        
        // Save selected paths to handle renames
        Set<String> selectedPaths = new HashSet<>();
        for (Object obj : selection) {
            if (obj instanceof BookmarkNode) {
                selectedPaths.add(((BookmarkNode) obj).getFullPath());
            }
        }
        
        if (model.getBookmarkTree() != null && !selection.isEmpty()) {
            HashSet<Object> tempSel = new HashSet<>(selection);
            selection.clear();
            definedFilters.clear();

            Set<String> currentBookmarks = App.get().appCase.getMultiBookmarks().getBookmarkSet();

            for (Object obj : tempSel) {
                if (obj == BookmarksTreeModel.ROOT || obj == BookmarksTreeModel.NO_BOOKMARKS) {
                    selection.add(obj);
                    continue;
                }
                
                if (obj instanceof BookmarkNode) {
                    BookmarkNode node = (BookmarkNode) obj;
                    String oldPath = node.getFullPath();
                    
                    // Check if this bookmark still exists at same path
                    if (currentBookmarks.contains(oldPath)) {
                        selectedPaths.add(oldPath);											   
                    }
                    else {
                        // Check if it was moved/renamed
                        for (Map.Entry<String, String> entry : modifiedBookmarksPaths) {
                            String movedOldPath = entry.getKey();
                            String movedNewPath = entry.getValue();
                            
                            if (movedOldPath.equals(oldPath)) {
                                // Update to new path
                                selectedPaths.add(movedNewPath);
                                break;
                            }
                        }
                    }
                }
            }

            boolean rootCollapsed = App.get().bookmarksTree.isCollapsed(0);
            
            // Rebuild the tree model
            BookmarksTreeModel newModel = new BookmarksTreeModel(model.isFilterTree());
            App.get().bookmarksTree.setModel(newModel);
            
            // Restore expansion state and expand paths to newly added/moved bookmarks
            restoreExpansionState(newModel, expansionState, currentBookmarks);
            
            if (rootCollapsed) {
                App.get().bookmarksTree.collapseRow(0);
            }

            // Restore selection based on paths
            ArrayList<TreePath> newSelectedPaths = new ArrayList<>();
            for (String path : selectedPaths) {
                BookmarkNode node = newModel.findBookmarkByPath(path);
                if (node != null) {
                    selection.add(node);
                    addFilterForNode(node);
                    TreePath treePath = newModel.getTreePath(node);
                    if (treePath != null) {
                        newSelectedPaths.add(treePath);
                    }
                }
            }
            
            // Also add ROOT and NO_BOOKMARKS if they were selected
            for (Object obj : tempSel) {
                if (obj == BookmarksTreeModel.ROOT || obj == BookmarksTreeModel.NO_BOOKMARKS) {
                    TreePath path;
                    if (obj == BookmarksTreeModel.ROOT) {
                        path = new TreePath(new Object[] { BookmarksTreeModel.ROOT });
                    } else {
                        path = new TreePath(new Object[] { BookmarksTreeModel.ROOT, BookmarksTreeModel.NO_BOOKMARKS });
                    }
                    newSelectedPaths.add(path);
                }
            }

            App.get().bookmarksTree.setSelectionPaths(newSelectedPaths.toArray(new TreePath[0]));

        } else {
            boolean rootCollapsed = App.get().bookmarksTree.isCollapsed(0);

            // Rebuild the tree model
            BookmarksTreeModel newModel = new BookmarksTreeModel(model.isFilterTree());
            App.get().bookmarksTree.setModel(newModel);
            
            // Restore expansion state
            Set<String> currentBookmarks = App.get().appCase.getMultiBookmarks().getBookmarkSet();
            restoreExpansionState(newModel, expansionState, currentBookmarks);
            
            if (rootCollapsed) {
                App.get().bookmarksTree.collapseRow(0);
            }
        }

        // Check if a bookmark was moved and if it affects the current filter
        if (modifiedBookmarksPaths.size() > 0) {
            boolean shouldUpdateFilter = false;

            for (Object obj : selection) {
                if (obj instanceof BookmarkNode) {
                    BookmarkNode selectedNode = (BookmarkNode) obj;
                    String selectedPath = selectedNode.getFullPath();
                    
                    for (Map.Entry<String, String> entry : modifiedBookmarksPaths) {
                        String oldPath = entry.getKey();
                        String newPath = entry.getValue();

                        // Check if moved bookmark's NEW location is under this selected node
                        if (newPath.equals(selectedPath) || 
                            newPath.startsWith(selectedPath + Bookmarks.PATH_SEPARATOR)) {
                            shouldUpdateFilter = true;
                            break;
                        }

                        // Check if moved bookmark's OLD location was under this selected node
                        if (oldPath != null && 
                            (oldPath.equals(selectedPath) || 
                            oldPath.startsWith(selectedPath + Bookmarks.PATH_SEPARATOR))) {
                            shouldUpdateFilter = true;
                            break;
                        }
                    }

                    if (shouldUpdateFilter) {
                        break;
                    }
                }
            }
            
            if (shouldUpdateFilter) {
                // Trigger filter update on next event loop
                javax.swing.SwingUtilities.invokeLater(() -> {
                    App.get().appletListener.updateFileListing();
                });
            }
            
            modifiedBookmarksPaths.clear();
        }

        // informs combinedfilter of bookmark change so it can update its internal
        // bitset cache
        App.get().filtersPanel.getCombinedFilterer().startSearchResult(App.get().ipedResult);

        updatingSelection = false;
    }
    
    /**
     * Saves the expansion state of all nodes in the tree
     */
    private void saveExpansionState(BookmarksTreeModel model, Map<String, Boolean> expansionState) {
        BookmarkNode root = model.getBookmarkTree().getRoot();
        saveNodeExpansionState(root, model, expansionState);
    }
    
    /**
     * Recursively saves expansion state for a node and its children
     */
    private void saveNodeExpansionState(BookmarkNode node, BookmarksTreeModel model, Map<String, Boolean> expansionState) {
        if (!node.isRoot()) {
            TreePath path = model.getTreePath(node);
            if (path != null) {
                boolean isExpanded = App.get().bookmarksTree.isExpanded(path);
                expansionState.put(node.getFullPath(), isExpanded);
            }
        }        
        if (node.hasChildren()) {
            for (BookmarkNode child : node.getChildren()) {
                saveNodeExpansionState(child, model, expansionState);
            }
        }
    }
    
    /**
     * Restores the expansion state of all nodes in the tree
     */
    private void restoreExpansionState(BookmarksTreeModel model, Map<String, Boolean> expansionState, Set<String> currentBookmarks) {
        // Find newly added bookmarks (not in saved state but in current bookmarks)
        Set<String> newBookmarks = new HashSet<>();
        for (String bookmark : currentBookmarks) {
            if (!expansionState.containsKey(bookmark)) {
                newBookmarks.add(bookmark);
            }
        }
        
        // For newly added bookmarks, ensure their parent paths are expanded
        for (String newBookmark : newBookmarks) {
            String[] parts = BookmarkTree.splitPath(newBookmark);
            StringBuilder currentPath = new StringBuilder();
            
            for (int i = 0; i < parts.length - 1; i++) { // Don't include the leaf itself
                if (currentPath.length() > 0) {
                    currentPath.append(Bookmarks.PATH_SEPARATOR);
                }
                currentPath.append(parts[i]);
                String ancestorPath = currentPath.toString();
                
                // Mark ancestor as expanded
                expansionState.put(ancestorPath, true);
            }
        }
        
        if (expansionState.isEmpty()) {
            return;
        }
        
        // Collect all paths that should be expanded, sorted by depth
        List<String> pathsToExpand = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : expansionState.entrySet()) {
            if (entry.getValue() == true) {
                pathsToExpand.add(entry.getKey());
            }
        }
        
        // Sort paths by depth (shallow paths first) to ensure parents are expanded before children
        pathsToExpand.sort((p1, p2) -> {
            int depth1 = p1.split(Bookmarks.PATH_SEPARATOR).length;
            int depth2 = p2.split(Bookmarks.PATH_SEPARATOR).length;
            return Integer.compare(depth1, depth2); // ascending order (shallow first)
        });
        
        // Expand paths in order
        for (String path : pathsToExpand) {
            BookmarkNode node = model.findBookmarkByPath(path);
            if (node != null) {
                TreePath treePath = model.getTreePath(node);
                if (treePath != null) {
                    App.get().bookmarksTree.expandPath(treePath);
                }
            }
        }
    }

    private void addFilterForNode(BookmarkNode node) {
        Set<String> bookmarkNames = new HashSet<>();
        bookmarkNames.add(node.getFullPath());
        if (node.hasChildren()) {
            for (BookmarkNode desc : node.getAllDescendants()) {
                if (!desc.equals(node)) {
                    bookmarkNames.add(desc.getFullPath());
                }
            }
        }
        if (!bookmarkNames.isEmpty()) {
            definedFilters.put(node, new BookMarkFilter(bookmarkNames));
        }
    }

    @Override
    public void treeExpanded(TreeExpansionEvent event) {

    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
        collapsed = System.currentTimeMillis();

    }

    @Override
    public void clearFilter() {
        clearing = true;
        App.get().bookmarksTree.clearSelection();
        clearing = false;
    }

    NoBookMarkFilter noBookMarkFilter = new NoBookMarkFilter();

    @Override
    public List getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        Set<String> bookmarkSelection = getSelectedBookmarkNames();
        
        if ((!bookmarkSelection.isEmpty() || isNoBookmarksSelected()) && !isRootSelected()) {
            result.addAll(definedFilters.values());
            
            if (isNoBookmarksSelected()) {
                result.add(noBookMarkFilter);
            }
        }
        return result;
    }

    public String toString() {
        return "Bookmarks filterer";
    }

    @Override
    public IFilter getFilter() {
        Set<String> bookmarkSelection = getSelectedBookmarkNames();

        if ((!bookmarkSelection.isEmpty() || isNoBookmarksSelected()) && !isRootSelected()) {
            IFilter result;
            if (isNoBookmarksSelected()) {
                if (bookmarkSelection.isEmpty()) {
                    result = noBookMarkFilter;
                } else {
                    result = new NoBookMarkFilter(bookmarkSelection);
                }
            } else {
                result = new BookMarkFilter(bookmarkSelection);
            }
            return result;
        }
        return null;
    }

    @Override
    public boolean hasFilters() {
        return selection.size() > 0 && !isRootSelected();
    }

    @Override
    public boolean hasFiltersApplied() {
        return selection.size() > 0 && !isRootSelected();
    }
}

class BookMarkFilter implements IResultSetFilter, IMutableFilter, IBitmapFilter {
    Set<String> bookmark;

    public BookMarkFilter(Set<String> bookmark2) {
        this.bookmark = bookmark2;
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        return (MultiSearchResult) App.get().appCase.getMultiBookmarks().filterBookmarks(src, (Set<String>) bookmark);
    }

    public String toString() {
        return bookmark.toString();
    }

    @Override
    public RoaringBitmap[] getBitmap() {
        IMultiBookmarks mm = App.get().appCase.getMultiBookmarks();
        if (mm instanceof MultiBitmapBookmarks) {
            return ((MultiBitmapBookmarks) mm).getBookmarksUnions(bookmark);
        }
        return null;
    }
}

class NoBookMarkFilter implements IResultSetFilter, IMutableFilter, IBitmapFilter {
    Set<String> bookmarkSelection;

    public NoBookMarkFilter(Set<String> bookmarkSelection) {
        this.bookmarkSelection = bookmarkSelection;
    }

    public NoBookMarkFilter() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        return (MultiSearchResult) App.get().appCase.getMultiBookmarks().filterNoBookmarks(src);
    }

    public String toString() {
        return BookmarksTreeModel.NO_BOOKMARKS_NAME;
    }

    @Override
    public boolean isToFilterOut() {
        return true;
    }

    @Override
    public RoaringBitmap[] getBitmap() {
        IMultiBookmarks mm = App.get().appCase.getMultiBookmarks();
        if (mm instanceof MultiBitmapBookmarks) {
            RoaringBitmap[] result = ((MultiBitmapBookmarks) mm).getBookmarksUnions();
            RoaringBitmap[] rbSelecitons = null;
            if (bookmarkSelection != null) {
                rbSelecitons = ((MultiBitmapBookmarks) mm).getBookmarksUnions(bookmarkSelection);
            }
            if (rbSelecitons != null) {
                for (int i = 0; i < result.length; i++) {
                    result[i].andNot(rbSelecitons[i]);
                }
            }
            return result;
        }
        return null;
    }
}
