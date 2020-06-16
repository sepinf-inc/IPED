package br.gov.pf.labld.graph.links;

import org.neo4j.graphdb.GraphDatabaseService;

import br.gov.pf.labld.graph.PathQueryListener;

public interface SearchLinksQuery {

  void search(String start, String end, GraphDatabaseService graphDB, PathQueryListener listener);

  String getQueryName();

  String getLabel();
}
