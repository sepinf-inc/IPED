package iped.app.ui.filterdecisiontree;

import iped.viewers.api.IFilter;

public class FilterNode extends DecisionNode {
    IFilter filter;

    public FilterNode(IFilter filter) {
        this.filter = filter;
    }

    public IFilter getFilter() {
        return filter;
    }

    @Override
    public String toString() {
        return filter.toString();
    }

}
