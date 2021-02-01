package br.gov.pf.labld.graph.links;

import dpf.sp.gpinf.indexer.desktop.Messages;

public class WirelessNetworksLinksQuery extends AbstractSearchLinksQuery {

    private static final String QUERY_NAME = "WIRELESSNETWORKS";

    @Override
    public String getQueryName() {
        return QUERY_NAME;
    }

    @Override
    public String getLabel() {
        return Messages.get("GraphAnalysis.LinksWireless");
    }

}
