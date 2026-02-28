package iped.app.ui;

import java.awt.Color;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import javax.swing.KeyStroke;

import iped.engine.data.Bookmarks;

/**
 * Represents a node in the hierarchical bookmark tree.
 * All nodes (including intermediate ones) are regular bookmarks.
 */
public class BookmarkNode implements Comparable<BookmarkNode> {
    // Tree node properties
    private String name;
    private BookmarkNode parent;
    private Map<String, BookmarkNode> children;    
    // Bookmark properties
    private Color color;
    private String comment;
    private int count;
    private int subtreeCount;
    private KeyStroke keyStroke;
    
    /**
     * Creates a root node
     */
    public BookmarkNode() {
        this.name = "";
        this.parent = null;
        this.children = new TreeMap<>(Collator.getInstance());
    }
    
    /**
     * Creates a node with a name and parent
     */
    public BookmarkNode(String name, BookmarkNode parent) {
        this.name = name;
        this.parent = parent;
        this.children = new TreeMap<>(Collator.getInstance());
    }
    
    /**
     * Adds a child node
     */
    public void addChild(BookmarkNode child) {
        children.put(child.getName(), child);
    }
    
    /**
     * Removes a child node
     */
    public BookmarkNode removeChild(String name) {
        return children.remove(name);
    }
    
    /**
     * Gets a child by name
     */
    public BookmarkNode getChild(String name) {
        return children.get(name);
    }
    
    /**
     * Gets all children sorted by name
     */
    public List<BookmarkNode> getChildren() {
        return new ArrayList<>(children.values());
    }
    
    /**
     * Checks if this node has children
     */
    public boolean hasChildren() {
        return !children.isEmpty();
    }
    
    /**
     * Gets the number of children
     */
    public int getChildCount() {
        return children.size();
    }
    
    /**
     * Checks if this is a leaf node (no children)
     */
    public boolean isLeaf() {
        return children.isEmpty();
    }
    
    // Getters and setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getFullPath() {
        if (parent == null || parent.isRoot()) {
            return name;
        }
        return parent.getFullPath() + Bookmarks.PATH_SEPARATOR + name;
    }
    
    public BookmarkNode getParent() {
        return parent;
    }
    
    public void setParent(BookmarkNode parent) {
        this.parent = parent;
        parent.addChild(this);
    }
    
    public boolean isRoot() {
        return parent == null;
    }
    
    public Color getColor() {
        return color;
    }
    
    public void setColor(Color color) {
        this.color = color;
    }
    
    public String getComment() {
        return comment;
    }
    
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    public int getSubtreeCount() {
        return subtreeCount;
    }
    
    /**
     * Recalculates subtree count from scratch (call after tree structure changes)
     */
    public void recalculateSubtreeCount() {
        subtreeCount = count;
        for (BookmarkNode child : children.values()) {
            child.recalculateSubtreeCount();
            subtreeCount += child.getSubtreeCount();
        }
    }
    
    public KeyStroke getKeyStroke() {
        return keyStroke;
    }
    
    public void setKeyStroke(KeyStroke keyStroke) {
        this.keyStroke = keyStroke;
    }
    
    /**
     * Gets the depth of this node (root = 0)
     */
    public int getDepth() {
        int depth = 0;
        BookmarkNode current = this.parent;
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }
    
    /**
     * Gets all descendants, including this node (recursive)
     */
    public List<BookmarkNode> getAllDescendants() {
        List<BookmarkNode> descendants = new ArrayList<>();
        descendants.add(this);
        for (BookmarkNode child : children.values()) {
            descendants.addAll(child.getAllDescendants());
        }
        return descendants;
    }
    
    /**
     * Gets only this node if leaf, or all leaf descendants if has children
     */
    public List<BookmarkNode> getEffectiveBookmarks() {
        if (isLeaf()) {
            return Collections.singletonList(this);
        }
        
        List<BookmarkNode> bookmarks = new ArrayList<>();
        for (BookmarkNode child : children.values()) {
            bookmarks.addAll(child.getEffectiveBookmarks());
        }
        return bookmarks;
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    @Override
    public int compareTo(BookmarkNode other) {
        return Collator.getInstance().compare(this.name, other.name);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BookmarkNode)) return false;
        BookmarkNode other = (BookmarkNode) obj;
        return Objects.equals(getFullPath(), other.getFullPath());
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(getFullPath());
    }
}