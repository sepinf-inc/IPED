/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.KeyStroke;

import iped3.IItemId;

/**
 *
 * @author WERNECK
 * @author Nassif
 */
public interface IMultiBookmarks extends Serializable {

    void addBookmark(List<IItemId> ids, String bookmarkName);

    void addToTypedWords(String texto);

    void renameBookmark(String oldBookmark, String newBookmark);

    void clearChecked();

    void clearTypedWords();

    void delBookmark(String bookmarkName);

    IMultiSearchResult filterBookmarks(IMultiSearchResult result, Set<String> bookmarkNames);

    IMultiSearchResult filterChecked(IMultiSearchResult result);

    IMultiSearchResult filterBookmarksOrNoBookmarks(IMultiSearchResult result, Set<String> bookmarkNames);

    IMultiSearchResult filterNoBookmarks(IMultiSearchResult result);

    Set<String> getBookmarkSet();

    List<String> getBookmarkList(IItemId item);

    Collection<IBookmarks> getSingleBookmarks();

    int getTotalChecked();

    LinkedHashSet<String> getTypedWords();

    boolean hasBookmark(IItemId item);

    boolean hasBookmark(IItemId item, Set<String> bookmarkNames);

    boolean hasBookmark(IItemId item, String bookmarkName);

    boolean isChecked(IItemId item);

    void loadState();

    void loadState(File file) throws ClassNotFoundException, IOException;

    void newBookmark(String bookmarkName);

    void removeBookmark(List<IItemId> ids, String bookmarkName);

    void saveState();

    void saveState(boolean sync);

    void saveState(File file) throws IOException;

    void setBookmarkKeyStroke(String bookmarkName, KeyStroke key);

    KeyStroke getBookmarkKeyStroke(String bookmarkName);
    
    void checkAll();

    void setChecked(boolean value, IItemId item);

    public String getBookmarkComment(String bookmarkName);

    public int getBookmarkCount(String bookmarkName);

    void setBookmarkComment(String texto, String comment);

    boolean isInReport(String bookmark);

    void setInReport(String bookmark, boolean checked);

    void addSelectionListener(SelectionListener listener);

    void removeSelectionListener(SelectionListener listener);

}
