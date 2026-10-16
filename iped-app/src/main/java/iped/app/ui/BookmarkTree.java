package iped.app.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import iped.engine.data.Bookmarks;

/**
 * Manages the hierarchical bookmark tree structure.
 * All nodes (including intermediate ones) are treated as regular bookmarks.
 */
public class BookmarkTree {

    private BookmarkNode root;
    private Map<String, BookmarkNode> pathToNode;
    
    public BookmarkTree() {
        this.root = new BookmarkNode();
        this.pathToNode = new HashMap<>();
    }
    
    /**
     * Builds the tree from a set of bookmark names (paths with <PATH_SEPARATOR> separator)
     */
    public void buildFromBookmarkSet(Set<String> bookmarkNames) {
        root = new BookmarkNode();
        pathToNode = new HashMap<>();
        
        for (String bookmarkName : bookmarkNames) {
            addBookmark(bookmarkName);
        }
    }
    
    /**
     * Adds a bookmark to the tree, creating intermediate nodes as needed.
     * All nodes created are regular bookmarks.
     */
    public BookmarkNode addBookmark(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            return null;
        }
        
        fullPath = normalizePath(fullPath);
        
        // Check if already exists
        if (pathToNode.containsKey(fullPath)) {
            return pathToNode.get(fullPath);
        }
        
        String[] parts = splitPath(fullPath);
        BookmarkNode current = root;
        StringBuilder currentPath = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            currentPath.append(i == 0 ? "" : Bookmarks.PATH_SEPARATOR).append(part);
            
            String pathStr = currentPath.toString();
            BookmarkNode child = current.getChild(part);            
            if (child == null) {
                child = new BookmarkNode(part, current);
                current.addChild(child);
                pathToNode.put(pathStr, child);
            }            
            current = child;
        }
        
        return current;
    }
    
    /**
     * Removes a bookmark node and its subtree from the tree.
     */
    public boolean removeBookmark(String fullPath) {
        fullPath = normalizePath(fullPath);
        BookmarkNode node = pathToNode.get(fullPath);
        
        if (node == null || node.isRoot()) {
            return false;
        }
        
        // Update path-to-node map (remove paths for node and children)
        for (BookmarkNode descendant : node.getAllDescendants()) {
            pathToNode.remove(descendant.getFullPath());
        }
        
        // Remove node from parent
        BookmarkNode parent = node.getParent();
        parent.removeChild(node.getName());
        
        return true;
    }
    
    /**
     * Moves a bookmark node and its subtree.
     */
    public boolean moveBookmark(String oldPath, String newPath) {
        
        BookmarkNode node = pathToNode.get(oldPath);
        
        if (node == null || node.isRoot()) {
            return false;
        }
        
        if (!removeBookmark(oldPath)) {
            return false;
        }

        // Update new parent
        BookmarkNode newParent = findByPath(getParentPath(newPath));
        node.setParent(newParent);

        // Update path-to-node map
        for (BookmarkNode child : node.getAllDescendants()) {
            pathToNode.put(child.getFullPath(), child);
        }

        return true;
    }

    /**
     * Renames a bookmark (changes the path)
     */
    public boolean renameBookmark(String oldPath, String newPath) {
        oldPath = normalizePath(oldPath);
        newPath = normalizePath(newPath);
        
        if (oldPath.equals(newPath)) {
            return true;
        }
        
        BookmarkNode node = pathToNode.get(oldPath);        
        if (node == null || node.isRoot()) {
            return false;
        }
        
        // Check if new path already exists
        if (pathToNode.containsKey(newPath)) {
            return false;
        }
        
        // Update path-to-node map (remove old paths for node and children)
        for (BookmarkNode descendant : node.getAllDescendants()) {
            pathToNode.remove(descendant.getFullPath());
        }
        
        // Update node name
        String newName = getNameFromPath(newPath);
        node.setName(newName);

        // Update node name in parent
        BookmarkNode parent = node.getParent();
        String oldName = getNameFromPath(oldPath);
        parent.removeChild(oldName);
        parent.addChild(node);

        // Update path-to-node map (add new paths for node and children)
        for (BookmarkNode descendant : node.getAllDescendants()) {
            pathToNode.put(descendant.getFullPath(), descendant);
        }

        return true;
    }
       
    /**
     * Finds a node by its full path
     */
    public BookmarkNode findByPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return root;
        }
        return pathToNode.get(normalizePath(fullPath));
    }
    
    /**
     * Gets all nodes (including intermediate ones)
     */
    public List<BookmarkNode> getAllNodes() {
        return root.getAllDescendants();
    }
    
    /**
     * Gets all bookmark paths
     */
    public Set<String> getAllPaths() {
        Set<String> paths = new HashSet<>();
        for (BookmarkNode node : getAllNodes()) {
            if (!node.isRoot()) {
                paths.add(node.getFullPath());
            }
        }
        return paths;
    }
    
    /**
     * Gets the root node
     */
    public BookmarkNode getRoot() {
        return root;
    }
    
    /**
     * Checks if a path exists
     */
    public boolean containsPath(String fullPath) {
        return pathToNode.containsKey(normalizePath(fullPath));
    }

    /**
     * Gets the parent path of a given path
     */
    public static String getParentPath(String path) {
        path = normalizePath(path);
        int last = path.lastIndexOf(Bookmarks.PATH_SEPARATOR);
        if (last < 0) {
            return "";
        }
        return path.substring(0, last);
    }
    
    /**
     * Gets the name (last component) of a path
     */
    public static String getNameFromPath(String path) {
        path = normalizePath(path);
        int last = path.lastIndexOf(Bookmarks.PATH_SEPARATOR);
        if (last < 0) {
            return path;
        }
        return path.substring(last + Bookmarks.PATH_SEPARATOR.length());
    }
    
    /**
     * Counts the number of ancestors in a bookmark path
     */
    public static int countAncestors(String path) {
        if (path == null) {
            return 0;
        }
        path = normalizePath(path);
        int count = 0;
        int index = 0;
        while ((index = path.indexOf(Bookmarks.PATH_SEPARATOR, index)) != -1) {
            count++;
            index += Bookmarks.PATH_SEPARATOR.length();
        }
        return count;
    }    

    /**
     * Normalizes a path
     */
    public static String normalizePath(String path) {
        String[] segments = splitPath(path);
        
        if (segments.length == 0) {
            return "";
        }
        
        return String.join(Bookmarks.PATH_SEPARATOR, segments);
    }
    
    /**
     * Splits a path into segments
     */
    public static String[] splitPath(String path) {
        if (path == null || path.isEmpty()) {
            return new String[0];
        }
        
        String[] segments = path.split(Pattern.quote(Bookmarks.PATH_SEPARATOR));
        List<String> cleanSegments = new ArrayList<>();       
        for (String segment : segments) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                cleanSegments.add(trimmed);
            }
        }
        
        return cleanSegments.toArray(new String[0]);
    }

    /**
     * Joins path segments
     */
    public static String joinPath(String... segments) {
        if (segments == null || segments.length == 0) {
            return "";
        }
        
        String joined = String.join(Bookmarks.PATH_SEPARATOR, segments);
        
        return normalizePath(joined);
    }

    /**
     * Display path (changes the storage separator to the display separator)
     */
    public static String displayPath(String path) {
        return path.replace(Bookmarks.PATH_SEPARATOR, Bookmarks.PATH_SEPARATOR_DISPLAY_FORMATTED);
    }
}