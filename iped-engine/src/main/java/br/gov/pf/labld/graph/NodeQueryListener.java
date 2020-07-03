package br.gov.pf.labld.graph;

import org.neo4j.graphdb.Node;

public interface NodeQueryListener {

  public boolean nodeFound(Node node);
  
}
