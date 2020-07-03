package br.gov.pf.labld.graph;

import org.neo4j.graphdb.RelationshipType;

public class DynRelationshipType implements RelationshipType {

  private String name;

  public DynRelationshipType(String name) {
    super();
    this.name = name;
  }

  @Override
  public String name() {
    return this.name;
  }

  public static RelationshipType withName(String name) {
    return new DynRelationshipType(name);
  }

  @Override
  public String toString() {
    return "RelationshipType [" + name + "]";
  }

}
