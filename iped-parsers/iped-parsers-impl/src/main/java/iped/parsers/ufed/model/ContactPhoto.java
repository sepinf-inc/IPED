package iped.parsers.ufed.model;

import java.util.StringJoiner;

/**
 * Represents a <model type="ContactPhoto"> element.
 */
public class ContactPhoto extends BaseModel {

    private static final long serialVersionUID = -7306239929532046153L;

    private transient byte[] imageData;

    public ContactPhoto() {
        super("ContactPhoto");
    }

    // Specific field getters
    public String getName() { return (String) getField("Name"); }
    public String getPhotoNodeId() { return (String) getField("PhotoNodeId"); }

    public byte[] getImageData() {
        return imageData;
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
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