package iped.parsers.ufed.model;

import java.util.StringJoiner;

/**
 * Represents a <model type="Attachment"> element.
 */
public class Attachment extends BaseModel {

    private static final long serialVersionUID = -1061991406347039102L;

    public Attachment() {
        super("Attachment");
    }

    // Specific attribute getter
    public String getFileId() { return getAttribute("file_id"); }

    // Specific field getters
    public String getFilename() { return (String) getField("Filename"); }
    public String getContentType() { return (String) getField("ContentType"); }
    public String getURL() { return (String) getField("URL"); }
    public String getTitle() { return (String) getField("Title"); }
    public String getTranscript() { return (String) getField("Transcript"); }
    public String getAttachmentExtractedPath() { return (String) getField("attachment_extracted_path"); }

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
