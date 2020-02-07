package br.gov.pf.labld.graph;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphServiceImpl implements GraphService {

  private static Logger LOGGER = LoggerFactory.getLogger(GraphServiceImpl.class);

  private GraphDatabaseService graphDB;
  private boolean started = false;
  private File dbFile;

  public void start(File path) {
    if (!started) {
      dbFile = path;
      GraphDatabaseFactory graphDatabaseFactory = new GraphDatabaseFactory();
      LOGGER.info("Starting neo4j service at " + path.getAbsolutePath());

      GraphDatabaseBuilder builder = graphDatabaseFactory.newEmbeddedDatabaseBuilder(dbFile);

      graphDB = builder.newGraphDatabase();

      started = true;

    } else {
      LOGGER.info("Service already started.");
    }
  }

  public synchronized void stop() {
    if (started) {
      LOGGER.info("Shutting down neo4j service.");
      graphDB.shutdown();
      started = false;
    } else {
      LOGGER.info("Service already stopped.");
    }
  }

  public GraphDatabaseService getGraphDb() {
    return graphDB;
  }

  @Override
  public synchronized File getDbFile() {
    return dbFile;
  }

  @Override
  public void getNodes(String[] ids, NodeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("param", ids);
      Result result = graphDB.execute("MATCH (n) WHERE n.evidenceId IN $param RETURN n", parameters);

      ResourceIterator<Node> resourceIterator = result.columnAs("n");
      boolean proceed = true;
      while (resourceIterator.hasNext() && proceed) {
        Node node = resourceIterator.next();
        proceed = listener.nodeFound(node);
      }

      tx.success();
    } finally {
      tx.close();
    }
  }

  @Override
  public void getNodes(Collection<Long> ids, NodeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("param", ids);
      Result result = graphDB.execute("MATCH (n) WHERE ID(n) IN $param RETURN n", parameters);

      ResourceIterator<Node> resourceIterator = result.columnAs("n");
      boolean proceed = true;
      while (resourceIterator.hasNext() && proceed) {
        Node node = resourceIterator.next();
        proceed = listener.nodeFound(node);
      }

      tx.success();
    } finally {
      tx.close();
    }
  }

  @Override
  public void getNeighboursWithLabels(Collection<String> labels, Long nodeId, NodeEdgeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("labels", labels);
      parameters.put("nodeId", nodeId);
      Result result = graphDB.execute(
          "MATCH (n)-[r]-(m) WHERE ID(n) = $nodeId AND ANY(l IN LABELS(m) WHERE l IN $labels) RETURN m as node, r as edge",
          parameters);
      boolean proceed = true;
      while (result.hasNext() && proceed) {
        Map<String, Object> next = result.next();
        Node node = (Node) next.get("node");
        proceed = listener.nodeFound(node);
        Relationship edge = (Relationship) next.get("edge");
        proceed = proceed && listener.edgeFound(edge);
      }
      tx.success();
    } finally {
      tx.close();
    }
  }

  @Override
  public void getNeighbours(Long id, NodeEdgeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("param", id);
      Result result = graphDB.execute("MATCH (n)-[r]-(m) WHERE ID(n) = $param RETURN m as node, r as edge", parameters);
      boolean proceed = true;
      while (result.hasNext() && proceed) {
        Map<String, Object> next = result.next();
        Node node = (Node) next.get("node");
        proceed = listener.nodeFound(node);
        Relationship edge = (Relationship) next.get("edge");
        proceed = proceed && listener.edgeFound(edge);
      }
      tx.success();
    } finally {
      tx.close();
    }
  }

  @Override
  public void getConnections(Set<Long> ids, EdgeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("param", ids);

      Result result = graphDB.execute("MATCH (n)-[r]-(m) WHERE ID(n) IN $param AND ID(m) IN $param RETURN r as edge",
          parameters);
      ResourceIterator<Relationship> iterator = result.columnAs("edge");
      boolean proceed = true;
      while (iterator.hasNext() && proceed) {
        Relationship edge = iterator.next();
        proceed = listener.edgeFound(edge);
      }
      tx.success();
    } finally {
      tx.close();
    }
  }

  @Override
  public void getPaths(Long source, Long target, int maxDistance, PathQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("source", source);
      parameters.put("target", target);

      StringBuilder queryBuilder = new StringBuilder("MATCH p = allShortestPaths((n1)-[*1..").append(maxDistance)
          .append("]-(n2))");
      queryBuilder.append(" WHERE ID(n1) = $source");
      queryBuilder.append(" AND ID(n2) = $target");
      queryBuilder.append(" RETURN p");

      Result result = graphDB.execute(queryBuilder.toString(), parameters);
      ResourceIterator<Path> iterator = result.columnAs("p");
      boolean proceed = true;
      while (iterator.hasNext() && proceed) {
        Path path = iterator.next();
        proceed = listener.pathFound(path);
      }
      tx.success();
    } finally {
      tx.close();
    }

  }

  @Override
  public void search(String param, NodeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("param", param.toUpperCase());

      String query = "MATCH (n) WHERE ANY(prop IN keys(n) WHERE toUpper(toString(n[prop])) CONTAINS $param) RETURN n";

      Result result = graphDB.execute(query, parameters);
      ResourceIterator<Node> resourceIterator = result.columnAs("n");
      boolean proceed = true;
      while (resourceIterator.hasNext() && proceed) {
        Node node = resourceIterator.next();
        proceed = listener.nodeFound(node);
      }

      tx.success();
    } finally {
      tx.close();
    }

  }

  @Override
  public void findConnections(Long id, ConnectionQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("param", id);

      String query = "MATCH (n)--(m) WHERE ID(n) = $param RETURN DISTINCT m as neighbour, LABELS(m) AS labels";

      Result result = graphDB.execute(query, parameters);
      ResourceIterator<Collection<String>> resourceIterator = result.columnAs("labels");

      Map<String, Integer> accum = new HashMap<>();
      while (resourceIterator.hasNext()) {
        Collection<String> labels = resourceIterator.next();
        for (Object label : labels) {
          Integer cnt = accum.get(label.toString());
          if (cnt == null) {
            cnt = 0;
          }
          cnt++;
          accum.put(label.toString(), cnt);
        }
      }
      for (Entry<String, Integer> entry : accum.entrySet()) {
        listener.connectionsFound(entry.getKey(), entry.getValue());
      }
      tx.success();
    } finally {
      tx.close();
    }

  }

  @Override
  public List<Node> search(Label label, Map<String, Object> params) {
    final List<Node> result = new ArrayList<>();
    search(label, params, new NodeQueryListener() {

      @Override
      public boolean nodeFound(Node node) {
        result.add(node);
        return true;
      }
    });
    return result;
  }

  @Override
  public void search(Label label, Map<String, Object> params, NodeQueryListener listener, String... ordering) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      StringBuilder queryBuilder = new StringBuilder();
      queryBuilder.append("MATCH (n:").append(label.name()).append(") ");
      if (!params.isEmpty()) {
        queryBuilder.append(" WHERE ");
        Iterator<String> iterator = params.keySet().iterator();
        while (iterator.hasNext()) {
          String name = iterator.next();
          queryBuilder.append(" n.").append(name);
          if (iterator.hasNext()) {
            queryBuilder.append(" AND ");
          }
        }
      }
      queryBuilder.append(" RETURN n ");

      if (ordering.length > 0) {
        queryBuilder.append(" ORDER BY ");
        queryBuilder.append(Arrays.stream(ordering).map(o -> "n." + o).collect(Collectors.joining(", ")));
      }

      Result result = graphDB.execute(queryBuilder.toString(), params);
      ResourceIterator<Node> resourceIterator = result.columnAs("n");
      boolean proceed = true;
      while (resourceIterator.hasNext() && proceed) {
        Node node = resourceIterator.next();
        proceed = listener.nodeFound(node);
      }

      tx.success();
    } finally

    {
      tx.close();
    }

  }

  @Override
  public void findLabels(LabelQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      StringBuilder query = new StringBuilder();
      query.append(" MATCH (n) ");
      query.append(" WITH DISTINCT labels(n) AS labels ");
      query.append(" UNWIND labels AS label ");
      query.append(" RETURN DISTINCT label ");
      query.append(" ORDER BY label ");

      Result result = graphDB.execute(query.toString());
      ResourceIterator<String> resourceIterator = result.columnAs("label");
      while (resourceIterator.hasNext()) {
        String label = resourceIterator.next();
        listener.labelFound(label);
      }

      tx.success();
    } finally {
      tx.close();
    }

  }

  @Override
  public void findLinks(ExportLinksQuery query, LinkQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      StringBuilder queryBuilder = new StringBuilder("MATCH ");
      StringBuilder whereBuilder = new StringBuilder(" WHERE ");
      StringBuilder returnBuilder = new StringBuilder(" RETURN DISTINCT ");

      int numOfLinks = query.getNumOfLinks();
      for (int index = 0; index < numOfLinks; index++) {
        List<String> types = query.getTypes(index);

        String node = "n_" + index;
        queryBuilder.append("(").append(node).append(")");

        Iterator<String> iterator = types.iterator();
        whereBuilder.append("(");
        while (iterator.hasNext()) {
          String type = iterator.next();
          whereBuilder.append(node).append(":").append(type).append(" ");
          if (iterator.hasNext()) {
            whereBuilder.append("OR ");
          }
        }
        whereBuilder.append(")");

        returnBuilder.append(node);
        if (index + 1 < numOfLinks) {
          int distance = query.getDistance(index);
          queryBuilder.append("-[*").append(distance).append("]-");
          returnBuilder.append(",");
          whereBuilder.append(" AND ");
        }
      }

      queryBuilder.append(whereBuilder).append(returnBuilder);

      Result result = graphDB.execute(queryBuilder.toString());

      while (result.hasNext()) {
        Map<String, Object> next = result.next();
        for (int index = 0; index < numOfLinks - 1; index++) {
          String node1Name = "n_" + index;
          String node2Name = "n_" + (index + 1);

          Node node1 = (Node) next.get(node1Name);
          Node node2 = (Node) next.get(node2Name);

          listener.linkFound(node1, node2);
        }

      }

      tx.success();
    } catch (Exception e) {
      LOGGER.error("Error executing query.", e);
    } finally {
      tx.close();
    }
  }

  @Override
  public void advancedSearch(String query, FreeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      Result result = graphDB.execute(query);
      listener.columnsFound(result.columns());
      while (result.hasNext()) {
        listener.resultFound(result.next());
      }

      tx.success();
    } finally {
      tx.close();
    }

  }

  @Override
  public void getRelationships(Collection<Long> ids, EdgeQueryListener listener) {
    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      HashMap<String, Object> parameters = new HashMap<>(1);
      parameters.put("param", ids);
      Result result = graphDB.execute("MATCH (n)-[r]-(m) WHERE ID(r) IN $param RETURN r", parameters);

      ResourceIterator<Relationship> resourceIterator = result.columnAs("r");
      boolean proceed = true;
      while (resourceIterator.hasNext() && proceed) {
        Relationship relationship = resourceIterator.next();
        proceed = listener.edgeFound(relationship);
      }

      tx.success();
    } finally {
      tx.close();
    }
  }

}
