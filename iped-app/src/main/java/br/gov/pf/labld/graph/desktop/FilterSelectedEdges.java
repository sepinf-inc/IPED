package br.gov.pf.labld.graph.desktop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.kharon.Edge;
import org.kharon.OverlappedEdges;

import dpf.sp.gpinf.indexer.desktop.App;

public class FilterSelectedEdges {
    
    private static FilterSelectedEdges INSTANCE = new FilterSelectedEdges();
    
    private Set<Edge> selectedEdges = new HashSet<>();
    
    private FilterSelectedEdges() {
    }
    
    public static FilterSelectedEdges getInstance() {
        return INSTANCE;
    }
    
    public void selectEdges(Collection<Edge> edges, boolean keepSelection) {
        if(!keepSelection) {
            this.selectedEdges.clear();
        }
        boolean added = false;
        for(Edge edge : edges) {
            if(edge instanceof OverlappedEdges) {
                OverlappedEdges overlapped = (OverlappedEdges)edge;
                for(Edge e : overlapped.getEdges()) {
                    if(selectedEdges.add(e))
                        added = true;
                }
            }else {
                if(selectedEdges.add(edge))
                    added = true;
            }
        }
        if(added) {
            updateResults();
        }
    }
    
    public void setEdge(Edge edge) {
        if(edge instanceof OverlappedEdges) {
            OverlappedEdges edges = (OverlappedEdges)edge;
            if(selectedEdges.size() == edges.getEdgeCount()) {
                boolean equal = true;
                for(Edge e : edges.getEdges()) {
                    if(!selectedEdges.contains(e)) {
                        equal = false;
                        break;
                    }
                }
                if(equal) {
                    return;
                }
            }
        }
        addEdge(edge, false);
    }
    
    public void addEdge(Edge edge, boolean keepSelection) {
        if(!keepSelection) {
            this.selectedEdges.clear();
        }
        if(edge instanceof OverlappedEdges) {
            OverlappedEdges edges = (OverlappedEdges)edge;
            for(Edge e : edges.getEdges()) {
                selectedEdges.add(e);
            }
        }else {
            selectedEdges.add(edge);
        }
        updateResults();
    }
    
    public void removeEdge(Edge edge) {
        if(edge instanceof OverlappedEdges) {
            OverlappedEdges edges = (OverlappedEdges)edge;
            for(Edge e : edges.getEdges()) {
                selectedEdges.remove(e);
            }
        }else {
            selectedEdges.remove(edge);
        }
        updateResults();
    }
    
    public void unselecEdgesOfNodes(Collection<String> nodeIds) {
        Iterator<Edge> iter = selectedEdges.iterator();
        boolean update = false;
        while(iter.hasNext()) {
            Edge edge = iter.next();
            if(nodeIds.contains(edge.getSource()) || nodeIds.contains(edge.getTarget())) {
                iter.remove();
                update = true;
            }
        }
        if(update) updateResults();
    }
    
    private void updateResults() {
        App.get().setGraphDefaultColor(selectedEdges.isEmpty());
        App.get().getAppListener().updateFileListing();
    }
    
    public void clearSelection() {
        if(selectedEdges.size() > 0) {
            selectedEdges.clear();
            updateResults();
        }
    }
    
    public Collection<Integer> getItemIdsOfSelectedEdges(){
        ArrayList<Integer> ids = new ArrayList<>();
        for(Edge edge : selectedEdges) {
            ids.add(Integer.parseInt(edge.getLabel()));
        }
        return ids;
    }
    

}
