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
    public String getFilename() { return getFieldAsString("Filename"); }
    public String getContentType() { return getFieldAsString("ContentType"); }
    public String getURL() { return getFieldAsString("URL"); }
    public String getTitle() { return getFieldAsString("Title"); }
    public String getTranscript() { return getFieldAsString("Transcript"); }
    public String getAttachmentExtractedPath() { return StringUtils.replaceChars(getFieldAsString("attachment_extracted_path"), '\\', '/'); }

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

    /**
     * @return true if the attachment content is available (referenced file or unreferenced content)
     */
    public boolean hasContent() {
        return referencedFile != null || unreferencedContent != null;
    }

    /**
     * Checks if both attachments represent the same file, e.g. an attachment present
     * both in a forwarded message and in its embedded message.
     */
    public boolean isSameAs(Attachment other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (StringUtils.isNoneBlank(fileId, other.fileId)) {
            return fileId.equals(other.fileId);
        }
        if (referencedFile != null && other.referencedFile != null) {
            if (referencedFile.getItem() == other.referencedFile.getItem()) {
                return true;
            }
            String hash = referencedFile.getHash();
            if (hash != null && hash.equalsIgnoreCase(other.referencedFile.getHash())) {
                return true;
            }
        }
        String path = getAttachmentExtractedPath();
        String otherPath = other.getAttachmentExtractedPath();
        if (StringUtils.isNoneBlank(path, otherPath)) {
            return path.equals(otherPath);
        }
        return StringUtils.isNoneBlank(getId(), other.getId()) && getId().equals(other.getId());
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
