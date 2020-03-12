package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
    }

    @Override
    public List<IItemBase> search(String luceneQuery) {

        List<IItemBase> items = new ArrayList<IItemBase>();
        try {
            if (iSource == null)
                iSource = new IPEDSource(caseFolder, iw);

            IPEDSearcher searcher = new IPEDSearcher(iSource, luceneQuery);
            searcher.setTreeQuery(true);
            searcher.setNoScoring(true);
            SearchResult result = searcher.search();

            for (int i = 0; i < result.getLength(); i++) {
                int id = result.getId(i);
                items.add(iSource.getItemByID(id));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return items;
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
