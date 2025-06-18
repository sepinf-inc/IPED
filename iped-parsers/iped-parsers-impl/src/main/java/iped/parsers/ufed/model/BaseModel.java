package iped.parsers.ufed.model;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

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
    private final Map<String, Object> fields = new HashMap<>();
    private final Map<String, String> attributes = new HashMap<>();
    private final List<JumpTarget> jumpTargets = new ArrayList<>();

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

    public List<JumpTarget> getJumpTargets() {
        return jumpTargets;
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

    public MediaType getContentType() {
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


    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("modelType='" + modelType + "'")
                .toString();
    }
}

