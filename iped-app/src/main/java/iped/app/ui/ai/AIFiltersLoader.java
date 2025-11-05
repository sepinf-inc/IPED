package iped.app.ui.ai;

import java.io.IOException;
import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import iped.app.ui.App;
import iped.engine.config.AIFiltersConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.SimpleFilterNode;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.LuceneSearchResult;
import iped.engine.search.SimpleNodeFilterSearch;

public class AIFiltersLoader {
    public static void load() {
        AIFiltersConfig config = ConfigurationManager.get().findObject(AIFiltersConfig.class);
        SimpleFilterNode root = (SimpleFilterNode) config.getRootAIFilter().clone();

        updateCount(App.get().appCase, root);
        removeEmptyTopLevel(root);
        expandDynamic(App.get().appCase, root);

        AIFiltersTreeModel model = new AIFiltersTreeModel(root);
        App.get().aiFiltersTree.setModel(model);
    }

    private static void removeEmptyTopLevel(SimpleFilterNode root) {
        for (int i = 0; i < root.getChildren().size(); i++) {
            SimpleFilterNode node = root.getChildren().get(i);
            if (node.getNumItems() <= 0) {
                root.getChildren().remove(i--);
            }
        }
    }

    private static void updateCount(IPEDSource source, SimpleFilterNode node) {
        Query query = SimpleNodeFilterSearch.getNodeQuery(source, node);
        if (query != null) {
            IPEDSearcher searcher = new IPEDSearcher(source, query);
            searcher.setNoScoring(true);
            int num = 0;
            try {
                if (source instanceof IPEDMultiSource) {
                    num = searcher.multiSearch().getLength();
                } else {
                    num = searcher.search().getLength();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            node.setNumItems(num);
        }
        for (SimpleFilterNode child : node.getChildren()) {
            updateCount(source, child);
        }
    }

    private static void expandDynamic(IPEDSource source, SimpleFilterNode node) {
        if (node.getDynamic()) {
            try {
                // Read distinct values and their frequency
                Query query = SimpleNodeFilterSearch.getNodeQuery(source, node);
                IPEDSearcher ipedSearcher = new IPEDSearcher(source, query);
                Map<String, Integer> values = new HashMap<>();
                LuceneSearchResult result = ipedSearcher.luceneSearch();
                int[] ids = result.getLuceneIds();
                IndexSearcher indexSearcher = source.getSearcher();
                String property = node.getProperty().replaceAll("\\\\", "");
                for (int id : ids) {
                    Document doc = indexSearcher.doc(id);
                    String[] val = doc.getValues(property);
                    for (String v : val) {
                        Integer cnt = values.get(v);
                        values.put(v, cnt == null ? 1 : cnt + 1);
                    }
                }

                // Create children
                for (String val : values.keySet()) {
                    SimpleFilterNode child = new SimpleFilterNode();
                    child.setDynamicChild(true);
                    child.setName(val.replace('_', ' '));
                    child.setValue(val);
                    child.setNumItems(values.get(val));
                    child.setParent(node);
                    node.getChildren().add(child);
                }

                // Sort children
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                Collections.sort(node.getChildren(), new Comparator<SimpleFilterNode>() {
                    public int compare(SimpleFilterNode a, SimpleFilterNode b) {
                        int cmp = Integer.compare(b.getNumItems(), a.getNumItems());
                        return cmp == 0 ? collator.compare(a.getName(), b.getName()) : cmp;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            for (SimpleFilterNode child : node.getChildren()) {
                expandDynamic(source, child);
            }
        }
    }
}
