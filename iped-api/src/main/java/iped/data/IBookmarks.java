/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped.data;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.KeyStroke;

import iped.search.SearchResult;

/**
 *
 * @author WERNECK
 * @author Nassif
 */
public interface IBookmarks extends Serializable {

    void addBookmark(List<Integer> ids, int bookmark);

    void addToTypedWords(String texto);

    void renameBookmark(int bookmarkId, String newName);

    void clearSelected();

    void delBookmark(int bookmark);

    File getIndexDir();

    byte[] getBookmarkBits(int[] bookmarkIds);

    int getBookmarkId(String bookmarkName);

    ArrayList<Integer> getBookmarkIds(int id);

    Map<Integer, String> getBookmarkMap();

    String getBookmarkName(int bookmarkId);

    public List<String> getBookmarkList(int itemId);

    int getLastId();

    int getTotalItens();

    int getTotalChecked();

    LinkedHashSet<String> getTypedWords();

    boolean hasBookmark(int id);

    boolean hasBookmark(int id, byte[] bookmarkbits);

    boolean hasBookmark(int id, int bookmark);

    boolean isChecked(int id);

    void loadState();

    void loadState(File file) throws IOException, ClassNotFoundException;

    int newBookmark(String bookmarkName);

    void removeBookmark(List<Integer> ids, int bookmark);

    void saveState();

    void saveState(boolean sync);

    void saveState(File file) throws IOException;

    void saveState(File file, boolean sync) throws IOException;

    void checkAll();

    void setChecked(boolean value, int id);

    void updateCookie();

    void setBookmarkComment(int bookmarkId, String comment);

    String getBookmarkComment(int bookmarkId);

    void setBookmarkColor(int bookmarkId, Color color);

    Color getBookmarkColor(int bookmarkId);
    
    void setBookmarkKeyStroke(int bookmarkId, KeyStroke key);

    KeyStroke getBookmarkKeyStroke(int bookmarkId);
    
    int getBookmarkCount(int bookmarkId);
    
    void setInReport(int bookmarkId, boolean inReport);

    boolean isInReport(int bookmarkId);

    SearchResult filterBookmarks(SearchResult result, Set<String> bookmarkNames);

    SearchResult filterChecked(SearchResult result);

    SearchResult filterBookmarksOrNoBookmarks(SearchResult result, Set<String> bookmarkNames);

    SearchResult filterNoBookmarks(SearchResult result);

    SearchResult filterInReport(SearchResult result);

}
