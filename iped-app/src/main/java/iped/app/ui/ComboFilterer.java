package iped.app.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;

import org.apache.lucene.search.Query;

import iped.engine.search.MultiSearchResult;
import iped.engine.search.QueryBuilder;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilter;
import iped.viewers.api.IMutableFilter;
import iped.viewers.api.IQueryFilter;
import iped.viewers.api.IQueryFilterer;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class ComboFilterer implements IQueryFilterer, IResultSetFilterer {
    private JComboBox<String> comboFilter;
    FilterManager fm;

    public ComboFilterer(FilterManager fm, JComboBox<String> comboFilter) {
        this.comboFilter = comboFilter;
        this.fm = fm;
    }

    @Override
    public List<IFilter> getDefinedFilters() {
        List<IFilter> result = new ArrayList<IFilter>();
        if (comboFilter.getSelectedIndex() != -1 && !App.FILTRO_TODOS.equals(comboFilter.getSelectedItem())) {
            if (!App.FILTRO_SELECTED.equals(comboFilter.getSelectedItem())) {
                result.add(new IQueryFilter() {
                    String filterName = (String) comboFilter.getSelectedItem();
                    String filterExpression = fm.getFilterExpression((String) comboFilter.getSelectedItem());
                    private Query query;

                    @Override
                    public Query getQuery() {
                        if (query == null) {
                            try {
                                query = new QueryBuilder(App.get().appCase).getQuery(filterExpression);
                            } catch (ParseException | QueryNodeException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                        return query;
                    }

                    @Override
                    public String toString() {
                        return filterName;
                    }
                });
            } else {
                result.add(getFilter());
            }
        }
        return result;
    }

    class CheckedFilter implements IResultSetFilter, IMutableFilter {
        @Override
        public IMultiSearchResult filterResult(IMultiSearchResult src)
                throws ParseException, QueryNodeException, IOException {
            return (MultiSearchResult) App.get().appCase.getMultiBookmarks().filterChecked(src);
        }

        public String toString() {
            return App.FILTRO_SELECTED;
        }
    }

    @Override
    public IFilter getFilter() {
        if (App.FILTRO_SELECTED.equals(comboFilter.getSelectedItem())) {
            return new CheckedFilter();
        }
        return null;
    }

    @Override
    public boolean hasFiltersApplied() {
        return comboFilter.getSelectedIndex() > 0;
    }

    @Override
    public Query getQuery() {
        if (comboFilter.getSelectedIndex() == -1 || App.FILTRO_TODOS.equals(comboFilter.getSelectedItem())
                || App.FILTRO_SELECTED.equals(comboFilter.getSelectedItem())) {
            return null;
        }

        Query result = null;
        try {
            result = new QueryBuilder(App.get().appCase)
                    .getQuery(fm.getFilterExpression((String) comboFilter.getSelectedItem()));
        } catch (ParseException | QueryNodeException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return result;
    }

    @Override
    public boolean hasFilters() {
        return comboFilter.getSelectedIndex() != -1 && !App.FILTRO_TODOS.equals(comboFilter.getSelectedItem());
    }

    @Override
    public void clearFilter() {
        App.get().appletListener.clearAllFilters = true;
        App.get().filterComboBox.setSelectedIndex(0);
        App.get().appletListener.clearAllFilters = false;
    }
}
