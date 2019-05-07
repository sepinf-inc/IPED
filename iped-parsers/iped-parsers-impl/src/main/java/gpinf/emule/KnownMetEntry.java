package gpinf.emule;

import java.util.Date;

public class KnownMetEntry {
    private String hash, name, fileType, partName;
    private Date lastModified, lastPublishedKad, lastShared;
    private long fileSize = -1, bytesTransfered = -1;
    private int totalRequests = -1, acceptedRequests = -1;

    public String getPartName() {
        return partName;
    }

    public void setPartName(String partName) {
        this.partName = partName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public Date getLastPublishedKad() {
        return lastPublishedKad;
    }

    public void setLastPublishedKad(Date lastPublishedKad) {
        this.lastPublishedKad = lastPublishedKad;
    }

    public Date getLastShared() {
        return lastShared;
    }

    public void setLastShared(Date lastShared) {
        this.lastShared = lastShared;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getBytesTransfered() {
        return bytesTransfered;
    }

    public void setBytesTransfered(long bytesTransfered) {
        this.bytesTransfered = bytesTransfered;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(int totalRequests) {
        this.totalRequests = totalRequests;
    }

    public int getAcceptedRequests() {
        return acceptedRequests;
    }

    public void setAcceptedRequests(int acceptedRequests) {
        this.acceptedRequests = acceptedRequests;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("KnownMetEntry [acceptedRequests="); //$NON-NLS-1$
        builder.append(acceptedRequests);
        builder.append(", bytesTransfered="); //$NON-NLS-1$
        builder.append(bytesTransfered);
        builder.append(", fileType="); //$NON-NLS-1$
        builder.append(fileType);
        builder.append(", hash="); //$NON-NLS-1$
        builder.append(hash);
        builder.append(", lastModified="); //$NON-NLS-1$
        builder.append(lastModified);
        builder.append(", lastPublishedKad="); //$NON-NLS-1$
        builder.append(lastPublishedKad);
        builder.append(", lastShared="); //$NON-NLS-1$
        builder.append(lastShared);
        builder.append(", fileSize="); //$NON-NLS-1$
        builder.append(fileSize);
        builder.append(", name="); //$NON-NLS-1$
        builder.append(name);
        builder.append(", totalRequests="); //$NON-NLS-1$
        builder.append(totalRequests);
        builder.append(", partName="); //$NON-NLS-1$
        builder.append(partName);
        builder.append("]"); //$NON-NLS-1$
        return builder.toString();
    }
}
