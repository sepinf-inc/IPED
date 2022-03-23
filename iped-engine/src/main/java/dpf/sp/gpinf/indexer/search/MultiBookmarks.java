package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.KeyStroke;

import org.apache.commons.lang.ArrayUtils;

import dpf.sp.gpinf.indexer.util.Util;
import iped3.IIPEDSource;
import iped3.IItemId;
import iped3.search.IBookmarks;
import iped3.search.IMultiBookmarks;
import iped3.search.IMultiSearchResult;
import iped3.search.SelectionListener;

public class MultiBookmarks implements Serializable, IMultiBookmarks {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private transient List<SelectionListener> selectionListeners = new ArrayList<>();

    Map<Integer, IBookmarks> map = new HashMap<Integer, IBookmarks>();

    public MultiBookmarks(List<IPEDSource> cases) {
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

    public int getTotalItens() {
        int sum = 0;
        for (IBookmarks m : map.values())
            sum += m.getTotalItens();
        return sum;
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

    private static final int[] getLabelIds(IBookmarks m, Set<String> labelNames) {
        int[] labelIds = new int[labelNames.size()];
        int i = 0;
        boolean hasLabel = false;
        for (String labelName : labelNames) {
            labelIds[i] = m.getBookmarkId(labelName);
            if (labelIds[i++] != -1)
                hasLabel = true;
        }
        if (!hasLabel)
            return null;
        return labelIds;
    }

    public boolean hasBookmark(IItemId item, Set<String> labelNames) {
        IBookmarks m = map.get(item.getSourceId());
        int[] labelIds = getLabelIds(m, labelNames);
        return m.hasBookmark(item.getId(), m.getBookmarkBits(labelIds));
    }

    public final boolean hasLabel(ItemId item, String labelName) {
        IBookmarks m = map.get(item.getSourceId());
        int labelId = m.getBookmarkId(labelName);
        if (labelId == -1)
            return false;
        return m.hasBookmark(item.getId(), labelId);
    }

    public void addBookmark(List<IItemId> ids, String labelName) {
        HashMap<Integer, List<Integer>> itemsPerSource = getIdsPerSource(ids);
        for (Integer sourceId : itemsPerSource.keySet()) {
            IBookmarks m = map.get(sourceId);
            int labelId = m.getBookmarkId(labelName);
            if (labelId == -1)
                labelId = m.newBookmark(labelName);
            m.addBookmark(itemsPerSource.get(sourceId), labelId);
        }
    }

    private HashMap<Integer, List<Integer>> getIdsPerSource(List<IItemId> ids) {
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

    public void removeBookmark(List<IItemId> ids, String labelName) {
        HashMap<Integer, List<Integer>> itemsPerSource = getIdsPerSource(ids);
        for (Integer sourceId : itemsPerSource.keySet()) {
            IBookmarks m = map.get(sourceId);
            int labelId = m.getBookmarkId(labelName);
            if (labelId != -1)
                m.removeBookmark(itemsPerSource.get(sourceId), labelId);
        }

    }

    public void newBookmark(String labelName) {
        for (IBookmarks m : map.values())
            m.newBookmark(labelName);
    }

    public void delBookmark(String labelName) {
        for (IBookmarks m : map.values()) {
            int labelId = m.getBookmarkId(labelName);
            m.delBookmark(labelId);
        }
    }

    public void renameBookmark(String oldLabel, String newLabel) {
        for (IBookmarks m : map.values())
            m.renameBookmark(m.getBookmarkId(oldLabel), newLabel);
    }

    public void setBookmarkComment(String labelName, String comment) {
        for (IBookmarks m : map.values())
            m.setBookmarkComment(m.getBookmarkId(labelName), comment);
    }

    public String getBookmarkComment(String labelName) {
        for (IBookmarks m : map.values()) {
            String comm = m.getBookmarkComment(m.getBookmarkId(labelName));
            if (comm != null)
                return comm;
        }
        return null;
    }

    public void setBookmarkKeyStroke(String labelName, KeyStroke key) {
        for (IBookmarks m : map.values())
            m.setBookmarkKeyStroke(m.getBookmarkId(labelName), key);
    }

    public KeyStroke getBookmarkKeyStroke(String labelName) {
        for (IBookmarks m : map.values()) {
            KeyStroke key = m.getBookmarkKeyStroke(m.getBookmarkId(labelName));
            if (key != null)
                return key;
        }
        return null;
    }
    
    public int getBookmarkCount(String labelName) {
        int ret = 0;
        for (IBookmarks m : map.values()) {
            Integer labelId = m.getBookmarkId(labelName);
            if (labelId != null) {
                Integer cnt = m.getBookmarkCount(labelId);
                if (cnt != null) {
                    ret += cnt;
                }
            }
        }
        return ret;
    }
    
    public void setInReport(String labelName, boolean inReport) {
        for (IBookmarks m : map.values()) {
            int labelId = m.getBookmarkId(labelName);
            m.setInReport(labelId, inReport);
        }
    }

    public boolean isInReport(String labelName) {
        for (IBookmarks m : map.values()) {
            int labelId = m.getBookmarkId(labelName);
            if (m.isInReport(labelId))
                return true;
        }
        return false;
    }

    public TreeSet<String> getBookmarkSet() {
        TreeSet<String> labels = new TreeSet<String>();
        for (IBookmarks m : map.values())
            labels.addAll(m.getBookmarkMap().values());
        return labels;
    }

    public IMultiSearchResult filterBookmarks(IMultiSearchResult result, Set<String> labelNames) {
        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        HashMap<Integer, byte[]> labelBitsPerSource = new HashMap<Integer, byte[]>();
        for (IItemId item : result.getIterator()) {
            IBookmarks m = map.get(item.getSourceId());
            byte[] labelbits = labelBitsPerSource.get(item.getSourceId());
            if (labelbits == null) {
                int[] labelIds = getLabelIds(m, labelNames);
                if (labelIds != null)
                    labelbits = m.getBookmarkBits(labelIds);
                else
                    labelbits = new byte[0];
                labelBitsPerSource.put(item.getSourceId(), labelbits);
            }
            if (labelbits.length != 0 && m.hasBookmark(item.getId(), labelbits)) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public IMultiSearchResult filterBookmarksOrNoBookmarks(IMultiSearchResult result, Set<String> labelNames) {
        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        HashMap<Integer, byte[]> labelBitsPerSource = new HashMap<Integer, byte[]>();
        for (IItemId item : result.getIterator()) {
            IBookmarks m = map.get(item.getSourceId());
            byte[] labelbits = labelBitsPerSource.get(item.getSourceId());
            if (labelbits == null) {
                int[] labelIds = getLabelIds(m, labelNames);
                if (labelIds != null)
                    labelbits = m.getBookmarkBits(labelIds);
                else
                    labelbits = new byte[0];
                labelBitsPerSource.put(item.getSourceId(), labelbits);
            }
            if (!m.hasBookmark(item.getId()) || (labelbits.length != 0 && m.hasBookmark(item.getId(), labelbits))) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public IMultiSearchResult filterNoBookmarks(IMultiSearchResult result) {

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            if (!this.hasBookmark(item)) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

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
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public void loadState() {
        for (IBookmarks m : map.values())
            m.loadState();
    }

    public void loadState(File file) throws ClassNotFoundException, IOException {
        Object obj = Util.readObject(file.getAbsolutePath());
        if (obj instanceof IMultiBookmarks) {
            MultiBookmarks state = (MultiBookmarks) obj;
            if (state.getTotalItens() != this.getTotalItens())
                throw new IllegalArgumentException("Incompatible state file! It has different number of items."); //$NON-NLS-1$
            map = state.map;
        } else {
            IBookmarks m = (IBookmarks) obj;
            if (map.size() > 1 || m.getTotalItens() != this.getTotalItens())
                throw new IllegalArgumentException("Incompatible state file!"); //$NON-NLS-1$
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
    public boolean hasBookmark(IItemId item, String labelName) {
        HashSet<String> labelNames = new HashSet<String>();
        labelNames.add(labelName);
        return hasBookmark(item, labelNames);
    }

    @Override
    public void addSelectionListener(SelectionListener listener) {
        selectionListeners.add(listener);
    }

    @Override
    public void removeSelectionListener(SelectionListener listener) {
        selectionListeners.remove(listener);
    }

}
