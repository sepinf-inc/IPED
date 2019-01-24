package br.gov.pf.labld.graph.desktop;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import dpf.sp.gpinf.indexer.desktop.Messages;

public class SearchLinksWorker extends SwingWorker<Void, Void> implements PathQueryListener {

  private static Logger LOGGER = LoggerFactory.getLogger(SearchLinksWorker.class);

  private AppGraphAnalytics app;

  private List<String> origin;
  private List<String> destiny;
  private List<String> queries;

  private GraphElements graphElements;
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
    try {
      GraphDatabaseService graphDb = GraphServiceFactoryImpl.getInstance().getGraphService().getGraphDb();
      double total = queries.size() * origin.size() * destiny.size();
      double count = 0;

      Set<String> control = new HashSet<>();

      for (String queryName : queries) {
        SearchLinksQuery query = SearchLinksQueryProvider.get().getQuery(queryName);
        app.setStatus(Messages.getString("GraphAnalysis.LinksSearching", query.getLabel(), found));
        for (String start : origin) {
          for (String end : destiny) {

            String id = Arrays.asList(start, end).stream().sorted().collect(Collectors.joining("_"));

            if (control.add(id) && !start.equals(end)) {
              query.search(start, end, graphDb, this);
            }
            count = count + 1;
            double progress = (count / total) * 100;
            app.setProgress((int) progress);
          }
        }
      }

    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
    }
    return null;
  }

  @Override
  public boolean pathFound(Path path) {
    app.addPath(path, this.graphElements);
    found++;
    return true;
  }

  @Override
  protected void done() {
    app.addGraphElements(graphElements);
    app.setProgress(100);
    app.setStatus(Messages.get("GraphAnalysis.Done"));
  }

}
