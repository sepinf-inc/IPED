package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.search.SearchResult;

public class ItemSearcher implements IItemSearcher {

    File caseFolder;
    IndexWriter iw;
    IPEDSource iSource;

    public ItemSearcher(IPEDSource iSource) {
        this.iSource = iSource;
    }

    public ItemSearcher(File caseFolder, IndexWriter iw) {
        this.caseFolder = caseFolder;
        this.iw = iw;
        this.iSource = new IPEDSource(caseFolder, iw);
    }

    @Override
    public List<IItemBase> search(String luceneQuery) {

        List<IItemBase> items = new ArrayList<IItemBase>();
        for (IItemBase item : searchIterable(luceneQuery)) {
            items.add(item);
        }
        return items;
    }

    @Override
    public Iterable<IItemBase> searchIterable(String luceneQuery) {

        SearchResult result = getResult(luceneQuery);

        return new Iterable<IItemBase>() {
            @Override
            public Iterator<IItemBase> iterator() {
                return new Iterator<IItemBase>() {

                    int pos = 0;

                    @Override
                    public boolean hasNext() {
                        return pos < result.getLength();
                    }

                    @Override
                    public IItemBase next() {
                        return iSource.getItemByID(result.getId(pos++));
                    }

                };
            }
        };
    }

    private SearchResult getResult(String luceneQuery) {
        IPEDSearcher searcher = new IPEDSearcher(iSource, luceneQuery);
        searcher.setTreeQuery(true);
        searcher.setNoScoring(true);
        try {
            return searcher.search();

        } catch (Exception e) {
            e.printStackTrace();
            return new SearchResult(new int[0], new float[0]);
        }
    }

    @Override
    public void close() throws IOException {
        if (iSource != null)
            iSource.close();
    }

    @Override
    public String escapeQuery(String string) {
        return QueryParserUtil.escape(string);
    }

}
