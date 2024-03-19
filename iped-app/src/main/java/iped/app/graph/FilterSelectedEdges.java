package iped.app.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.kharon.Edge;
import org.kharon.Graph;
import org.kharon.OverlappedEdges;

import iped.app.ui.App;
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.engine.data.ItemId;
import iped.engine.search.MultiSearchResult;
import iped.exception.ParseException;
import iped.exception.QueryNodeException;
import iped.search.IMultiSearchResult;
import iped.viewers.api.IFilter;
import iped.viewers.api.IResultSetFilter;
import iped.viewers.api.IResultSetFilterer;

public class FilterSelectedEdges implements IResultSetFilterer {

    private static FilterSelectedEdges INSTANCE = new FilterSelectedEdges();

    private Set<Edge> selectedEdges = new HashSet<>();

    private Graph graph;

    private FilterSelectedEdges() {
    }

    public static FilterSelectedEdges getInstance() {
        return INSTANCE;
    }

    public void selectEdges(Collection<Edge> edges, boolean keepSelection) {
        if (!keepSelection) {
            this.selectedEdges.clear();
        }
        boolean added = false;
        for (Edge edge : edges) {
            if (edge instanceof OverlappedEdges) {
                OverlappedEdges overlapped = (OverlappedEdges) edge;
                for (Edge e : overlapped.getEdges()) {
                    if (selectedEdges.add(e))
                        added = true;
                }
            } else {
                if (selectedEdges.add(edge))
                    added = true;
            }
        }
        if (added) {
            updateResults();
        }
    }

    public void setEdge(Edge edge) {
        if (edge instanceof OverlappedEdges) {
            OverlappedEdges edges = (OverlappedEdges) edge;
            if (selectedEdges.size() == edges.getEdgeCount()) {
                boolean equal = true;
                for (Edge e : edges.getEdges()) {
                    if (!selectedEdges.contains(e)) {
                        equal = false;
                        break;
                    }
                }
                if (equal) {
                    return;
                }
            }
        }
        addEdge(edge, false);
    }

    public void addEdge(Edge edge, boolean keepSelection) {
        if (!keepSelection) {
            this.selectedEdges.clear();
        }
        if (edge instanceof OverlappedEdges) {
            OverlappedEdges edges = (OverlappedEdges) edge;
            for (Edge e : edges.getEdges()) {
                selectedEdges.add(e);
            }
        } else {
            selectedEdges.add(edge);
        }
        updateResults();
    }

    public void removeEdge(Edge edge) {
        if (edge instanceof OverlappedEdges) {
            OverlappedEdges edges = (OverlappedEdges) edge;
            for (Edge e : edges.getEdges()) {
                selectedEdges.remove(e);
            }
        } else {
            selectedEdges.remove(edge);
        }
        updateResults();
    }

    public void unselecEdgesOfNodes(Collection<String> nodeIds) {
        Iterator<Edge> iter = selectedEdges.iterator();
        boolean update = false;
        while (iter.hasNext()) {
            Edge edge = iter.next();
            if (nodeIds.contains(edge.getSource()) || nodeIds.contains(edge.getTarget())) {
                iter.remove();
                update = true;
            }
        }
        if (update)
            updateResults();
    }

    private void updateResults() {
        App.get().setGraphDefaultColor(selectedEdges.isEmpty());
        App.get().getAppListener().updateFileListing();
    }

    public void clearSelection(boolean updateResults) {
        if (selectedEdges.size() > 0) {
            selectedEdges.clear();
            if (updateResults) {
                updateResults();
            } else {
                App.get().setGraphDefaultColor(true);
            }
        }
    }

    public Set<IItemId> getItemIdsOfSelectedEdges() {
        HashMap<String, Integer> map = new HashMap<>();
        int i = 0;
        for (IPEDSource source : App.get().appCase.getAtomicSources()) {
            for (String uuid : source.getEvidenceUUIDs()) {
                map.put(uuid, i);
            }
            i++;
        }
        Set<IItemId> result = new HashSet<>();
        for (Edge edge : selectedEdges) {
            String[] values = edge.getLabel().split("_");
            String uuid = values[0];
            String id = values[1];
            ItemId itemId = new ItemId(map.get(uuid), Integer.valueOf(id));
            result.add(itemId);
        }
        return result;
    }

    @Override
    public List getDefinedFilters() {
        ArrayList<IFilter> result = new ArrayList<IFilter>();
        IFilter filter = getFilter();
        if (filter != null) {
            result.add(filter);
        }
        return result;
    }

    @Override
    public IFilter getFilter() {
        if (selectedEdges != null && selectedEdges.size() > 0) {
            Set<IItemId> selectedEdgesItems = FilterSelectedEdges.getInstance().getItemIdsOfSelectedEdges();
            HashSet<Edge> edges = new HashSet();
            edges.addAll(FilterSelectedEdges.getInstance().selectedEdges);
            return new EdgeFilter(graph, selectedEdgesItems, edges);
        } else {
            return null;
        }
    }

    @Override
    public boolean hasFilters() {
        return selectedEdges.size() > 0;
    }

    @Override
    public boolean hasFiltersApplied() {
        return false;
    }

    @Override
    public void clearFilter() {
        FilterSelectedEdges.INSTANCE.clearSelection(false);
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }
}

class EdgeFilter implements IResultSetFilter {
    Set<Edge> selectedEdges = new HashSet();
    Set<IItemId> selectedEdgesIds = new HashSet();
    String name;
    Graph graph;

    public EdgeFilter(Graph graph, Set<IItemId> itemIdsOfSelectedEdges, HashSet<Edge> edges) {
        this.selectedEdgesIds.addAll(itemIdsOfSelectedEdges);
        this.selectedEdges = edges;
        this.graph = graph;
        StringBuffer sb = new StringBuffer();

        String lastLabel = null;
        HashSet group = new HashSet();
        for (Iterator iterator = selectedEdges.iterator(); iterator.hasNext();) {
            Edge edge = (Edge) iterator.next();
            String filterInfo = graph.getNode(edge.getSource()).getLabel() + "->" + graph.getNode(edge.getTarget()).getLabel();
            if (!group.contains(filterInfo)) {
                group.add(filterInfo);
                sb.append(filterInfo);
                sb.append(",");
            }
        }
        if (sb.length() > 1) {
            name = sb.toString().substring(0, sb.length() - 1);
        }
    }

    @Override
    public IMultiSearchResult filterResult(IMultiSearchResult src) throws ParseException, QueryNodeException, IOException {
        IMultiSearchResult result = src;
        ArrayList<IItemId> filteredItems = new ArrayList<IItemId>();
        ArrayList<Float> scores = new ArrayList<Float>();
        int i = 0;
        for (IItemId item : result.getIterator()) {
            if (selectedEdgesIds.contains(item)) {
                filteredItems.add(item);
                scores.add(result.getScore(i));
            }
            i++;
        }
        result = new MultiSearchResult(filteredItems.toArray(new ItemId[0]), ArrayUtils.toPrimitive(scores.toArray(new Float[0])));
        return result;
    }

    public String toString() {
        return name;
    }
}
