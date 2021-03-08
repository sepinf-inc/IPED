package br.gov.pf.labld.graph;

import org.neo4j.graphdb.Relationship;

public interface EdgeQueryListener {

    public boolean edgeFound(Relationship edge);

}
