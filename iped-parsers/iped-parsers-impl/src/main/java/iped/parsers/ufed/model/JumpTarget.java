package iped.parsers.ufed.model;

import java.io.Serializable;
import java.util.StringJoiner;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Represents a <targetid> element within a <jumptargets> block.
 */
public class JumpTarget implements Serializable {

    private static final long serialVersionUID = 5023511640740911716L;

    private String id;
    private boolean isModel;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public boolean isModel() {
        return isModel;
    }
    public void setIsModel(boolean isModel) {
        this.isModel = isModel;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", JumpTarget.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("isModel=" + isModel)
                .toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JumpTarget)) {
            return false;
        }
        JumpTarget other = (JumpTarget) obj;
        return new EqualsBuilder() .append(id, other.id).append(isModel, other.isModel).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(getClass()).append(id).append(isModel).toHashCode();
    }
}