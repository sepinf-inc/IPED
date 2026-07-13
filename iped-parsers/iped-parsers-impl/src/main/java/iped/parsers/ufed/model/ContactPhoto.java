package iped.parsers.ufed.model;

import java.util.Optional;
import java.util.StringJoiner;

import iped.data.IItemReader;
import iped.parsers.ufed.reference.ReferencedFile;

/**
 * Represents a <model type="ContactPhoto"> element.
 */
public class ContactPhoto extends BaseModel {

    private static final long serialVersionUID = -7306239929532046153L;

    private transient Optional<ReferencedFile> referencedFile = Optional.empty();

    public ContactPhoto() {
        super("ContactPhoto");
    }

    // Specific field getters
    public String getName() { return (String) getField("Name"); }
    public String getPhotoNodeId() { return (String) getField("PhotoNodeId"); }
    public String getUrl() { return (String) getField("Url"); }

    public Optional<ReferencedFile> getReferencedFile() {
        return referencedFile;
    }

    public void setReferencedFile(IItemReader fileItem) {
        this.referencedFile = Optional.of(new ReferencedFile(fileItem));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ContactPhoto.class.getSimpleName() + "[", "]")
                .add("id='" + getId() + "'")
                .add("Name='" + getName() + "'")
                .add("PhotoNodeId='" + getPhotoNodeId() + "'")
                .toString();
    }
}