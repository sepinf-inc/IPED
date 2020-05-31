package br.gov.pf.labld.graph.desktop;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.kharon.Edge;
import org.kharon.OverlappedEdges;

import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemId;
import iped3.IItemId;

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
    
    public Set<IItemId> getItemIdsOfSelectedEdges(){
        HashMap<String, Integer> map = new HashMap<>();
        int i = 0;
        for(IPEDSource source : App.get().appCase.getAtomicSources()) {
            for(String uuid : source.getEvidenceUUIDs()) {
                map.put(uuid, i);
            }
            i++;
        }
        Set<IItemId> result = new HashSet<>();
        for(Edge edge : selectedEdges) {
            String[] values = edge.getLabel().split("_");
            String uuid = values[0];
            String id = values[1];
            ItemId itemId = new ItemId(map.get(uuid), Integer.valueOf(id));
            result.add(itemId);
        }
        return result;
    }
    

}
