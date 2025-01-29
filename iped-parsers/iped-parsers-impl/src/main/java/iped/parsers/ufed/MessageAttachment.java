package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;

import iped.data.IItemReader;

public class MessageAttachment extends AbstractMessageSubitem {

    private ReferencedFile referencedFile;

    public MessageAttachment(IItemReader item) {
        super(item);
    }

    public String getTitle() {
        return readUfedMetadata(item, "Title");
    }

    public String getUrl() {
        return readUfedMetadata(item, "URL");
    }

    public String getContentType() {
        return readUfedMetadata(item, "ContentType");
    }

    public String getFileName() {
        return readUfedMetadata(item, "Filename");
    }

    public String getFileId() {
        return readUfedMetadata(item, "file_id");
    }

    public ReferencedFile getReferencedFile() {
        return referencedFile;
    }

    public void setReferencedFile(IItemReader fileItem) {
        String fileItemId = readUfedMetadata(fileItem, "id");
        if (!fileItemId.equals(getFileId())) {
            throw new IllegalArgumentException("Ufed file_id doesn't match: " + fileItemId + " x " + getFileId());
        }
        this.referencedFile = new ReferencedFile(fileItem);
    }

}
