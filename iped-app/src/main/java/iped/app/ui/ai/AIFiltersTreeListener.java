package iped.app.ui.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import iped.app.ui.App;
import iped.engine.data.SimpleFilterNode;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;

public class AIFiltersTreeListener implements TreeSelectionListener, TreeExpansionListener, IQueryFilterer {

    private BooleanQuery query;
    private List<SimpleFilterNode> aiFiltersList = new ArrayList<SimpleFilterNode>();
    private HashSet<TreePath> selection = new HashSet<TreePath>();
    private TreePath root;
    private long collapsed = 0;
    private boolean clearing = false;

    public Query getQuery() {
        return query;
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
            query = null;

        } else {
            App.get().setAIFiltersDefaultColor(false);

            aiFiltersList.clear();
            query = null;            
            /* TODO
            Builder builder = new Builder();
            aiFiltersList.clear();
            for (TreePath path : selection) {
                FilterNode aif = (FilterNode) path.getLastPathComponent();
                addAIFilterToQuery(aif, builder);
            }
            query = builder.build();
            */
        }

        if (!clearing)
            App.get().appletListener.updateFileListing();

    }

    private void addAIFilterToQuery(SimpleFilterNode aif, Builder builder) {
        builder.add(new TermQuery(new Term(aif.getProperty(), aif.getValue())), Occur.SHOULD);
        aiFiltersList.add(aif);
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
        for (SimpleFilterNode aif : aiFiltersList) {
            result.add(new IQueryFilter() {
                @Override
                public Query getQuery() {
                    StringBuffer queryStr = new StringBuffer();
                    queryStr.append(aif.getProperty());
                    queryStr.append(" :\"");
                    queryStr.append(aif.getValue());
                    queryStr.append("\"");

                    Query query;
                    try {
                        query = new QueryBuilder(App.get().appCase).getQuery(queryStr.toString());
                        return query;
                    } catch (ParseException | QueryNodeException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                public String toString() {
                    return IndexItem.normalize(aif.getName(), true);
                }
            });
        }
        return result;
    }

    @Override
    public boolean hasFiltersApplied() {
        return false;
    }

    public String toString() {
        return "AI filters panel filterer";
    }

    @Override
    public boolean hasFilters() {
        return aiFiltersList.size() > 0;
    }
}
