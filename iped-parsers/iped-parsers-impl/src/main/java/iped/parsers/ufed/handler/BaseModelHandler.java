package iped.parsers.ufed.handler;

import static iped.properties.ExtraProperties.COMMUNICATION_DATE;
import static iped.properties.ExtraProperties.MESSAGE_BODY;
import static iped.properties.ExtraProperties.MESSAGE_SUBJECT;
import static iped.properties.ExtraProperties.UFED_JUMP_TARGETS;
import static iped.properties.ExtraProperties.UFED_META_PREFIX;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.ufed.model.BaseModel;
import iped.parsers.util.HashUtils;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

/**
 * Base handler for handling common operations on UFED models.
 * @param <T> The type of the BaseModel being processed.
 */
public class BaseModelHandler<T extends BaseModel> {

    private static final Logger logger = LoggerFactory.getLogger(BaseModelHandler.class);

    protected final T model;
    protected final IItemReader item;

    // if set to true, the item will be linked on addLinkedItems even if the item id is already set in jumpTargets
    private boolean skipJumpTargetsCheckOnAddLinkedItems = false;

    private static final HashSet<String> ignoreAttrs = new HashSet<>(Arrays.asList( //
            "type", //
            "path", //
            "size", //
            "deleted", //
            "deleted_state" //
    ));

    public static final HashSet<String> ignoreFields = new HashSet<>(Arrays.asList( //
            "Tags", //
            "CreationTime", //
            "ModifyTime", //
            "AccessTime", //
            "CoreFileSystemFileSystemNodeCreationTime", //
            "CoreFileSystemFileSystemNodeModifyTime", //
            "CoreFileSystemFileSystemNodeLastAccessTime", //
            "CoreFileSystemFileSystemNodeDeletedTime", //
            "UserMapping" //
    ));

    public BaseModelHandler(T model, IItemReader item) {
        this.model = model;
        this.item = item;
    }

    public BaseModelHandler(T model) {
        this(model, null);
    }

    public void setSkipJumpTargetsCheckOnAddLinkedItems(boolean skipJumpTargetsCheckOnAddLinkedItems) {
        this.skipJumpTargetsCheckOnAddLinkedItems = skipJumpTargetsCheckOnAddLinkedItems;
    }

    protected void fillMetadata(String prefix, Metadata metadata) {
        fillCommonMetadata(metadata);
    }

    /**
     * Fills all relevant metadata for the model into the Tika Metadata object.
     * This is the main entry point for a handler.
     */
    public final void fillMetadata(Metadata metadata) {
        fillMetadata("", metadata);
    }

    /**
     * Loads all indexed references for the model.
     */
    public final void loadReferences(IItemSearcher searcher) {
        if (model.isReferenceLoaded()) {
            return;
        }

        try {
            doLoadReferences(searcher);
        } catch (Exception e) {
            logger.error("Error loading references of model {}: {}", model, e.getMessage());
            logger.warn("", e);
        }

        model.setReferenceLoaded(true);
    }

    protected void doLoadReferences(IItemSearcher searcher) {
    }

    /**
     * Generates a title for the model.
     */
    public String getTitle() {
        return model.getModelType() + "_" + model.getId();
    }

    public void updateItemNameWithTitle() {
        if (item instanceof IItem) {
            IItem writableItem = (IItem) item;
            writableItem.setName(getTitle());
            writableItem.setExtension("");
        }
    }

    public final void addLinkedItemsAndSharedHashes(Metadata metadata, IItemSearcher searcher) {

        HashSet<String> newLinkedItems = new HashSet<>();
        HashSet<String> newSharedHashes = new HashSet<>();

        doAddLinkedItemsAndSharedHashes(newLinkedItems, newSharedHashes, searcher);

        updateLinkedItemsAndSharedHashes(metadata, newLinkedItems, newSharedHashes);
    }

    public final void addLinkedItemsAndSharedHashes(Set<String> linkedItems, Set<String> sharedHashes, IItemSearcher searcher) {
        doAddLinkedItemsAndSharedHashes(linkedItems, sharedHashes, searcher);
    }

    public static void updateLinkedItemsAndSharedHashes(Metadata metadata, Set<String> newLinkedItems, Set<String> newSharedHashes) {

        if (!newLinkedItems.isEmpty()) {
            Property linkedItemsProperty = Property.internalTextBag(ExtraProperties.LINKED_ITEMS);
            HashSet<String> linkedItems = new HashSet<>(Arrays.asList(metadata.getValues(linkedItemsProperty)));
            if (linkedItems.addAll(newLinkedItems)) {
                metadata.set(linkedItemsProperty, linkedItems.toArray(new String[] {}));
            }
        }

        if (!newSharedHashes.isEmpty()) {
            Property sharedHashesProperty = Property.internalTextBag(ExtraProperties.SHARED_HASHES);
            HashSet<String> sharedHashes = new HashSet<>(Arrays.asList(metadata.getValues(sharedHashesProperty)));
            if (sharedHashes.addAll(newLinkedItems)) {
                metadata.set(sharedHashesProperty, sharedHashes.toArray(new String[] {}));
            }
        }
    }

    protected void doAddLinkedItemsAndSharedHashes(Set<String> linkedItems, Set<String> sharedHashes, IItemSearcher searcher) {
    }

    protected final void addLinkedItem(Set<String> linkedItems, IItemReader referencedItem, IItemSearcher searcher) {

        String referencedUfedId = referencedItem.getMetadata().get(ExtraProperties.UFED_ID);

        // add linked items (if referencedUfedId not present in jumpTargets)
        if (StringUtils.isNotBlank(referencedUfedId)
                && (skipJumpTargetsCheckOnAddLinkedItems || !model.getJumpTargets().stream().anyMatch(j -> referencedUfedId.equals(j.getId())))) {
            String linkedItemQuery = searcher.escapeQuery(ExtraProperties.UFED_ID) + ":\"" + referencedUfedId + "\"";
            linkedItems.add(linkedItemQuery);
        }
    }

    protected final void addSharedHash(Set<String> sharedHashes, IItemReader sharedItem) {
        String sharedHash = sharedItem.getHash();
        if (HashUtils.isValidHash(sharedHash)) {
            sharedHashes.add(sharedHash);
        }
    }

    protected final void fillCommonMetadata(Metadata metadata) {

        // title
        if (metadata.get(TikaCoreProperties.TITLE) == null) {
            String title = getTitle();
            if (StringUtils.isNotBlank(title)) {
                metadata.set(TikaCoreProperties.TITLE, getTitle());
            }
        }

        // deleted
        if (model.isDeleted()) {
            metadata.set(ExtraProperties.DELETED, Boolean.toString(true));
        }

        // add attributes
        model.getAttributes().entrySet().stream()
            .filter(e -> !ignoreAttrs.contains(e.getKey()))
            .forEach(e -> {
                metadata.set(UFED_META_PREFIX + e.getKey(), e.getValue());
            });

        // add fields
        model.getFields().forEach((key, value) -> {
            fillFieldMetadata(key, value, metadata, Collections.emptySet());
        });

        // add additional info
        model.getAdditionalInfo().forEach(info -> {
            metadata.set(UFED_META_PREFIX + "Info:" +  info.getKey(), info.getValue());
        });

        // add jumpTargets
        model.getJumpTargets().forEach(jt -> {
            metadata.add(UFED_JUMP_TARGETS, jt.getId());
        });

        // add other model fields
        model.getOtherModelFields().forEach((fieldName, childs) -> {
            childs.forEach(child -> {
                child.getFields().forEach((key, value) -> {
                    fillFieldMetadata(fieldName + ":" + key, value, metadata, Collections.emptySet());
                });
            });
        });

        postProcessMetadata(metadata);
    }

    protected final void fillFieldMetadata(String key, Object value, Metadata metadata, Set<String> fieldsToIgnore) {

        if (fieldsToIgnore.contains(key) || ignoreFields.contains(key)) {
            return;
        }

        // avoid conflict between "Id" field and "id" attribute
        if ("Id".equals(key)) {
            key = model.getModelType() + key;
        }

        if (value instanceof Date) {
            metadata.add(UFED_META_PREFIX + key, DateUtils.formatDate((Date) value));
        } else {
            metadata.add(UFED_META_PREFIX + key, value.toString());
        }
    }

    private void postProcessMetadata(Metadata metadata) {
        if (StringUtils.equalsAny(model.getModelType(), "InstantMessage", "Email", "Call", "SMS", "MMS")) {

            String date = metadata.get(UFED_META_PREFIX + "TimeStamp");
            metadata.remove(UFED_META_PREFIX + "TimeStamp");
            metadata.set(COMMUNICATION_DATE, date);

            String subject = metadata.get(UFED_META_PREFIX + "Subject");
            metadata.remove(UFED_META_PREFIX + "Subject");
            metadata.set(MESSAGE_SUBJECT, subject);

            String body = metadata.get(UFED_META_PREFIX + "Body");
            metadata.remove(UFED_META_PREFIX + "Body");
            if (body == null) {
                body = metadata.get(UFED_META_PREFIX + "Snippet");
                metadata.remove(UFED_META_PREFIX + "Snippet");
                if (body != null) {
                    metadata.set(UFED_META_PREFIX + "isBodyFromSnippet", Boolean.toString(true));
                }
            }
            metadata.set(MESSAGE_BODY, body);
        }
    }

    public Metadata createMetadata() {
        Metadata meta = new Metadata();
        fillMetadata(meta);
        return meta;
    }
}