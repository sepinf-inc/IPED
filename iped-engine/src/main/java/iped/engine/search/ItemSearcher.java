package iped.engine.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.index.IndexWriter;

import iped.data.IItemReader;
import iped.engine.data.IPEDSource;
import iped.search.IItemSearcher;
import iped.search.SearchResult;

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
    public List<IItemReader> search(String luceneQuery) {

        List<IItemReader> items = new ArrayList<IItemReader>();
        for (IItemReader item : searchIterable(luceneQuery)) {
            items.add(item);
        }
        return items;
    }

    @Override
    public Iterable<IItemReader> searchIterable(String luceneQuery) {

        SearchResult result = getResult(luceneQuery);

        return new Iterable<IItemReader>() {
            @Override
            public Iterator<IItemReader> iterator() {
                return new Iterator<IItemReader>() {

                    int pos = 0;

                    @Override
                    public boolean hasNext() {
                        return pos < result.getLength();
                    }

                    @Override
                    public IItemReader next() {
                        return iSource.getItemByID(result.getId(pos++));
                    }

                };
            }
        };
    }

    private SearchResult getResult(String luceneQuery) {
        try {
            IPEDSearcher searcher = new IPEDSearcher(iSource, luceneQuery);
            searcher.setTreeQuery(true);
            searcher.setNoScoring(true);
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
        return QueryBuilder.escape(string);
    }

}
