package br.gov.pf.labld.graph.desktop;

import java.util.Collection;
import java.util.HashSet;

import javax.swing.SwingWorker;

import org.kharon.Edge;
import org.neo4j.graphdb.Relationship;

import br.gov.pf.labld.graph.EdgeQueryListener;
import br.gov.pf.labld.graph.GraphService;
import br.gov.pf.labld.graph.GraphServiceFactoryImpl;
import dpf.sp.gpinf.indexer.desktop.Messages;

class AddRelationshipWorker extends SwingWorker<Void, Void> implements EdgeQueryListener {

    /**
     * 
     */
    private final AppGraphAnalytics app;
    private Collection<Long> ids;
    private Collection<Edge> newEdges = new HashSet<>();
    private int found = 0;

    public AddRelationshipWorker(AppGraphAnalytics appGraphAnalytics, Collection<Long> ids) {
        super();
        app = appGraphAnalytics;
        this.ids = ids;
    }

    @Override
    protected Void doInBackground() throws Exception {
        app.setStatus(Messages.getString("GraphAnalysis.Processing"));
        app.setProgress(0);
        GraphService graphService = GraphServiceFactoryImpl.getInstance().getGraphService();
        graphService.getRelationships(ids, this);
        app.addEdges(newEdges);
        return null;
    }

    @Override
    public boolean edgeFound(Relationship relationship) {
        found++;
        app.increaseProgress((int) ((found / this.ids.size()) * 100));
        Edge edge = app.addRelationship(relationship);
        if (edge != null) {
            this.newEdges.add(edge);
        }
        return true;
    }

    @Override
    protected void done() {
        app.setStatus(Messages.getString("GraphAnalysis.Done"));
        app.setProgress(100);
    }

}