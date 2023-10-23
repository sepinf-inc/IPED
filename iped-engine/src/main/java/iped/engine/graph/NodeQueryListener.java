package iped.engine.graph;

import org.neo4j.graphdb.Node;

public interface NodeQueryListener {

    public boolean nodeFound(Node node);

}
