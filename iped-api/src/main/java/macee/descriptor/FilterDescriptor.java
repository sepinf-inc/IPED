package macee.descriptor;

import macee.filter.Filter;

public interface FilterDescriptor extends Descriptor {

  Filter getFilter();

  void setFilter(Filter filter);

}
