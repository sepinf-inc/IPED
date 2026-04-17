package iped.bfac.api;

import iped.data.IItemId;

/**
 * Represents file hash information to be sent to the BFAC backend.
 */
public class FileHashInfo {

    private String fileName;
    private long fileSize;
    private String md5;
    private String sha1;
    private String sha256;
    private String ed2k;
    private String mimeType;
    private String filePath;
    private IItemId itemId;

    public FileHashInfo() {
    }

    public FileHashInfo(String fileName, long fileSize, String md5, String sha1, String sha256) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.md5 = md5;
        this.sha1 = sha1;
        this.sha256 = sha256;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getSha1() {
        return sha1;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getEd2k() {
        return ed2k;
    }

    public void setEd2k(String ed2k) {
        this.ed2k = ed2k;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public IItemId getItemId() {
        return itemId;
    }

    public void setItemId(IItemId itemId) {
        this.itemId = itemId;
    }
}
