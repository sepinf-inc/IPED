package br.gov.pf.labld.graph;

import org.neo4j.graphdb.Label;

public class DynLabel implements Label {

  private String name;

  public DynLabel(String name) {
    super();
    this.name = name;
  }

  @Override
  public String name() {
    return name;
  }

  public static DynLabel label(String name) {
    return new DynLabel(name);
  }

  @Override
  public String toString() {
    return "Label [" + name + "]";
  }

}
