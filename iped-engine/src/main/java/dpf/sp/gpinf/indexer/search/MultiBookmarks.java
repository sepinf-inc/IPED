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

    public int getTotalSelected() {
        int sum = 0;
        for (IBookmarks m : map.values())
            sum += m.getTotalSelected();
        return sum;
    }

    public int getTotalItens() {
        int sum = 0;
        for (IBookmarks m : map.values())
            sum += m.getTotalItens();
        return sum;
    }

    public void clearSelected() {
        for (IBookmarks m : map.values())
            m.clearSelected();
        for (SelectionListener l : selectionListeners)
            l.clearAll();
    }

    public void selectAll() {
        for (IBookmarks m : map.values())
            m.selectAll();
        for (SelectionListener l : selectionListeners)
            l.selectAll();
    }

    public boolean isSelected(IItemId item) {
        return map.get(item.getSourceId()).isSelected(item.getId());
    }

    public void setSelected(boolean value, IItemId item) {
        map.get(item.getSourceId()).setSelected(value, item.getId());
        for (SelectionListener l : selectionListeners)
            l.setSelected(item, value);
    }

    public List<String> getLabelList(IItemId item) {
        return map.get(item.getSourceId()).getBookmarkNames(item.getId());
    }

    public final boolean hasLabel(IItemId item) {
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

    public boolean hasLabel(IItemId item, Set<String> labelNames) {
        IBookmarks m = map.get(item.getSourceId());
        int[] labelIds = getLabelIds(m, labelNames);
        return m.hasBookmarkBits(item.getId(), m.getBookmarkBits(labelIds));
    }

    public final boolean hasLabel(ItemId item, String labelName) {
        IBookmarks m = map.get(item.getSourceId());
        int labelId = m.getBookmarkId(labelName);
        if (labelId == -1)
            return false;
        return m.hasBookmark(item.getId(), labelId);
    }

    public void addLabel(List<IItemId> ids, String labelName) {
        HashMap<Integer, List<Integer>> itemsPerSource = getIdsPerSource(ids);
        for (Integer sourceId : itemsPerSource.keySet()) {
            IBookmarks m = map.get(sourceId);
            int labelId = m.getBookmarkId(labelName);
            if (labelId == -1)
                labelId = m.newBookmark(labelName);
            m.addToBookmark(itemsPerSource.get(sourceId), labelId);
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

    public void removeLabel(List<IItemId> ids, String labelName) {
        HashMap<Integer, List<Integer>> itemsPerSource = getIdsPerSource(ids);
        for (Integer sourceId : itemsPerSource.keySet()) {
            IBookmarks m = map.get(sourceId);
            int labelId = m.getBookmarkId(labelName);
            if (labelId != -1)
                m.removeFromBookmark(itemsPerSource.get(sourceId), labelId);
        }

    }

    public void newLabel(String labelName) {
        for (IBookmarks m : map.values())
            m.newBookmark(labelName);
    }

    public void delLabel(String labelName) {
        for (IBookmarks m : map.values()) {
            int labelId = m.getBookmarkId(labelName);
            m.delBookmark(labelId);
        }
    }

    public void changeLabel(String oldLabel, String newLabel) {
        for (IBookmarks m : map.values())
            m.renameBookmark(m.getBookmarkId(oldLabel), newLabel);
    }

    public void setLabelComment(String labelName, String comment) {
        for (IBookmarks m : map.values())
            m.setComment(m.getBookmarkId(labelName), comment);
    }

    public String getLabelComment(String labelName) {
        for (IBookmarks m : map.values()) {
            String comm = m.getComment(m.getBookmarkId(labelName));
            if (comm != null)
                return comm;
        }
        return null;
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

    public TreeSet<String> getLabelMap() {
        TreeSet<String> labels = new TreeSet<String>();
        for (IBookmarks m : map.values())
            labels.addAll(m.getBookmarkMap().values());
        return labels;
    }

    public IMultiSearchResult filterBookmarks(IMultiSearchResult result, Set<String> labelNames) throws Exception {
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
            if (labelbits.length != 0 && m.hasBookmarkBits(item.getId(), labelbits)) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public IMultiSearchResult filterBookmarksOrNoBookmarks(IMultiSearchResult result, Set<String> labelNames)
            throws Exception {
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
            if (!m.hasBookmark(item.getId()) || (labelbits.length != 0 && m.hasBookmarkBits(item.getId(), labelbits))) {
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
            if (!this.hasLabel(item)) {
                selectedItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        MultiSearchResult r = new MultiSearchResult(selectedItems.toArray(new ItemId[0]),
                ArrayUtils.toPrimitive(scores.toArray(new Float[0])));

        return r;
    }

    public IMultiSearchResult filterSelected(IMultiSearchResult result) throws Exception {

        ArrayList<IItemId> selectedItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            if (map.get(item.getSourceId()).isSelected(item.getId())) {
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

    public void saveState() {
        for (IBookmarks m : map.values())
            m.saveState();
    }

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
    public boolean hasLabel(IItemId item, String labelName) {
        HashSet<String> labelNames = new HashSet<String>();
        labelNames.add(labelName);
        return hasLabel(item, labelNames);
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
