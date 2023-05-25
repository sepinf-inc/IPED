package iped.engine.search;

import java.util.BitSet;
import java.util.Iterator;

import iped.data.IIPEDSource;
import iped.data.IItemId;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.ItemId;
import iped.search.IMultiSearchResult;

public class MultiSearchResult implements IMultiSearchResult {

    private IItemId[] ids;
    private float[] scores;
    IPEDSearcher ipedSearcher;
    IIPEDSource ipedSource;
    BitSet docids;

    public MultiSearchResult() {
        this.ids = new ItemId[0];
        this.scores = new float[0];
    }

    public MultiSearchResult(IItemId[] ids, float[] scores) {
        this.ids = ids;
        this.scores = scores;
    }

    public MultiSearchResult(IIPEDSource ipedSource, IItemId[] ids, float[] scores) {
        this.ids = ids;
        this.scores = scores;
    }

    public final int getLength() {
        return ids.length;
    }

    public final IItemId getItem(int i) {
        return ids[i];
    }

    public final float getScore(int i) {
        return scores[i];
    }

    public final void setScore(int i, float score) {
        scores[i] = score;
    }

    public final void setItem(int i, IItemId itemId) {
        ids[i] = itemId;
    }

    public Iterable<IItemId> getIterator() {
        return new ItemIdIterator();
    }

    public class ItemIdIterator implements Iterable<IItemId>, Iterator<IItemId> {

        private int pos = 0;

        @Override
        public final Iterator<IItemId> iterator() {
            return this;
        }

        @Override
        public final boolean hasNext() {
            return pos < ids.length;
        }

        @Override
        public final IItemId next() {
            return ids[pos++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not allowed"); //$NON-NLS-1$
        }

        public int getPos() {
            return pos;
        }

        public void setPos(int pos) {
            this.pos = pos;
        }
    }

    public static MultiSearchResult get(IPEDMultiSource iSource, LuceneSearchResult luceneResult) {

        // System.out.println("multi Result");

        MultiSearchResult result = new MultiSearchResult();
        result.scores = luceneResult.getScores();
        result.ids = new ItemId[luceneResult.getLength()];

        if (luceneResult.getLength() <= IPEDSearcher.MAX_SIZE_TO_SCORE) {
            int[] docs = luceneResult.getLuceneIds();
            for (int i = 0; i < docs.length; i++) {
                result.ids[i] = iSource.getItemId(docs[i]);
            }

            // Otimização: considera que itens estão em ordem crescente do LuceneId (qdo não
            // usa scores)
        } else {
            IIPEDSource atomicSource = null;
            int baseDoc = 0;
            int sourceId = 0;
            int maxdoc = 0;
            int[] docs = luceneResult.getLuceneIds();
            for (int i = 0; i < docs.length; i++) {
                if (atomicSource == null || docs[i] >= baseDoc + maxdoc) {
                    atomicSource = iSource.getAtomicSource(docs[i]);
                    sourceId = atomicSource.getSourceId();
                    baseDoc = iSource.getBaseLuceneId(atomicSource);
                    maxdoc = atomicSource.getReader().maxDoc();
                }
                result.ids[i] = new ItemId(sourceId, atomicSource.getId(docs[i] - baseDoc));
            }
        }

        return result;
    }

    public static LuceneSearchResult get(IMultiSearchResult ipedResult, IPEDMultiSource iSource) {
        LuceneSearchResult lResult = new LuceneSearchResult(ipedResult.getLength());
        float[] scores = lResult.getScores();
        int[] docs = lResult.getLuceneIds();

        int i = 0;
        if (ipedResult.getLength() <= IPEDSearcher.MAX_SIZE_TO_SCORE) {
            for (IItemId item : ipedResult.getIterator()) {
                scores[i] = ipedResult.getScore(i);
                docs[i] = iSource.getLuceneId(item);
                i++;
            }

            // Otimização: considera que itens estão em ordem crescente do LuceneId (qdo não
            // usa scores)
        } else {
            IIPEDSource atomicSource = null;
            int baseDoc = 0;
            int sourceId = 0;
            for (IItemId item : ipedResult.getIterator()) {
                if (atomicSource == null || item.getSourceId() != sourceId) {
                    sourceId = item.getSourceId();
                    atomicSource = iSource.getAtomicSourceBySourceId(sourceId);
                    baseDoc = iSource.getBaseLuceneId(atomicSource);
                }
                docs[i] = atomicSource.getLuceneId(item.getId()) + baseDoc;
                scores[i] = ipedResult.getScore(i);
                i++;
            }
        }

        return lResult;
    }

    @Override
    public MultiSearchResult clone() {
        MultiSearchResult result = new MultiSearchResult();
        result.ids = this.ids.clone();
        result.scores = this.scores.clone();
        return result;
    }

    public boolean hasDocId(int docId) {
        return docids.get(docId);
    }

    public IPEDSearcher getIpedSearcher() {
        return ipedSearcher;
    }

    public void setIpedSearcher(IPEDSearcher ipedSearcher) {
        this.ipedSearcher = ipedSearcher;
    }

    @Override
    public IIPEDSource getIPEDSource() {
        return ipedSource;
    }

    public void setIPEDSource(IIPEDSource ipedSource) {
        if (this.ipedSource == null || this.docids == null) {
            this.ipedSource = ipedSource;
            this.docids = new BitSet(ids.length);
            for (int i = 0; i < ids.length; i++) {
                docids.set(ipedSource.getLuceneId(ids[i]));
            }
        }
    }
}
