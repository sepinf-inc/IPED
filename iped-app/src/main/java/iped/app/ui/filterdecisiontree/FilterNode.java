package iped.app.ui.filterdecisiontree;

import iped.app.ui.CombinedFilterTreeModel;
import iped.viewers.api.IFilter;

public class FilterNode extends DecisionNode {
    IFilter filter;

    public FilterNode(IFilter filter, CombinedFilterTreeModel model) {
        super(model);
        this.filter = filter;
    }

    public IFilter getFilter() {
        return filter;
    }

    @Override
    public String toString() {
        return filter.toString();
    }

    @Override
    public DecisionNode clone() {
        FilterNode clone = new FilterNode(filter, model);
        clone.parent = this.parent;
        clone.inverted = this.inverted;
        model.getFiltersToNodeMap().get(filter).add(this);
        return clone;
    }

}
