package br.gov.pf.labld.graph.links;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class EmailLinksQuery extends AbstractSearchLinksQuery {

  private static final String QUERY_NAME = "EMAILS";

  @Override
  public String getQueryName() {
    return QUERY_NAME;
  }

  @Override
  public String getLabel() {
    return Messages.get("GraphAnalysis.LinksEmails");
  }

}
