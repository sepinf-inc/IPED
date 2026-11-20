package iped.parsers.leveldb.model;

import java.nio.file.Path;

/**
 * IPED-facing record shape, retaining compatibility with the first prototype.
 */
public class Record {
    public Path source;
    public String sourceType;
    public long sequence;
    /** PUT, DELETE, or UNKNOWN; PUT does not imply currently active. */
    public String operation;
    public byte[] key;
    public byte[] value;
    /**
     * Offset reported by LevelDB4j Record.getOffset(); semantics are
     * library-defined.
     */
    public long recordOffset = -1;
    /** SST_ENTRY_PHYSICAL, SST_BLOCK_START, or WAL_LIBRARY_ESTIMATE. */
    public String offsetKind;
    /** Not available as a separate value from LevelDB4j. */
    @Deprecated
    public long blockOffset = -1;
    /** Not available as a separate value from LevelDB4j. */
    @Deprecated
    public long entryOffset = -1;
    public boolean compressed;
    /**
     * Null means unknown; false means the table is absent from selected MANIFEST.
     */
    public Boolean manifestActive;
    public Integer level;
}