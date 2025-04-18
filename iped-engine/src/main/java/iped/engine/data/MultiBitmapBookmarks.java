package iped.engine.data;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.swing.KeyStroke;

import org.apache.commons.lang.ArrayUtils;
import org.roaringbitmap.RoaringBitmap;

import iped.data.IBookmarks;
import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.data.IMultiBookmarks;
import iped.data.SelectionListener;
import iped.engine.search.MultiSearchResult;
import iped.engine.util.Util;
import iped.search.IMultiSearchResult;

public class MultiBitmapBookmarks implements Serializable, IMultiBookmarks {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    // cache made of soft reference to bookmarks hashcodes
    private transient HashMap<String, SoftReference<RoaringBitmap[]>> bsetCache = new HashMap<>();
    private transient SoftReference<RoaringBitmap[]> unionsCache = new SoftReference<RoaringBitmap[]>(null);
    private transient List<SelectionListener> selectionListeners = new ArrayList<>();

    Map<Integer, IBookmarks> map = new HashMap<Integer, IBookmarks>();

    public MultiBitmapBookmarks(List<IPEDSource> cases) {
        for (IIPEDSource s : cases)
            map.put(s.getSourceId(), s.getBookmarks());
    }

    public Collection<IBookmarks> getSingleBookmarks() {
        return map.values();
    }

    public int getTotalChecked() {
        int sum = 0;
        for (IBookmarks m : map.values())
            sum += m.getTotalChecked();
        return sum;
    }

    public int getLastId() {
        int lastId = 0;
        for (IBookmarks m : map.values())
            lastId = Math.max(lastId, m.getLastId());
        return lastId;
    }

    public void clearChecked() {
        for (IBookmarks m : map.values())
            m.clearSelected();
        for (SelectionListener l : selectionListeners)
            l.clearAll();
    }

    public void checkAll() {
        for (IBookmarks m : map.values())
            m.checkAll();
        for (SelectionListener l : selectionListeners)
            l.selectAll();
    }

    public boolean isChecked(IItemId item) {
        return map.get(item.getSourceId()).isChecked(item.getId());
    }

    public void setChecked(boolean value, IItemId item) {
        map.get(item.getSourceId()).setChecked(value, item.getId());
        for (SelectionListener l : selectionListeners)
            l.setSelected(item, value);
    }

    public List<String> getBookmarkList(IItemId item) {
        return map.get(item.getSourceId()).getBookmarkList(item.getId());
    }

    public final boolean hasBookmark(IItemId item) {
        return map.get(item.getSourceId()).hasBookmark(item.getId());
    }

    private static final int[] getBookmarkIds(IBookmarks m, Set<String> bookmarkNames) {
        int[] bookmarkIds = new int[bookmarkNames.size()];
        int i = 0;
        boolean hasBookmark = false;
        for (String bookmarkName : bookmarkNames) {
            bookmarkIds[i] = m.getBookmarkId(bookmarkName);
            if (bookmarkIds[i++] != -1)
                hasBookmark = true;
        }
        if (!hasBookmark)
            return null;
        return bookmarkIds;
    }

    public boolean hasBookmark(IItemId item, Set<String> bookmarkNames) {
        RoaringBitmap r = getBookmarksUnions(bookmarkNames)[item.getSourceId()];
        return r.contains(item.getId());
    }

    public final boolean hasBookmark(ItemId item, String bookmarkName) {
        IBookmarks m = map.get(item.getSourceId());
        int bookmarkId = m.getBookmarkId(bookmarkName);
        if (bookmarkId == -1)
            return false;
        return m.hasBookmark(item.getId(), bookmarkId);
    }

    @Deprecated
    public void addBookmark(List<IItemId> ids, String bookmarkName) {
        ItemIdSet set = new ItemIdSet();
        set.addAll(ids);
        addBookmark(set, bookmarkName);
    }

    public void addBookmark(Set<IItemId> ids, String bookmarkName) {
        HashMap<Integer, List<Integer>> itemsPerSource = getIdsPerSource(ids);
        for (Integer sourceId : itemsPerSource.keySet()) {
            IBookmarks m = map.get(sourceId);
            int bookmarkId = m.getBookmarkId(bookmarkName);
            if (bookmarkId == -1)
                bookmarkId = m.newBookmark(bookmarkName);
            m.addBookmark(itemsPerSource.get(sourceId), bookmarkId);
        }
        invalidadeCaches();
    }

    private void invalidadeCaches() {
        unionsCache = null;
        bsetCache.clear();
    }

    private HashMap<Integer, List<Integer>> getIdsPerSource(Set<IItemId> ids) {
        HashMap<Integer, List<Integer>> itemsPerSource = new HashMap<>();
        for (IItemId item : ids) {
            List<Integer> items = itemsPerSource.get(item.getSourceId());
            if (items == null) {
                items = new ArrayList<Integer>();
                itemsPerSource.put(item.getSourceId(), items);
            }
            items.add(item.getId());
        }
        return itemsPerSource;
    }

    @Deprecated
    public void removeBookmark(List<IItemId> ids, String bookmarkName) {
        ItemIdSet set = new ItemIdSet();
        set.addAll(ids);
        removeBookmark(set, bookmarkName);
    }

    public void removeBookmark(Set<IItemId> ids, String bookmarkName) {
        HashMap<Integer, List<Integer>> itemsPerSource = getIdsPerSource(ids);
        for (Integer sourceId : itemsPerSource.keySet()) {
            IBookmarks m = map.get(sourceId);
            int bookmarkId = m.getBookmarkId(bookmarkName);
            if (bookmarkId != -1)
                m.removeBookmark(itemsPerSource.get(sourceId), bookmarkId);
        }
        invalidadeCaches();
    }

    public void newBookmark(String bookmarkName) {
        for (IBookmarks m : map.values())
            m.newBookmark(bookmarkName);
        invalidadeCaches();
    }

    public void delBookmark(String bookmarkName) {
        for (IBookmarks m : map.values()) {
            int bookmarkId = m.getBookmarkId(bookmarkName);
            m.delBookmark(bookmarkId);
        }
        invalidadeCaches();
    }

    public void renameBookmark(String oldBookmark, String newBookmark) {
        for (IBookmarks m : map.values())
            m.renameBookmark(m.getBookmarkId(oldBookmark), newBookmark);
    }

    public void setBookmarkComment(String bookmarkName, String comment) {
        for (IBookmarks m : map.values())
            m.setBookmarkComment(m.getBookmarkId(bookmarkName), comment);
    }

    public String getBookmarkComment(String bookmarkName) {
        for (IBookmarks m : map.values()) {
            String comm = m.getBookmarkComment(m.getBookmarkId(bookmarkName));
            if (comm != null)
                return comm;
        }
        return null;
    }

    public void setBookmarkKeyStroke(String bookmarkName, KeyStroke key) {
        for (IBookmarks m : map.values())
            m.setBookmarkKeyStroke(m.getBookmarkId(bookmarkName), key);
    }

    public KeyStroke getBookmarkKeyStroke(String bookmarkName) {
        for (IBookmarks m : map.values()) {
            KeyStroke key = m.getBookmarkKeyStroke(m.getBookmarkId(bookmarkName));
            if (key != null)
                return key;
        }
        return null;
    }

    public int getBookmarkCount(String bookmarkName) {
        int ret = 0;
        for (IBookmarks m : map.values()) {
            int bookmarkId = m.getBookmarkId(bookmarkName);
            if (bookmarkId != -1) {
                ret += m.getBookmarkCount(bookmarkId);
            }
        }
        return ret;
    }

    public void setInReport(String bookmarkName, boolean inReport) {
        for (IBookmarks m : map.values()) {
            int bookmarkId = m.getBookmarkId(bookmarkName);
            m.setInReport(bookmarkId, inReport);
        }
    }

    public boolean isInReport(String bookmarkName) {
        for (IBookmarks m : map.values()) {
            int bookmarkId = m.getBookmarkId(bookmarkName);
            if (m.isInReport(bookmarkId))
                return true;
        }
        return false;
    }

    public TreeSet<String> getBookmarkSet() {
        TreeSet<String> bookmarks = new TreeSet<String>();
        for (IBookmarks m : map.values())
            bookmarks.addAll(m.getBookmarkMap().values());
        return bookmarks;
    }

    /**
     * return union of RoaringBitmap arrays with itemIds for each case of the
     * bookmarks in parameter
     */
    public RoaringBitmap[] getBookmarksUnions(Set<String> bookmarkNames) {
        String key = bookmarkNames.stream().sorted().collect(Collectors.joining("|"));
        SoftReference<RoaringBitmap[]> sbitmap = bsetCache.get(key);
        RoaringBitmap[] unions = null;
        if (sbitmap != null) {
            unions = sbitmap.get();
        }
        if (unions == null) {
            unions = new RoaringBitmap[map.size()];

            for (Entry<Integer, IBookmarks> e : map.entrySet()) {
                IBookmarks bookmarks = (IBookmarks) e.getValue();
                if (bookmarks instanceof BitmapBookmarks) {
                    RoaringBitmap lunion = ((BitmapBookmarks) e.getValue()).getBookmarksUnion(bookmarkNames);
                    unions[e.getKey()] = lunion;
                }
            }
            bsetCache.put(key, new SoftReference<RoaringBitmap[]>(unions));
        }

        unions = unions.clone();
        for (int i = 0; i < unions.length; i++) {
            unions[i] = unions[i].clone();
        }

        return unions;
    }

    /**
     * return union of RoaringBitmap arrays with itemIds for each case
     */
    public RoaringBitmap[] getBookmarksUnions() {
        RoaringBitmap[] unions = unionsCache != null ? unionsCache.get() : null;
        if (unions == null) {
            unions = new RoaringBitmap[map.size()];

            for (Entry<Integer, IBookmarks> e : map.entrySet()) {
                IBookmarks bookmarks = (IBookmarks) e.getValue();
                if (bookmarks instanceof BitmapBookmarks) {
                    RoaringBitmap lunion = ((BitmapBookmarks) e.getValue()).getBookmarksUnion();
                    unions[e.getKey()] = lunion;
                }
            }
            unionsCache = new SoftReference<RoaringBitmap[]>(unions);
        }
        unions = unions.clone();
        for (int i = 0; i < unions.length; i++) {
            unions[i] = unions[i].clone();
        }

        return unions;
    }

    public IMultiSearchResult filterBookmarks(IMultiSearchResult result, Set<String> bookmarkNames) {
        RoaringBitmap[] unions = getBookmarksUnions(bookmarkNames);
        RoaringBitmap uniqueUnion = null;
        if (map.size() == 1) {
            uniqueUnion = unions[0];
        }

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            RoaringBitmap union = null;
            if (map.size() > 1) {
                union = unions[item.getSourceId()];
            } else {
                union = uniqueUnion;
            }
            if (union.contains(item.getId())) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public IMultiSearchResult filterBookmarksOrNoBookmarks(IMultiSearchResult result, Set<String> bookmarkNames) {
        HashMap<Integer, RoaringBitmap> unions = new HashMap<Integer, RoaringBitmap>();

        for (Entry<Integer, IBookmarks> e : map.entrySet()) {
            IBookmarks bookmarks = (IBookmarks) e.getValue();
            if (bookmarks instanceof BitmapBookmarks) {
                unions.put(e.getKey(), ((BitmapBookmarks) bookmarks).getBookmarksOrNoBookmarksUnion(bookmarkNames));
            }
        }

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            RoaringBitmap union = unions.get(item.getSourceId());
            if (union.contains(item.getId())) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public IMultiSearchResult filterNoBookmarks(IMultiSearchResult result) {

        HashMap<Integer, RoaringBitmap> unions = new HashMap<Integer, RoaringBitmap>();

        for (Entry<Integer, IBookmarks> e : map.entrySet()) {
            IBookmarks bookmarks = (IBookmarks) e.getValue();
            if (bookmarks instanceof BitmapBookmarks) {
                unions.put(e.getKey(), ((BitmapBookmarks) bookmarks).getBookmarksUnion());
            }
        }

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            RoaringBitmap union = unions.get(item.getSourceId());
            if (!union.contains(item.getId())) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public IMultiSearchResult filterChecked(IMultiSearchResult result) {

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            if (map.get(item.getSourceId()).isChecked(item.getId())) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public void loadState() {
        for (IBookmarks m : map.values())
            m.loadState();
    }

    public void loadState(File file) throws ClassNotFoundException, IOException {
        Object obj = Util.readObject(file.getAbsolutePath());
        if (obj instanceof MultiBitmapBookmarks) {
            MultiBitmapBookmarks state = (MultiBitmapBookmarks) obj;
            if (state.getLastId() != this.getLastId())
                throw new IllegalArgumentException("Incompatible state file!");
            map = state.map;
        } else if (obj instanceof MultiBookmarks) {
            MultiBookmarks state = (MultiBookmarks) obj;
            if (state.getLastId() != this.getLastId())
                throw new IllegalArgumentException("Incompatible state file!");
            map = state.map;
        } else if (obj instanceof IBookmarks) {
            IBookmarks m = (IBookmarks) obj;
            if (map.size() > 1 || m.getLastId() != this.getLastId())
                throw new IllegalArgumentException("Incompatible state file!");
            map.put(map.keySet().iterator().next(), m);
        }
        for (IBookmarks marcador : this.map.values())
            marcador.updateCookie();
    }

    @Override
    public void saveState() {
        saveState(false);
    }

    @Override
    public void saveState(boolean sync) {
        for (IBookmarks m : map.values())
            m.saveState(sync);
    }

    @Override
    public void saveState(File file) throws IOException {
        Util.writeObject(this, file.getAbsolutePath());
    }

    public LinkedHashSet<String> getTypedWords() {
        LinkedHashSet<String> searches = new LinkedHashSet<String>();
        for (IBookmarks m : map.values())
            for (String s : m.getTypedWords())
                if (!searches.contains(s))
                    searches.add(s);
        return searches;
    }

    public void clearTypedWords() {
        for (IBookmarks m : map.values())
            m.getTypedWords().clear();
    }

    public void addToTypedWords(String texto) {
        for (IBookmarks m : map.values())
            m.addToTypedWords(texto);
    }

    @Override
    public boolean hasBookmark(IItemId item, String bookmarkName) {
        HashSet<String> bookmarkNames = new HashSet<String>();
        bookmarkNames.add(bookmarkName);
        return hasBookmark(item, bookmarkNames);
    }

    @Override
    public void addSelectionListener(SelectionListener listener) {
        selectionListeners.add(listener);
    }

    @Override
    public void removeSelectionListener(SelectionListener listener) {
        selectionListeners.remove(listener);
    }

    public void setBookmarkColor(String bookmarkName, Color color) {
        for (IBookmarks m : map.values()) {
            int bid = m.getBookmarkId(bookmarkName);
            if (bid != -1) {
                m.setBookmarkColor(bid, color);
            }
        }

    }

    public Color getBookmarkColor(String bookmarkName) {
        for (IBookmarks m : map.values()) {
            int bid = m.getBookmarkId(bookmarkName);
            if (bid != -1) {
                Color color = m.getBookmarkColor(bid);
                if (color != null)
                    return color;
            }
        }
        return null;
    }

    public Set<Color> getUsedColors() {
        Set<Color> usedColors = new HashSet<Color>();
        for (IBookmarks m : map.values()) {
            usedColors.addAll(m.getUsedColors());
        }
        return usedColors;
    }

    @Override
    public void removeBookmarkKeyStroke(String bookmarkName) {
        for (IBookmarks m : map.values())
            m.removeBookmarkKeyStroke(m.getBookmarkId(bookmarkName));
    }
}
