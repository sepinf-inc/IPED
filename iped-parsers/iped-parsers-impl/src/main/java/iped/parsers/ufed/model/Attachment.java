package iped.parsers.ufed.model;

import static iped.parsers.ufed.util.UfedUtils.readUfedMetadata;

import java.util.StringJoiner;

import iped.data.IItemReader;
import iped.parsers.ufed.reference.ReferencedFile;


/**
 * Represents a <model type="Attachment"> element.
 */
public class Attachment extends BaseModel {

    private static final long serialVersionUID = -1061991406347039102L;

    private transient ReferencedFile referencedFile;

    public Attachment() {
        super("Attachment");
    }

    // Specific attribute getter
    public String getFileId() { return getAttribute("file_id"); }

    // Specific field getters
    public String getFilename() { return (String) getField("Filename"); }
    public String getUfedContentType() { return (String) getField("ContentType"); }
    public String getURL() { return (String) getField("URL"); }
    public String getTitle() { return (String) getField("Title"); }
    public String getTranscript() { return (String) getField("Transcript"); }
    public String getAttachmentExtractedPath() { return (String) getField("attachment_extracted_path"); }

    public ReferencedFile getReferencedFile() {
        return referencedFile;
    }

    public void setReferencedFile(IItemReader fileItem) {
        String fileItemId = readUfedMetadata(fileItem, "id");
        if (!fileItemId.equals(getFileId())) {
            throw new IllegalArgumentException("Ufed file_id doesn't match: " + getFileId() + " x " + fileItemId);
        }
        this.referencedFile = new ReferencedFile(fileItem);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Attachment.class.getSimpleName() + "[", "]")
                .add("id='" + getId() + "'")
                .add("file_id='" + getFileId() + "'")
                .add("Filename='" + getFilename() + "'")
                .add("ContentType='" + getContentType() + "'")
                .toString();
    }
}
