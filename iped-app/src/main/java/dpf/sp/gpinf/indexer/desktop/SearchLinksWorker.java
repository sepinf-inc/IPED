package dpf.sp.gpinf.indexer.desktop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.swing.SwingWorker;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.PathQueryListener;
import br.gov.pf.labld.graph.links.SearchLinksQuery;
import br.gov.pf.labld.graph.links.SearchLinksQueryProvider;

public class SearchLinksWorker extends SwingWorker<Void, Void> implements PathQueryListener {

  private static Logger LOGGER = LoggerFactory.getLogger(SearchLinksWorker.class);

  private AppGraphAnalytics app;

  private List<String> origin;
  private List<String> destiny;
  private List<String> queries;

  private GraphElements graphElements;
  private int done = 0;
  private int total = 0;
  private int found = 0;

  public SearchLinksWorker(AppGraphAnalytics app, List<String> origin, List<String> destiny, List<String> queries) {
    super();
    this.app = app;
    this.origin = origin;
    this.destiny = destiny;
    this.queries = queries;
    this.graphElements = new GraphElements();
  }

  @Override
  protected Void doInBackground() throws Exception {

    ExecutorService executor = null;
    try {
      Set<String> control = new HashSet<>();

      executor = Executors.newWorkStealingPool();
      CompletionService<Void> completionService = new ExecutorCompletionService<>(executor);
      LOGGER.info("Running queries {}.", queries.stream().collect(Collectors.joining(", ")));

      List<Runnable> runnables = new ArrayList<>();
      for (String queryName : queries) {
        for (String start : origin) {
          for (String end : destiny) {

            String id = Arrays.asList(start, end).stream().sorted().collect(Collectors.joining("_")) + "_" + queryName;
            if (control.add(id) && !start.equals(end)) {
              SearchLinkRunnable subWorker = new SearchLinkRunnable(this, queryName, start, end);
              runnables.add(subWorker);
              total++;
            }
          }
        }
      }
      app.setStatus(Messages.getString("GraphAnalysis.LinksSearching", found));
      for (Runnable runnable : runnables) {
        completionService.submit(runnable, null);
      }
      while (done < total) {
        completionService.take();
      }

    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    } finally {
      if (executor != null) {
        executor.shutdown();
      }
    }
    return null;
  }

  @Override
  public boolean pathFound(Path path) {
    app.addPath(path, this.graphElements);
    found++;
    app.setStatus(Messages.getString("GraphAnalysis.LinksSearching", (done / total) * 100));
    return true;
  }

  public void doneQuery() {
    done = done + 1;
    double progress = (done / total) * 100;
    app.setProgress((int) progress);
  }

  @Override
  protected void done() {
    app.addGraphElements(graphElements);
    app.setProgress(100);
    app.setStatus(Messages.get("GraphAnalysis.Done"));
  }

  private class SearchLinkRunnable implements Runnable {

    private SearchLinksWorker listener;
    private String queryName;
    private String start;
    private String end;

    public SearchLinkRunnable(SearchLinksWorker listener, String queryName, String start, String end) {
      super();
      this.listener = listener;
      this.queryName = queryName;
      this.start = start;
      this.end = end;
    }

    @Override
    public void run() {
      try {
        LOGGER.info("Running query {}.", queryName);
        GraphDatabaseService graphDb = GraphServiceFactoryImpl.getInstance().getGraphService().getGraphDb();
        SearchLinksQuery query = SearchLinksQueryProvider.get().getQuery(queryName);
        query.search(start, end, graphDb, listener);
        LOGGER.info("Done query {}.", queryName);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      } finally {
        listener.doneQuery();
      }
    }

  }

}
