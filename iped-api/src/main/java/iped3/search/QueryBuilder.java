/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package iped3.search;

import iped3.exception.ParseException;
import iped3.exception.QueryNodeException;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;

/**
 *
 * @author WERNECK
 */
public interface QueryBuilder {

    Query getQuery(String texto) throws ParseException, QueryNodeException;

    Query getQuery(String texto, Analyzer analyzer) throws ParseException, QueryNodeException;

    Set<String> getQueryStrings(String queryText);

}
