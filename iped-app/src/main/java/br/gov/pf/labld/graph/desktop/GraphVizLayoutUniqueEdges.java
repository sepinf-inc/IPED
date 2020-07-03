package br.gov.pf.labld.graph.desktop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.kharon.Edge;
import org.kharon.Graph;
import org.kharon.Node;
import org.kharon.layout.graphviz.GraphVizAlgorithm;
import org.kharon.layout.graphviz.GraphVizLayout;
import org.kharon.layout.graphviz.GraphVizResolver;

public class GraphVizLayoutUniqueEdges extends GraphVizLayout{

    public GraphVizLayoutUniqueEdges(GraphVizAlgorithm algorithm, GraphVizResolver graphVizResolver) {
        super(algorithm, graphVizResolver);
    }

    @Override
    public List<Graph> getConnectedSubGraphs(Graph graph) {
        List<Graph> subGraphs = new ArrayList<>();

        if (!graph.isEmpty()) {
            Set<Node> processed = new HashSet<>();
            Set<Node> nodes = graph.getNodes();

            Iterator<Node> iterator = nodes.iterator();
            while (iterator.hasNext()) {
                Node start = iterator.next();

                if (!processed.contains(start)) {
                    Graph subGraph = new Graph();
                    visit(start, processed, subGraph, graph);
                    subGraphs.add(subGraph);
                }
            }
        }
        return subGraphs;
    }

    private void visit(Node start, Set<Node> control, Graph subGraph, Graph graph) {
        control.add(start);

        subGraph.addNode(start);

        Set<Node> neighbours = graph.getNeighbours(start);
        for (Node neighbour : neighbours) {
            if (!control.contains(neighbour)) {
                visit(neighbour, control, subGraph, graph);
            }
        }

        Collection<Edge> edges = graph.getEdges(start);
        HashMap<String, Edge> overlappedEdges = new HashMap<>();
        for(Edge edge : edges) {
            overlappedEdges.putIfAbsent(edge.getSource() + "-" + edge.getTarget(), edge);
        }
        subGraph.addEdges(overlappedEdges.values());
    }
}
