package gpinf.ares;

import java.util.Date;

public class AresEntry {
    private String hash, title, artist, album, category, year, language, url, comment, path, vdInfo, hashOfPHash,
            mimeType;
    private long fileSize;
    private Date date;
    private boolean shared, corrupted;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public boolean isCorrupted() {
        return corrupted;
    }

    public void setCorrupted(boolean corrupted) {
        this.corrupted = corrupted;
    }

    public String getPath() {
        return path;
    }

    public String getVdInfo() {
        return vdInfo;
    }

    public void setVdInfo(String vdInfo) {
        this.vdInfo = vdInfo;
    }

    public String getHashOfPHash() {
        return hashOfPHash;
    }

    public void setHashOfPHash(String hashOfPHash) {
        this.hashOfPHash = hashOfPHash;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("AresEntry [hash="); //$NON-NLS-1$
        builder.append(hash);
        builder.append(", title="); //$NON-NLS-1$
        builder.append(title);
        builder.append(", artist="); //$NON-NLS-1$
        builder.append(artist);
        builder.append(", album="); //$NON-NLS-1$
        builder.append(album);
        builder.append(", category="); //$NON-NLS-1$
        builder.append(category);
        builder.append(", year="); //$NON-NLS-1$
        builder.append(year);
        builder.append(", language="); //$NON-NLS-1$
        builder.append(language);
        builder.append(", url="); //$NON-NLS-1$
        builder.append(url);
        builder.append(", comment="); //$NON-NLS-1$
        builder.append(comment);
        builder.append(", path="); //$NON-NLS-1$
        builder.append(path);
        builder.append(", vdInfo="); //$NON-NLS-1$
        builder.append(vdInfo);
        builder.append(", hashOfPHash="); //$NON-NLS-1$
        builder.append(hashOfPHash);
        builder.append(", mimeType="); //$NON-NLS-1$
        builder.append(mimeType);
        builder.append(", fileSize="); //$NON-NLS-1$
        builder.append(fileSize);
        builder.append(", date="); //$NON-NLS-1$
        builder.append(date);
        builder.append(", shared="); //$NON-NLS-1$
        builder.append(shared);
        builder.append(", corrupted="); //$NON-NLS-1$
        builder.append(corrupted);
        builder.append("]"); //$NON-NLS-1$
        return builder.toString();
    }
}
