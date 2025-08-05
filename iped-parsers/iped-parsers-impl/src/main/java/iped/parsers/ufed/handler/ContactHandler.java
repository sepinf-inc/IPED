package iped.parsers.ufed.handler;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.ufed.model.Contact;
import iped.parsers.ufed.model.ContactEntry;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

/**
 * Handles all processing logic for a Contact model.
 */
public class ContactHandler extends AccountableHandler<Contact> {

    private static final Logger logger = LoggerFactory.getLogger(ContactHandler.class);

    public ContactHandler(Contact model, IItemReader item) {
        super(model, item);
    }

    public ContactHandler(Contact model) {
        super(model, null);
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {
        super.fillMetadata(prefix, metadata);

    }

    @Override
    public void doLoadReferences(IItemSearcher searcher) {

        super.doLoadReferences(searcher);

        loadReferencedContact(searcher);
    }

    private void loadReferencedContact(IItemSearcher searcher) {

        if (model.getId() == null || !"Shared".equals(model.getType())) {
            return;
        }
        String query = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + model.getId() + "\" && " + BasicProps.SUBITEM + ":false";
        List<IItemReader> contactItems = searcher.search(query);
        if (!contactItems.isEmpty()) {
            if (contactItems.size() > 1) {
                logger.warn("Found more than 1 contact for shared contact: {}", contactItems);
            }
            model.setReferencedContact(contactItems.get(0));

        } else {
            logger.debug("Contact reference was not found: {}", model);
        }
    }

    @Override
    protected void doAddLinkedItemsAndSharedHashes(Set<String> linkedItems, Set<String> sharedHashes, IItemSearcher searcher) {

        super.doAddLinkedItemsAndSharedHashes(linkedItems, sharedHashes, searcher);

        model.getReferencedContact().ifPresent(ref -> {
            addLinkedItem(linkedItems, ref.getItem(), searcher);
        });
    }

    @Override
    public String getTitle() {

        String name = model.getName();
        List<String> contactEntries = model.getContactEntries().values().stream()
                .flatMap(List::stream)
                .map(ContactEntry::getValue)
                .collect(Collectors.toList());

        String data = Stream.of(Arrays.asList(name), contactEntries)
            .flatMap(List::stream)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(" | "));

        String typeStr = model.getType() != null ? "-" + model.getType() : "";

        return new StringBuilder()
                .append(model.getModelType())
                .append(typeStr)
                .append("-[")
                .append(StringUtils.firstNonBlank(data, model.getId()))
                .append("]")
                .toString();
    }
}