package iped.parsers.leveldb.model;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Manifest {
    public Path source;
    public final Map<Long, Integer> activeTables = new HashMap<>();
    public long logNumber = -1;
    public long prevLogNumber = -1;
    public long lastSequence = -1;
    public int edits;
    /**
     * Means LevelDB4j completed iteration, not independently verified
     * CRC/integrity.
     */
    public boolean complete = true;
}