package iped.engine.graph.links;

import iped.engine.graph.GraphService;
import iped.engine.graph.PathQueryListener;

public interface SearchLinksQuery {

    void search(String start, String end, GraphService graphService, PathQueryListener listener);

    String getQueryName();

    String getLabel();
}
