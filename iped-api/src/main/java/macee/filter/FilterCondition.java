package macee.filter;

import macee.collection.CaseItemCollection;

/**
 * COMENTÁRIO (Werneck): onde está sendo usado?
 */
@FunctionalInterface
public interface FilterCondition {

    CaseItemCollection result();

    default String getName() {
        return "";
    }
}
