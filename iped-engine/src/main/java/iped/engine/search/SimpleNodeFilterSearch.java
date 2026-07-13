package iped.engine.search;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.Query;

import iped.data.IIPEDSource;
import iped.engine.data.SimpleFilterNode;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;

public class SimpleNodeFilterSearch {
    private static final Map<SimpleFilterNode, Query> queryFromNode = Collections
            .synchronizedMap(new HashMap<SimpleFilterNode, Query>());

    public static Query getNodeQuery(IIPEDSource ipedSource, SimpleFilterNode node) {
        Query query = queryFromNode.get(node);
        if (query == null) {
            String property = node.getProperty();
            if (property != null) {
                String value = node.getValue();
                if (value != null) {
                    value = value.trim();
                    property = property.trim();
                    StringBuffer queryStr = new StringBuffer();
                    queryStr.append(property);
                    queryStr.append(":");
                    if (value.length() > 1 && !value.endsWith("]")) {
                        queryStr.append('"');
                        queryStr.append(value);
                        queryStr.append('"');
                    } else {
                        queryStr.append(value);
                    }
                    try {
                        query = new QueryBuilder(ipedSource).getQuery(queryStr.toString());
                    } catch (ParseException | QueryNodeException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (node.getAddChildren()) {
                Builder builder = new Builder();
                if (query != null) {
                    builder.add(query, Occur.SHOULD);
                }
                for (SimpleFilterNode child : node.getChildren()) {
                    Query childQuery = getNodeQuery(ipedSource, child);
                    if (childQuery != null) {
                        builder.add(childQuery, Occur.SHOULD);
                    }
                }
                query = builder.build();
            }
            if (query != null) {
                queryFromNode.put(node, query);
            }
        }
        return query;
    }
}
