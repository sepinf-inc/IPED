package macee.components;

import macee.collection.CaseItemCollection;

public interface Searcher {

    CaseItemCollection searchIndex(String luceneQuery) throws Exception;

    CaseItemCollection searchCollection(String collectionName, String query) throws Exception;

    CaseItemCollection searchSql(String sqlQuery) throws Exception;
}
