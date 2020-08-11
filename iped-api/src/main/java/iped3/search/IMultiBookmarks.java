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

import iped3.IItemId;

/**
 *
 * @author WERNECK
 */
public interface IMultiBookmarks extends Serializable {

    void addToBookmark(List<IItemId> items, String bookmarkName);

    void addToTypedWords(String texto);

    void renameBookmark(String oldName, String newName);

    void clearSelected();

    void clearTypedWords();

    void delBookmark(String bookmarkName);

    IMultiSearchResult filterBookmarks(IMultiSearchResult result, Set<String> bookmarkNames);

    IMultiSearchResult filterNoBookmarks(IMultiSearchResult result);

    IMultiSearchResult filterSelected(IMultiSearchResult result);

    IMultiSearchResult filterBookmarksOrNoBookmarks(IMultiSearchResult result, Set<String> bookmarkNames);

    Set<String> getAllBookmarks();

    List<String> getBookmarks(IItemId item);

    Collection<IBookmarks> getCasesBookmarks();

    int getTotalSelected();

    LinkedHashSet<String> getTypedWords();

    boolean hasBookmark(IItemId item);

    boolean hasBookmarks(IItemId item, Set<String> bookmarkNames);

    boolean isSelected(IItemId item);

    void loadState();

    void loadState(File file) throws ClassNotFoundException, IOException;

    void newBookmark(String bookmarkName);

    void removeFromBookmark(List<IItemId> ids, String labelName);

    void saveState();

    void saveState(File file) throws IOException;

    void selectAll();

    void setSelected(boolean selected, IItemId item);

    public String getComment(String bookmark);

    void setComment(String bookmark, String comment);

    boolean isInReport(String bookmark);

    void setInReport(String bookmark, boolean inReport);

    void addSelectionListener(SelectionListener listener);

    void removeSelectionListener(SelectionListener listener);

}
