package br.gov.pf.labld.graph.links;

import dpf.sp.gpinf.indexer.localization.Messages;

public class InstantMessagesLinksQuery extends AbstractSearchLinksQuery {

    private static final String QUERY_NAME = "INSTANT_MESSAGES";

    @Override
    public String getQueryName() {
        return QUERY_NAME;
    }

    @Override
    public String getLabel() {
        return Messages.getString("GraphAnalysis.LinksInstantMessages");
    }

}
