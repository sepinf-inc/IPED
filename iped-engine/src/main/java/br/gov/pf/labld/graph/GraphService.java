package br.gov.pf.labld.graph;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;

public interface GraphService {

  void start(File path) throws IOException;

  void stop() throws IOException;

  GraphDatabaseService getGraphDb();

  File getDbFile();

  void getNodes(String[] ids, NodeQueryListener listener);

  void getNodes(Collection<Long> ids, NodeQueryListener listener);

  void getNeighbours(Long id, NodeEdgeQueryListener listener);

  void getConnections(Set<Long> ids, EdgeQueryListener listener);

  void getPaths(Long source, Long target, int maxDistance, PathQueryListener listener);

  void search(String param, NodeQueryListener listener);

  void findConnections(Long id, ConnectionQueryListener listener);
  
  void findRelationships(Long id, ConnectionQueryListener listener);

  void getNeighboursWithLabels(Collection<String> labels, Long nodeId, NodeEdgeQueryListener listener, int maxNodes);
  
  void getNeighboursWithRelationships(Collection<String> relationships, Long nodeId, NodeEdgeQueryListener listener, int maxNodes);

  List<Node> search(Label label, Map<String, Object> params);

  void search(Label label, Map<String, Object> params, NodeQueryListener listener, String... ordering);

  void findLabels(LabelQueryListener listener);

  void findLinks(ExportLinksQuery query, LinkQueryListener listener);

  void advancedSearch(String string, FreeQueryListener listener);

  void getRelationships(Collection<Long> ids, EdgeQueryListener listener);

}
