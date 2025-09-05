package iped.parsers.ufed.model;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MediaType;

import iped.properties.MediaTypes;

/**
 * A base class for all model objects parsed from the XML.
 * It holds common attributes and a map for generic fields.
 */
public abstract class BaseModel implements Serializable {

    private static final long serialVersionUID = -5923250360810627885L;

    private final String modelType;
    private String id;
    private final Map<String, Object> fields = new TreeMap<>(new FieldsComparator());
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<KeyValueModel> additionalInfo = new ArrayList<>();
    private final Set<JumpTarget> jumpTargets = new HashSet<>();
    private final List<BaseModel> relatedModels = new ArrayList<>();
    private final Map<String, List<BaseModel>> otherModelFields = new LinkedHashMap<>();

    private transient boolean referenceLoaded = false;

    public static enum DeletedState {
        Unknown, Intact, Deleted, Missed, Trash;

        public static DeletedState parse(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return Unknown;
            }
        }
    }

    public BaseModel(String modelType) {
        this.modelType = modelType;
    }

    public Object getField(String name) {
        return fields.get(name);
    }

    public Map<String, Object> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public void setField(String name, Object value) {

        // handle field value
        switch (name) {
        case "attachment_extracted_path":
            value = StringUtils.replaceChars((String) value, '\\', '/');
            break;

        default:
            break;
        }
        this.fields.put(name, value);
    }

    public void setAttribute(String name, String value) {
        this.attributes.put(name, value);
    }

    public String getAttribute(String name) {
        return this.attributes.get(name);
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public List<KeyValueModel> getAdditionalInfo() {
        return additionalInfo;
    }

    public Set<JumpTarget> getJumpTargets() {
        return jumpTargets;
    }

    public List<BaseModel> getRelatedModels() {
        return relatedModels;
    }

    public Map<String, List<BaseModel>> getOtherModelFields() {
        return otherModelFields;
    }

    // Specific attribute getters for convenience
    public String getDeletedState() {
        return getAttribute("deleted_state");
    }

    public String getDecodingConfidence() {
        return getAttribute("decoding_confidence");
    }

    public boolean isRelated() {
        return Boolean.parseBoolean(getAttribute("isrelated"));
    }

    public boolean isDeleted() {
        return StringUtils.equalsAnyIgnoreCase(getDeletedState(), "Trash", "Deleted");
    }

    public int getSourceIndex() {
        try {
            return Integer.parseInt(getAttribute("source_index"));
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    public int getExtractionId() {
        try {
            return Integer.parseInt(getAttribute("extractionId"));
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    public MediaType getMediaType() {
        return MediaType.application(MediaTypes.UFED_MIME_PREFIX + modelType);
    }

    // --- Field Methods ---

    // --- Common Getters/Setters ---
    public String getModelType() {
        return modelType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isReferenceLoaded() {
        return referenceLoaded;
    }

    public void setReferenceLoaded(boolean referenceLookedUp) {
        this.referenceLoaded = referenceLookedUp;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("modelType='" + modelType + "'")
                .toString();
    }

    private static class FieldsComparator implements Comparator<String> {

        private static final Map<String, Integer> rankingMap = new HashMap<>();
        static {
            int ranking = 0;
            rankingMap.put("Source", ranking++);
            rankingMap.put("Service Type", ranking++);
            rankingMap.put("Service Identifier", ranking++);
            rankingMap.put("Account", ranking++);
            rankingMap.put("Type", ranking++);
            rankingMap.put("Name", ranking++);
            rankingMap.put("User ID", ranking++);
            rankingMap.put("Username", ranking++);
        }

        @Override
        public int compare(String field1, String field2) {
            Integer ranking1 = rankingMap.get(field1);
            Integer ranking2 = rankingMap.get(field2);

            if (ranking1 == null && ranking2 == null) return field1.compareTo(field2);
            if (ranking1 == null) return 1;
            if (ranking2 == null) return -1;

            return Integer.compare(ranking1, ranking2);
        }
    }
}

