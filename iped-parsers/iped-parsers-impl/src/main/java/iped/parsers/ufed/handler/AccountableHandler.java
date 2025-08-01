package iped.parsers.ufed.handler;
import static iped.properties.ExtraProperties.UFED_META_PREFIX;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import iped.data.IItemReader;
import iped.parsers.ufed.model.Accountable;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.ContactPhoto;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

/**
 * Handles all processing logic for a Accountable model.
 */
public class AccountableHandler<T extends Accountable> extends BaseModelHandler<T> {

    public AccountableHandler(T model, IItemReader item) {
        super(model, item);
    }

    @Override
    public void doLoadReferences(IItemSearcher searcher) {

        model.getPhotos().forEach(photo -> {
            new ContactPhotoHandler(photo).loadReferences(searcher);
        });
    }

    @Override
    protected void fillMetadata(String prefix, Metadata metadata) {

        super.fillMetadata(prefix, metadata);

        model.getPhotos().forEach(photo -> {
            metadata.add(UFED_META_PREFIX + "Photo:id", photo.getPhotoNodeId());
            metadata.add(UFED_META_PREFIX + "Photo:name", photo.getName());
            metadata.add(UFED_META_PREFIX + "Photo:url", photo.getUrl());
        });

        model.getContactEntries().forEach((key, list) -> {
            for (ContactEntry value: list) {
                metadata.add(UFED_META_PREFIX + key, value.getValue());
                metadata.add(UFED_META_PREFIX + key + ":category", value.getCategory());
                metadata.add(UFED_META_PREFIX + key + ":domain", value.getDomain());
            }
        });
    }

    @Override
    protected void doAddLinkedItemsAndSharedHashes(Set<String> linkedItems, Set<String> sharedHashes, IItemSearcher searcher) {
        model.getPhotos().stream().map(ContactPhoto::getReferencedFile).filter(Optional::isPresent).forEach(ref -> {
            addLinkedItem(linkedItems, ref.get().getItem(), searcher);
        });
    }

    protected static String createAccountableQuery(String identifier, String source, MediaType contentType, BaseModel relatedModel, IItemReader relatedItem, IItemSearcher searcher) {

        Set<String> possiblePhoneNumbers = new HashSet<>();
        possiblePhoneNumbers.add(identifier);
        String identifierNode = StringUtils.substringBefore(identifier, '@');
        if (!identifierNode.isBlank()) {
            possiblePhoneNumbers.add(identifierNode);
        }

        return BasicProps.CONTENTTYPE + ":\"" + contentType.toString() + "\""
                + " && " + BasicProps.EVIDENCE_UUID + ":\"" + relatedItem.getDataSource().getUUID() + "\""
                + " && " + searcher.escapeQuery(UFED_META_PREFIX + "extractionId") + ":" + relatedModel.getExtractionId()
                + " && " + searcher.escapeQuery(UFED_META_PREFIX + "Source") + ":\"" + source + "\""
                + " && (" + searcher.escapeQuery(UFED_META_PREFIX + "UserID") + ":\"" + identifier + "\"" //
                + " || " + searcher.escapeQuery(UFED_META_PREFIX + "Username") + ":\"" + identifier + "\"" //
                + " || " + searcher.escapeQuery(UFED_META_PREFIX + "PhoneNumber") + ":(" + possiblePhoneNumbers.stream().collect(Collectors.joining(" ", "\"", "\"")) + ")" //
                + ")";
    }
}