/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author WERNECK
 */
public interface IBookmarks extends Serializable {

    @Deprecated
    void addLabel(List<Integer> ids, int label);

    void addToBookmark(List<Integer> itemIds, int bookmarkId);

    void addToTypedWords(String texto);

    @Deprecated
    void changeLabel(int labelId, String newLabel);

    void renameBookmark(int bookmarkId, String bookmarkName);

    void clearSelected();

    @Deprecated
    void delLabel(int label);

    void delBookmark(int bookmarkId);

    SearchResult filterBookmarks(SearchResult result, Set<String> bookmarks);

    SearchResult filterNoBookmarks(SearchResult result);

    SearchResult filterSelected(SearchResult result);

    SearchResult filterInReport(SearchResult result);

    File getIndexDir();

    @Deprecated
    byte[] getLabelBits(int[] labelids);

    byte[] getBookmarkBits(int[] bookmarkIds);

    @Deprecated
    int getLabelId(String labelName);

    int getBookmarkId(String bookmarkName);

    @Deprecated
    ArrayList<Integer> getLabelIds(int id);

    ArrayList<Integer> getBookmarkIds(int itemId);

    @Deprecated
    Map<Integer, String> getLabelMap();

    Map<Integer, String> getBookmarkMap();

    @Deprecated
    String getLabelName(int labelId);

    String getBookmarkName(int bookmarkId);

    @Deprecated
    public List<String> getLabelList(int itemId);

    public List<String> getBookmarkNames(int itemId);

    int getLastId();

    int getTotalItens();

    int getTotalSelected();

    LinkedHashSet<String> getTypedWords();

    @Deprecated
    boolean hasLabel(int id);

    boolean hasBookmark(int id);

    @Deprecated
    boolean hasLabel(int id, byte[] labelbits);

    boolean hasBookmarkBits(int itemId, byte[] bookmarkBits);

    @Deprecated
    boolean hasLabel(int id, int label);

    boolean hasBookmark(int itemId, int bookmarkId);

    boolean isSelected(int itemId);

    void loadState();

    void loadState(File file) throws IOException, ClassNotFoundException;

    @Deprecated
    int newLabel(String labelName);

    int newBookmark(String bookmarkName);

    @Deprecated
    void removeLabel(List<Integer> ids, int label);

    void removeFromBookmark(List<Integer> itemIds, int bookmarkId);

    void saveState();

    void saveState(File file) throws IOException;

    void selectAll();

    void setSelected(boolean value, int id);

    void updateCookie();

    @Deprecated
    void setLabelComment(int labelId, String comment);

    void setComment(int bookmarkId, String comment);

    @Deprecated
    String getLabelComment(int labelId);

    String getComment(int bookmarkId);

    void setInReport(int bookmarkId, boolean inReport);

    boolean isInReport(int bookmarkId);

}
