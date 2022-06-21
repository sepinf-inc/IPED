package iped.engine.graph;

import org.neo4j.graphdb.Path;

public interface PathQueryListener {

    public boolean pathFound(Path path);

}
