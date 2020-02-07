package br.gov.pf.labld.graph.links;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import br.gov.pf.labld.graph.PathQueryListener;

public abstract class AbstractSearchLinksQuery implements SearchLinksQuery {

  @Override
  public void search(String start, String end, GraphDatabaseService graphDB, PathQueryListener listener) {
    Map<String, Object> params = new HashMap<>(2);
    params.put("start", start);
    params.put("end", end);

    Transaction tx = null;
    try {
      tx = graphDB.beginTx();

      Result result = graphDB.execute(getQuery(), params);
      ResourceIterator<Path> resourceIterator = result.columnAs("path");

      boolean continueQuery = true;
      while (continueQuery && resourceIterator.hasNext()) {
        Path path = resourceIterator.next();
        continueQuery = listener.pathFound(path);
      }

      tx.success();
    } finally {
      tx.close();
    }
  }

  protected String getQuery() {
    try (InputStream in = new BufferedInputStream(openQueryFile())) {
      return IOUtils.toString(in, "utf-8");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected InputStream openQueryFile() {
    String resourceName = this.getClass().getSimpleName() + ".cypher";
    return this.getClass().getResourceAsStream(resourceName);
  }

}
