package iped.engine.graph.links;

import iped.engine.localization.Messages;

public class EmailLinksQuery extends AbstractSearchLinksQuery {

    private static final String QUERY_NAME = "EMAILS";

    @Override
    public String getQueryName() {
        return QUERY_NAME;
    }

    @Override
    public String getLabel() {
        return Messages.getString("GraphAnalysis.LinksEmails");
    }

}
