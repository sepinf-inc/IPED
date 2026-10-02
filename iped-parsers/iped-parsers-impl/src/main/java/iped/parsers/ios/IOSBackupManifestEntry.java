package iped.parsers.ios;

import java.util.Arrays;

final class IOSBackupManifestEntry {

    enum Type {
        FILE,
        DIRECTORY,
        SYMBOLIC_LINK,
        UNKNOWN
    }

    private static final int FILE_FLAG = 1;
    private static final int DIRECTORY_FLAG = 2;
    private static final int SYMBOLIC_LINK_FLAG = 4;

    private final String fileId;
    private final String domain;
    private final String relativePath;
    private final int flags;
    private final byte[] metadata;

    IOSBackupManifestEntry(String fileId, String domain, String relativePath, int flags, byte[] metadata) {
        this.fileId = fileId;
        this.domain = domain;
        this.relativePath = relativePath;
        this.flags = flags;
        this.metadata = metadata == null ? null : Arrays.copyOf(metadata, metadata.length);
    }

    String getFileId() {
        return fileId;
    }

    String getDomain() {
        return domain;
    }

    String getRelativePath() {
        return relativePath;
    }

    int getFlags() {
        return flags;
    }

    byte[] getMetadata() {
        return metadata == null ? null : Arrays.copyOf(metadata, metadata.length);
    }

    int getMetadataSize() {
        return metadata == null ? 0 : metadata.length;
    }

    Type getType() {
        switch (flags) {
            case FILE_FLAG:
                return Type.FILE;
            case DIRECTORY_FLAG:
                return Type.DIRECTORY;
            case SYMBOLIC_LINK_FLAG:
                return Type.SYMBOLIC_LINK;
            default:
                return Type.UNKNOWN;
        }
    }
}
