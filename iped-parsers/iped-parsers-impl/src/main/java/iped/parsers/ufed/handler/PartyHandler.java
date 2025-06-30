package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_ID;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_NAME;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_PHONE;
import static iped.properties.ExtraProperties.CONVERSATION_SUFFIX_USERNAME;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItemReader;
import iped.parsers.chat.PartyStringBuilder;
import iped.parsers.chat.PartyStringBuilderFactory;
import iped.parsers.ufed.model.Party;
import iped.properties.MediaTypes;
import iped.search.IItemSearcher;

public class PartyHandler extends BaseModelHandler<Party> {

    private static final Logger logger = LoggerFactory.getLogger(PartyHandler.class);

    private String source;
    private Map<String, IItemReader> cache;

    protected PartyHandler(Party model, String source, IItemReader parentItem, Map<String, IItemReader> cache) {
        super(model, parentItem);
        this.cache = cache;
        this.source = source;
    }

    public PartyHandler(Party model, String source) {
        super(model);
        this.source = source;
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {
        metadata.add(prefix, getTitle());

        model.getReferencedContact().ifPresentOrElse(ref -> {
            metadata.add(prefix + CONVERSATION_SUFFIX_ID, StringUtils.firstNonBlank(model.getIdentifier(), ref.getUserID()));
            metadata.add(prefix + CONVERSATION_SUFFIX_NAME, StringUtils.firstNonBlank(model.getName(), ref.getName()));
            metadata.add(prefix + CONVERSATION_SUFFIX_PHONE, ref.getPhoneNumber());
            metadata.add(prefix + CONVERSATION_SUFFIX_USERNAME, ref.getUsername());
        }, () -> {
            metadata.add(prefix + CONVERSATION_SUFFIX_ID, model.getIdentifier());
            metadata.add(prefix + CONVERSATION_SUFFIX_NAME, model.getName());
        });
    }

    @Override
    public void loadReferences(IItemSearcher searcher) {
        if (model.getReferencedContact().isPresent() || model.isSystemMessage() || model.getIdentifier() == null) {
            return;
        }

        String identifier = model.getIdentifier();
        if (cache.containsKey(identifier)) {
            IItemReader ref = cache.get(identifier);
            if (ref != null) {
                model.setReferencedContact(ref);
            }
            return;
        }

        String query;
        if (model.isPhoneOwner()) {
            query = AccountableHandler.createAccountableQuery(identifier, source, MediaTypes.UFED_USER_ACCOUNT_MIME, model, item, searcher);
        } else {
            query = AccountableHandler.createAccountableQuery(identifier, source, MediaTypes.UFED_CONTACT_MIME, model, item, searcher);
        }
        List<IItemReader> results = searcher.search(query);
        if (!results.isEmpty()) {
            IItemReader result = results.get(0);
            cache.put(identifier, result);
            model.setReferencedContact(result);
        } else {
            cache.put(identifier, null);
            logger.debug("Party reference was not found: {}", model);
        }
    }

    @Override
    public String getTitle() {

        PartyStringBuilder builder = PartyStringBuilderFactory.getBuilder(source);

        model.getReferencedContact().ifPresentOrElse(ref -> {
            builder //
                    .withUserId(StringUtils.firstNonBlank(model.getIdentifier(), ref.getUserID())) //
                    .withName(StringUtils.firstNonBlank(model.getName(), ref.getName())) //
                    .withPhoneNumber(ref.getPhoneNumber()) //
                    .withUsername(ref.getUsername());
        }, () -> {
            builder //
                    .withUserId(model.getIdentifier()) //
                    .withName(model.getName());
        });

        return builder.build();
    }
}
