package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.UFED_META_PREFIX;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;

import iped.data.IItemReader;
import iped.parsers.ufed.model.Party;
import iped.properties.BasicProps;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;

public class PartyHandler extends BaseModelHandler<Party> {

    private Map<String, IItemReader> cache;

    protected PartyHandler(Party model, IItemReader parentItem, Map<String, IItemReader> cache) {
        super(model, parentItem);
        this.cache = cache;
    }

    protected PartyHandler(Party model, IItemReader parentItem) {
        super(model, parentItem);
    }

    public PartyHandler(Party model) {
        super(model);
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {
        metadata.add(UFED_META_PREFIX + prefix, getTitle());

        model.getReferencedContact().ifPresentOrElse(ref -> {
            metadata.add(UFED_META_PREFIX + prefix + ":id", StringUtils.firstNonBlank(model.getIdentifier(), ref.getUserID()));
            metadata.add(UFED_META_PREFIX + prefix + ":name", StringUtils.firstNonBlank(model.getName(), ref.getName()));
            metadata.add(UFED_META_PREFIX + prefix + ":phoneNumber", ref.getPhoneNumber());
            metadata.add(UFED_META_PREFIX + prefix + ":username", ref.getUsername());
        }, () -> {
            metadata.add(UFED_META_PREFIX + prefix + ":id", model.getIdentifier());
            metadata.add(UFED_META_PREFIX + prefix + ":name", model.getName());
        });
    }

    @Override
    public void loadReferences(IItemSearcher searcher) {
        if (model.getReferencedContact() != null || model.isSystemMessage() || model.getIdentifier() == null) {
            return;
        }

        String identifier = model.getIdentifier();
        if (cache.containsKey(identifier)) {
            model.setReferencedContact(cache.get(identifier));
            return;
        }

        String source = (String) model.getField("Source");
        String query;
        if (model.isPhoneOwner()) {
            query = ChatHandler.createUserAccountQuery(identifier, model, item, searcher);
        } else {
            String identifierNode = StringUtils.substringBefore(identifier, '@');
            String possiblePhoneNumber = (!identifierNode.isBlank() && !identifierNode.equals(identifier)) ? identifierNode : null;
            query = BasicProps.CONTENTTYPE + ":\"" + MediaTypes.UFED_CONTACT_MIME.toString() + "\""
                    + " && " + BasicProps.EVIDENCE_UUID + ":\"" + item.getDataSource().getUUID() + "\""
                    + " && " + searcher.escapeQuery(UFED_META_PREFIX + "extractionId") + ":" + model.getExtractionId()
                    + " && " + searcher.escapeQuery(UFED_META_PREFIX + "Source") + ":\"" + source + "\""
                    + " && (" + searcher.escapeQuery(UFED_META_PREFIX + "UserID") + ":\"" + identifier + "\""
                    + ((possiblePhoneNumber != null) ? (" || " + searcher.escapeQuery(UFED_META_PREFIX + "PhoneNumber") + ":\"" + possiblePhoneNumber + "\"") : "") + ")";
        }

        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            IItemReader result = results.get(0);
            cache.put(identifier, result);
            model.setReferencedContact(result);
        } else {
            cache.put(identifier, null);
        }
    }

    @Override
    public String getTitle() {

        if (StringUtils.isAllBlank(model.getName(), model.getIdentifier())) {
            return "";
        }
        if (StringUtils.isNoneBlank(model.getName(), model.getIdentifier())) {
            return String.format("%s (%s)", model.getName(), model.getIdentifier());
        }

        return StringUtils.firstNonBlank(model.getName(), model.getIdentifier());
    }
}
