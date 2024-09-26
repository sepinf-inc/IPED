package iped.app.ui.filterdecisiontree;

import java.util.Set;

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
        Set<DecisionNode> nodes = model.getFiltersToNodeMap().get(filter);
        if (nodes != null) {
            nodes.add(this);
        }
        return clone;
    }

}
