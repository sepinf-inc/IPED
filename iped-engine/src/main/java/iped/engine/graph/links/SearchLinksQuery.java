package iped.engine.graph.links;

import org.neo4j.graphdb.GraphDatabaseService;

import iped.engine.graph.PathQueryListener;

public interface SearchLinksQuery {

    void search(String start, String end, GraphDatabaseService graphDB, PathQueryListener listener);

    String getQueryName();

    String getLabel();
}
