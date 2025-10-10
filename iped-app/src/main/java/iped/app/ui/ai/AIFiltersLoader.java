package iped.app.ui.ai;

import org.apache.lucene.search.Query;

import iped.app.ui.App;
import iped.engine.config.AIFiltersConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.data.IPEDMultiSource;
import iped.engine.data.IPEDSource;
import iped.engine.data.SimpleFilterNode;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.SimpleNodeFilterSearch;

public class AIFiltersLoader {
    public static void load() {
        AIFiltersConfig config = ConfigurationManager.get().findObject(AIFiltersConfig.class);
        SimpleFilterNode root = config.getRootAIFilter();

        updateAIFilterCount(App.get().appCase, root);
        removeEmptyTopLevelNodes(root);

        AIFiltersTreeModel model = new AIFiltersTreeModel(root);
        App.get().aiFiltersTree.setModel(model);
    }

    private static void removeEmptyTopLevelNodes(SimpleFilterNode root) {
        for (int i = 0; i < root.getChildren().size(); i++) {
            SimpleFilterNode node = root.getChildren().get(i);
            if (node.getNumItems() <= 0) {
                root.getChildren().remove(i--);
            }
        }
    }

    private static void updateAIFilterCount(IPEDSource source, SimpleFilterNode node) {
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
            updateAIFilterCount(source, child);
        }
    }

}
