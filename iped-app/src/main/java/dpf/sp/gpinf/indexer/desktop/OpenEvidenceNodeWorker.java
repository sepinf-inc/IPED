package dpf.sp.gpinf.indexer.desktop;

import java.util.Arrays;

import javax.swing.SwingWorker;

import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import br.gov.pf.labld.graph.NodeQueryListener;
import dpf.sp.gpinf.indexer.search.ItemId;

class OpenEvidenceNodeWorker extends SwingWorker<Void, Void> implements NodeQueryListener {

  private final AppGraphAnalytics app;
  private Long id;
  private String evidenceId;

  public OpenEvidenceNodeWorker(AppGraphAnalytics appGraphAnalytics, Long id) {
    super();
    app = appGraphAnalytics;
    this.id = id;
  }

  @Override
  public boolean nodeFound(org.neo4j.graphdb.Node node) {
    evidenceId = (String) node.getProperty("evidenceId");
    return true;
  }

  @Override
  protected Void doInBackground() throws Exception {
    app.setStatus(Messages.getString("GraphAnalysis.Processing"));
    app.setProgress(0);
    GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
    graphService.getNodes(Arrays.asList(id), this);
    return null;
  }

  @Override
  protected void done() {
    app.setStatus(Messages.getString("GraphAnalysis.Done"));
    app.setProgress(100);
    showEvidence(Integer.parseInt(evidenceId));
  }

  private void showEvidence(int evidenceId) {
    ItemId itemId = new ItemId(0, evidenceId);
    int luceneId = App.get().appCase.getLuceneId(itemId);
    FileProcessor parsingTask = new FileProcessor(luceneId, false);
    parsingTask.execute();
  }

}