package iped.parsers.ufed;

import static iped.parsers.ufed.UfedUtils.readUfedMetadata;

import iped.data.IItemReader;

public class Attachment implements Comparable<Attachment> {

    private IItemReader item;
    private File file;

    public Attachment(IItemReader item) {
        this.item = item;
    }

    public IItemReader getItem() {
        return item;
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

    public File getFile() {
        return file;
    }

    public void setFile(IItemReader fileItem) {
        String fileItemId = readUfedMetadata(fileItem, "id");
        if (!fileItemId.equals(getFileId())) {
            throw new IllegalArgumentException("Ufed file_id don't match: " + fileItemId + " x " + getFileId());
        }
        this.file = new File(fileItem);
    }

    @Override
    public int compareTo(Attachment o) {

        String thisIndex = readUfedMetadata(this.item, "source_index");
        String otherIndex = readUfedMetadata(o.item, "source_index");
        try {
            return Integer.parseInt(thisIndex) - Integer.parseInt(otherIndex);
        } catch (NumberFormatException | NullPointerException e) {
        }

        return 0;
    }

}
