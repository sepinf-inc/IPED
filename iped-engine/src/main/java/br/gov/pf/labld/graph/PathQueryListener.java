package br.gov.pf.labld.graph;

import org.neo4j.graphdb.Path;

public interface PathQueryListener {

  public boolean pathFound(Path path);

}
