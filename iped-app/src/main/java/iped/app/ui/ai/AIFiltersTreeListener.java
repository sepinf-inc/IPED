package iped.app.ui.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;

import iped.app.ui.App;
import iped.engine.data.SimpleFilterNode;
import iped.engine.search.SimpleNodeFilterSearch;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;

public class AIFiltersTreeListener implements TreeSelectionListener, TreeExpansionListener, IQueryFilterer {

    private BooleanQuery filterQuery;
    private final List<SimpleFilterNode> aiFiltersList = new ArrayList<SimpleFilterNode>();
    private final HashSet<TreePath> selection = new HashSet<TreePath>();
    private TreePath root;
    private long collapsed = 0;
    private boolean clearing = false;

    @Override
    public Query getQuery() {
        return filterQuery;
    }

    @Override
    public void valueChanged(TreeSelectionEvent evt) {

        if (root == null)
            root = new TreePath(App.get().aiFiltersTree.getModel().getRoot());

        if (System.currentTimeMillis() - collapsed < 100) {
            App.get().aiFiltersTree.setSelectionPaths(selection.toArray(new TreePath[0]));
            return;
        }

        for (TreePath path : evt.getPaths()) {
            if (selection.contains(path)) {
                selection.remove(path);
            } else {
                selection.add(path);
            }
        }

        if (selection.contains(root) || selection.isEmpty()) {
            App.get().setAIFiltersDefaultColor(true);
            aiFiltersList.clear();
            filterQuery = null;

        } else {
            App.get().setAIFiltersDefaultColor(false);

            aiFiltersList.clear();
            filterQuery = null;
            Builder builder = new Builder();
            aiFiltersList.clear();
            for (TreePath path : selection) {
                SimpleFilterNode node = (SimpleFilterNode) path.getLastPathComponent();
                Query query = getNodeQuery(node);
                if (query != null) {
                    builder.add(query, Occur.SHOULD);
                    aiFiltersList.add(node);
                }
            }
            if (!aiFiltersList.isEmpty()) {
                filterQuery = builder.build();
            }
        }
        if (!clearing) {
            App.get().appletListener.updateFileListing();
        }
    }

    @Override
    public void treeExpanded(TreeExpansionEvent event) {
    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
        collapsed = System.currentTimeMillis();

    }

    @Override
    public void clearFilter() {
        clearing = true;
        App.get().aiFiltersTree.clearSelection();
        aiFiltersList.clear();
        clearing = false;
    }

    public HashSet<TreePath> getSelection() {
        return selection;
    }

    @Override
    public List<IFilter> getDefinedFilters() {
        List<IFilter> result = new ArrayList<IFilter>();
        for (SimpleFilterNode node : aiFiltersList) {
            result.add(new IQueryFilter() {
                @Override
                public Query getQuery() {
                    return getNodeQuery(node);
                }

                public String toString() {
                    return AIFiltersLocalization.get(node);
                }
            });
        }
        return result;
    }

    @Override
    public boolean hasFiltersApplied() {
        return false;
    }

    @Override
    public String toString() {
        return "AI filters panel filterer";
    }

    @Override
    public boolean hasFilters() {
        return aiFiltersList.size() > 0;
    }

    private Query getNodeQuery(SimpleFilterNode node) {
        return SimpleNodeFilterSearch.getNodeQuery(App.get().appCase, node);
    }
}
