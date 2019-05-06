package macee.filter;

import macee.collection.CaseItemCollection;

/**
 * Define um filtro sobre uma coleção de itens.
 * 
 * COMENTÁRIO (Werneck): remover getOperation()?
 */
public interface Filter {

    static CaseItemCollection apply(CaseItemCollection bs, Filter root) {
        return root.filter(bs);
    }

    CaseItemCollection filter(CaseItemCollection input);

    String getFilterName();

    Filter and(Filter filters);

    Filter or(Filter filters);

    Filter andNot(Filter filters);

    Filter xor(Filter filters);

    FilterOperation getOperation();
}
