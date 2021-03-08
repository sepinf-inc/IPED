package br.gov.pf.labld.graph;

import org.neo4j.graphdb.Node;

public interface LinkQueryListener {

    void linkFound(Node node1, Node node2);

}
