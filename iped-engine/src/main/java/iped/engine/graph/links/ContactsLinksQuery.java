package iped.engine.graph.links;

import iped.engine.localization.Messages;

public class ContactsLinksQuery extends AbstractSearchLinksQuery {

    private static final String QUERY_NAME = "CONTACTS";

    @Override
    public String getQueryName() {
        return QUERY_NAME;
    }

    @Override
    public String getLabel() {
        return Messages.getString("GraphAnalysis.LinksContacts");
    }

}
