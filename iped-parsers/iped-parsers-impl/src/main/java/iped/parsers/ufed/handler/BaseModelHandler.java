package iped.parsers.ufed.handler;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.utils.DateUtils;

import iped.data.IItemReader;
import iped.parsers.ufed.model.BaseModel;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

/**
 * Base handler for handling common operations on UFED models.
 * @param <T> The type of the BaseModel being processed.
 */
public class BaseModelHandler<T extends BaseModel> {

    protected final T model;
    protected final IItemReader item;

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
            "UserMapping" //
    ));

    public BaseModelHandler(T model, IItemReader item) {
        this.model = model;
        this.item = item;
    }

    public BaseModelHandler(T model) {
        this(model, null);
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
    public void loadReferences(IItemSearcher searcher) {
    }

    /**
     * Generates a title for the model.
     */
    public String getTitle() {
        return model.getModelType() + "_" + model.getId();
    }


    protected void fillCommonMetadata(Metadata metadata) {

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
                metadata.set(ExtraProperties.UFED_META_PREFIX + e.getKey(), e.getValue());
            });

        // add fields
        model.getFields().forEach((key, value) -> {
            fillFieldMetadata(key, value, metadata, Collections.emptySet());
        });

        // add additional info
        model.getAdditionalInfo().forEach(info -> {
            metadata.set(ExtraProperties.UFED_META_PREFIX + "Info:" +  info.getKey(), info.getValue());
        });

        // add jumpTargets
        model.getJumpTargets().forEach(jt -> {
            metadata.add(ExtraProperties.UFED_JUMP_TARGETS, jt.getId());
        });
    }

    protected void fillFieldMetadata(String key, Object value, Metadata metadata, Set<String> fieldsToIgnore) {

        if (fieldsToIgnore.contains(key) || ignoreFields.contains(key)) {
            return;
        }

        // avoid conflict between "Id" field and "id" attribute
        if ("Id".equals(key)) {
            key = model.getModelType() + key;
        }

        if (value instanceof Date) {
            metadata.add(ExtraProperties.UFED_META_PREFIX + key, DateUtils.formatDate((Date) value));
        } else {
            metadata.add(ExtraProperties.UFED_META_PREFIX + key, value.toString());
        }
    }

    public Metadata createMetadata() {
        Metadata meta = new Metadata();
        fillMetadata(meta);
        return meta;
    }
}