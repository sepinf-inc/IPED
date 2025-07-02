package iped.parsers.ufed.model;

import java.util.StringJoiner;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.parsers.ufed.reference.ReferencedFile;


/**
 * Represents a <model type="Attachment"> element.
 */
public class Attachment extends BaseModel {

    private static final long serialVersionUID = -1061991406347039102L;

    private transient ReferencedFile referencedFile;
    private transient byte[] unreferencedContent;
    private String fileId;

    public Attachment() {
        super("Attachment");
    }

    // Specific field getters
    public String getFilename() { return (String) getField("Filename"); }
    public String getContentType() { return (String) getField("ContentType"); }
    public String getURL() { return (String) getField("URL"); }
    public String getTitle() { return (String) getField("Title"); }
    public String getTranscript() { return (String) getField("Transcript"); }
    public String getAttachmentExtractedPath() { return StringUtils.replaceChars((String) getField("attachment_extracted_path"), '\\', '/'); }

    public ReferencedFile getReferencedFile() {
        return referencedFile;
    }

    public void setReferencedFile(IItemReader fileItem) {
        this.referencedFile = new ReferencedFile(fileItem);
    }

    public byte[] getUnreferencedContent() {
        return unreferencedContent;
    }

    public void setUnreferencedContent(byte[] unreferencedContent) {
        this.unreferencedContent = unreferencedContent;
    }

    // externalized in a field to serialize in JSON
    public String getFileId() {
        return fileId;
    }

    @Override
    public void setAttribute(String name, String value) {
        super.setAttribute(name, value);
        if ("file_id".equals(name)) {
            fileId = value;
        }
    }

    public boolean isFileRelated() {
        return getFilename() != null || getAttachmentExtractedPath() != null;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Attachment.class.getSimpleName() + "[", "]")
                .add("id='" + getId() + "'")
                .add("file_id='" + getFileId() + "'")
                .add("Filename='" + getFilename() + "'")
                .add("ContentType='" + getMediaType() + "'")
                .toString();
    }
}
