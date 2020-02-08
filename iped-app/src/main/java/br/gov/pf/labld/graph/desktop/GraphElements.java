package br.gov.pf.labld.graph.desktop;

import java.util.HashSet;
import java.util.Set;

import org.kharon.Edge;
import org.kharon.Node;

public class GraphElements {

  private Set<Node> nodes = new HashSet<>();
  private Set<Edge> edges = new HashSet<>();
  private Set<String> nodeIds = new HashSet<>();
  private Set<String> edgeIds = new HashSet<>();

  public void add(Node node) {
    this.nodes.add(node);
    this.nodeIds.add(node.getId());
  }

  public void add(Edge edge) {
    this.edges.add(edge);
    this.edgeIds.add(edge.getId());
  }

  public boolean containsNode(String nodeId) {
    return this.nodeIds.contains(nodeId);
  }

  public boolean containsEdge(String edgeId) {
    return this.edgeIds.contains(edgeId);
  }

  public Set<Node> getNodes() {
    return nodes;
  }

  public Set<Edge> getEdges() {
    return edges;
  }

  public Set<String> getNodeIds() {
    return nodeIds;
  }

  public Set<String> getEdgeIds() {
    return edgeIds;
  }

}
