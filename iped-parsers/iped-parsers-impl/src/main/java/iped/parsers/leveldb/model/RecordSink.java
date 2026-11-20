package iped.parsers.leveldb.model;

import java.io.IOException;

public interface RecordSink {
    void onRecord(Record record) throws IOException;

    default void onWarning(String message) throws IOException {
        System.err.println("WARNING: " + message);
    }

    default void onManifest(Manifest manifest) throws IOException {
    }
}
