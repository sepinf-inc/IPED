package iped.app.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

import iped.engine.data.Category;
import iped.engine.search.QueryBuilder;
import iped.engine.task.index.IndexItem;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.viewers.api.IFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;

public class CategoryTreeListener implements TreeSelectionListener, TreeExpansionListener, IQueryFilterer {

    private BooleanQuery query;
    private LinkedHashSet<Category> categoryList = new LinkedHashSet<Category>();
    private String queryStr;
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
            root = new TreePath(App.get().categoryTree.getModel().getRoot());

        if (System.currentTimeMillis() - collapsed < 100) {
            App.get().categoryTree.setSelectionPaths(selection.toArray(new TreePath[0]));
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
            categoryList.clear();
            query = null;
            queryStr = null;

        } else {
            queryStr = "";

            Builder builder = new Builder();
            categoryList.clear();
            for (TreePath path : selection) {
                Category category = (Category) path.getLastPathComponent();
                addCategoryToQuery(category, builder);
            }
            query = builder.build();
        }

        App.get().setDockablesColors();

        if (!clearing)
            App.get().appletListener.updateFileListing();

    }

    private void addCategoryToQuery(Category category, Builder builder) {
        String name = IndexItem.normalize(category.getName(), true);
        builder.add(new TermQuery(new Term(IndexItem.CATEGORY, name)), Occur.SHOULD);
        queryStr += " category:\"" + name + "\"";

        categoryList.add(category);

        for (Category subcat : category.getChildren()) {
            addCategoryToQuery(subcat, builder);
        }
    }

    @Override
    public void treeExpanded(TreeExpansionEvent event) {
        // TODO Auto-generated method stub

    }

    @Override
    public void treeCollapsed(TreeExpansionEvent event) {
        collapsed = System.currentTimeMillis();

    }

    @Override
    public void clearFilter() {
        clearing = true;
        App.get().categoryTree.clearSelection();
        categoryList.clear();
        clearing = false;
    }

    public HashSet<TreePath> getSelection() {
        return selection;
    }

    public void recursiveCategoryQuery(Category cat, StringBuffer buff) {
        for (Category category : cat.getChildren()) {
            String name = IndexItem.normalize(category.getName(), true);
            buff.append(" || category:\"");
            buff.append(name);
            buff.append("\"");
            recursiveCategoryQuery(category, buff);
        }
    }

    @Override
    public List<IFilter> getDefinedFilters() {
        CategoryTreeListener self = this;
        List<IFilter> result = new ArrayList<IFilter>();
        for (Category category : categoryList) {
            result.add(new IQueryFilter() {
                @Override
                public Query getQuery() {
                    String name = IndexItem.normalize(category.getName(), true);
                    StringBuffer queryStr = new StringBuffer();
                    queryStr.append(" category:\"");
                    queryStr.append(name);
                    queryStr.append("\"");
                    recursiveCategoryQuery(category, queryStr);

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
                    return IndexItem.normalize(category.getName(), true);
                }
            });
        }
        return result;
    }

    @Override
    public boolean hasFiltersApplied() {
        return categoryList.size() > 0;
    }

    public String toString() {
        return "Category panel filterer";
    }

    @Override
    public boolean hasFilters() {
        return categoryList.size() > 0;
    }
}
