package br.gov.pf.labld.graph.desktop;

import javax.swing.SwingWorker;

import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import dpf.sp.gpinf.indexer.desktop.Messages;

class LoadGraphDatabaseWorker extends SwingWorker<Void, Void> {

  private final AppGraphAnalytics app;

  LoadGraphDatabaseWorker(AppGraphAnalytics appGraphAnalytics) {
    app = appGraphAnalytics;
  }

  @Override
  protected Void doInBackground() throws Exception {
    initGraphService();
    return null;
  }

  private void initGraphService() {
    app.setStatus(Messages.getString("GraphAnalysis.Preparing"));
    app.setProgress(50);

    app.setEnabled(false);

    final ClassLoader classLoader = this.getClass().getClassLoader();
    Thread.currentThread().setContextClassLoader(classLoader);
    GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
    try {
      graphService.start(AppGraphAnalytics.getAppDBPath());
    } catch (Throwable e) {
      AppGraphAnalytics.LOGGER.error(e.getMessage(), e);
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void done() {
    app.setStatus(Messages.getString("GraphAnalysis.Ready"));
    app.setProgress(100);
    app.setEnabled(true);
    app.setDatabaseLoaded(true);
  }
}