package iped.parsers.leveldb.model;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.github.dedinc.leveldb4j.RawLevelDb;
import com.github.dedinc.leveldb4j.core.LdbFile;
import com.github.dedinc.leveldb4j.core.LogFile;
import com.github.dedinc.leveldb4j.core.ManifestFile;
import com.github.dedinc.leveldb4j.core.VersionEdit;

public class LDB4jReader {


    private static final long TABLE_MAGIC = 0xdb4775248b80fb57L;

    private LDB4jReader() {
    }

    public static void readSingle(Path file, RecordSink sink) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(sink, "sink");
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".ldb") || lower.endsWith(".sst")) {
                readTable(file, sink, null);
            } else if (lower.endsWith(".log")) {
                readLog(file, sink);
            } else if (name.startsWith("MANIFEST-")) {
                sink.onManifest(readManifest(file));
            } else {
                throw new IOException("Unsupported LevelDB file: " + file);
            }
        } catch (ConsumerFailure e) {
            throw e.consumerException;
        } catch (RuntimeException e) {
            throw new IOException("LevelDB4j single-file read failed: " + file, e);
        }
    }

    /**
     * Cheap LevelDB SSTable signature test; not a checksum or validity guarantee.
     * The actual decoding is delegated exclusively to LevelDB4j.
     */
    public static boolean looksLikeTable(Path file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            if (raf.length() < 48)
                return false;
            raf.seek(raf.length() - 8);
            long littleEndian = 0;
            for (int i = 0; i < 8; i++)
                littleEndian |= (raf.readUnsignedByte() & 0xffL) << (8 * i);
            return littleEndian == TABLE_MAGIC;
        }
    }

    /**
     * Forensic default: scan each physical file individually. This includes orphan
     * / obsolete tables and continues after a damaged file, without silently
     * suppressing exceptions raised by the IPED record consumer.
     */
    public static void readDirectory(Path directory, RecordSink sink) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(sink, "sink");
        requireDirectory(directory);
        Manifest manifest = readCurrentManifest(directory, sink);
        for (Path file : dataFiles(directory)) {
            try {
                String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (lower.endsWith(".log"))
                    readLog(file, sink);
                else
                    readTable(file, sink, manifest);
            } catch (ConsumerFailure e) {
                throw e.consumerException;
            } catch (IOException | RuntimeException e) {
                sink.onWarning("Unable to read " + file.getFileName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Alternative using the aggregate RawLevelDb API. Faster/convenient, but use
     * readDirectory() when individual-file recovery is more important. Failure
     * partway through this iteration is propagated, not retried, to avoid emitting
     * duplicate records.
     */
    public static void readDirectoryAggregated(Path directory, RecordSink sink) throws IOException {
        Objects.requireNonNull(sink, "sink");
        requireDirectory(directory);
        Manifest manifest = readCurrentManifest(directory, sink);
        try (RawLevelDb db = RawLevelDb.open(directory)) {
            for (com.github.dedinc.leveldb4j.core.Record raw : db.iterateRecordsRaw()) {
                emit(raw, raw.getOriginFile(), sink, manifest);
            }
        } catch (ConsumerFailure e) {
            throw e.consumerException;
        } catch (RuntimeException e) {
            throw new IOException("LevelDB4j aggregate scan failed: " + directory, e);
        }
    }

    private static void readTable(Path file, RecordSink sink, Manifest manifest) throws IOException {
        withCompatibleDataName(file, ".ldb", path -> {
            try (LdbFile table = new LdbFile(path)) {
                for (com.github.dedinc.leveldb4j.core.Record raw : table) {
                    emit(raw, file, sink, manifest);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void readLog(Path file, RecordSink sink) throws IOException {
        withCompatibleDataName(file, ".log", path -> {
            try (LogFile log = new LogFile(path)) {
                for (com.github.dedinc.leveldb4j.core.Record raw : log) {
                    emit(raw, file, sink, null);
                }
            }
        });
    }

    private static void emit(com.github.dedinc.leveldb4j.core.Record raw, Path fallback, RecordSink sink, Manifest manifest) throws IOException {
        Record r = new Record();
        r.source = fallback != null ? fallback : raw.getOriginFile();
        if (r.source == null)
            throw new IOException("LevelDB4j did not report an origin file");
        String filename = r.source.getFileName().toString().toLowerCase(Locale.ROOT);
        r.sourceType = filename.endsWith(".log") ? "WAL" : "SST";
        r.sequence = raw.getSeq();
        r.operation = raw.getState() == null ? "UNKNOWN" : ("LIVE".equals(raw.getState().name()) ? "PUT" : ("DELETED".equals(raw.getState().name()) ? "DELETE" : "UNKNOWN"));
        r.key = raw.getUserKey() == null ? new byte[0] : raw.getUserKey();
        r.value = raw.getValue() == null ? new byte[0] : raw.getValue();
        r.recordOffset = raw.getOffset();
        r.compressed = raw.wasCompressed();
        r.offsetKind = "WAL".equals(r.sourceType) ? "WAL_LIBRARY_ESTIMATE" : (r.compressed ? "SST_BLOCK_START" : "SST_ENTRY_PHYSICAL");
        if (manifest != null && r.sourceType.equals("SST") && manifest.complete) {
            long number = tableNumber(r.source);
            if (number >= 0) {
                r.level = manifest.activeTables.get(number);
                r.manifestActive = r.level != null;
            }
        }
        // Distinguish sink-initiated IOException (e.g. maxRecords reached)
        // from a corrupt-file read error in readDirectory().
        try {
            sink.onRecord(r);
        } catch (IOException e) {
            throw new ConsumerFailure(e);
        }
    }

    private static Manifest readCurrentManifest(Path directory, RecordSink sink) throws IOException {
        Path current = directory.resolve("CURRENT");
        if (!Files.isRegularFile(current)) {
            sink.onWarning("CURRENT absent; scanning all existing table/log files");
            return null;
        }
        if (Files.size(current) > 4096) {
            sink.onWarning("CURRENT unexpectedly large; scanning physical files without MANIFEST");
            return null;
        }
        String name = new String(Files.readAllBytes(current), StandardCharsets.US_ASCII).trim();
        if (!name.matches("MANIFEST-[0-9]+")) {
            sink.onWarning("Malformed CURRENT; scanning physical files without MANIFEST");
            return null;
        }
        Path target = directory.resolve(name);
        if (!Files.isRegularFile(target)) {
            sink.onWarning("Referenced MANIFEST missing: " + name);
            return null;
        }
        try {
            Manifest m = readManifest(target);
            sink.onManifest(m);
            return m;
        } catch (ConsumerFailure e) {
            throw e.consumerException;
        } catch (IOException | RuntimeException e) {
            sink.onWarning("Unable to read " + name + ": " + e.getMessage());
            return null;
        }
    }

    private static Manifest readManifest(Path file) throws IOException {
        Manifest result = new Manifest();
        result.source = file;
        try (ManifestFile manifest = new ManifestFile(file)) {
            // Do NOT use ManifestFile.getFileToLevel(): as implemented in
            // LevelDB4j 0.1.0, it accumulates additions but ignores deletions.
            for (VersionEdit edit : manifest) {
                result.edits++;
                if (edit.getDeletedFiles() != null) {
                    for (VersionEdit.DeletedFile deleted : edit.getDeletedFiles()) {
                        result.activeTables.remove(deleted.getFileNo());
                    }
                }
                if (edit.getNewFiles() != null) {
                    for (VersionEdit.NewFile added : edit.getNewFiles()) {
                        result.activeTables.put(added.getFileNo(), added.getLevel());
                    }
                }
                if (edit.getLogNumber() != null)
                    result.logNumber = edit.getLogNumber();
                if (edit.getPrevLogNumber() != null)
                    result.prevLogNumber = edit.getPrevLogNumber();
                if (edit.getLastSequence() != null)
                    result.lastSequence = edit.getLastSequence();
            }
        } catch (RuntimeException e) {
            throw new IOException("LevelDB4j MANIFEST parse failed: " + file, e);
        }
        return result;
    }

    @FunctionalInterface
    private interface FileReader {
        void read(Path file) throws IOException;
    }

    /**
     * LevelDB4j 0.1.0 parses data-file stems as hexadecimal int values. Carved
     * files can have arbitrary names. Stage only incompatible names, and report the
     * ORIGINAL source path in every resulting record.
     */
    private static void withCompatibleDataName(Path original, String fallbackExt, FileReader reader) throws IOException {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        boolean compatible = false;
        if (dot > 0) {
            try {
                Integer.parseInt(name.substring(0, dot), 16);
                compatible = true;
            } catch (NumberFormatException ignored) {
            }
        }
        if (compatible) {
            reader.read(original);
            return;
        }
        Path tempDir = Files.createTempDirectory("leveldb4j-carved-");
        Path alias = tempDir.resolve("000000" + (name.toLowerCase(Locale.ROOT).endsWith(".sst") ? ".sst" : fallbackExt));
        try {
            Files.copy(original, alias);
            reader.read(alias);
        } finally {
            try {
                Files.deleteIfExists(alias);
            } finally {
                Files.deleteIfExists(tempDir);
            }
        }
    }

    private static long tableNumber(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        if (dot < 1)
            return -1;
        try {
            return Long.parseLong(name.substring(0, dot));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void requireDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory))
            throw new IOException("Not a directory: " + directory);
    }

    private static List<Path> dataFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path))
                    continue;
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.matches("[0-9]+\\.(ldb|sst|log)"))
                    files.add(path);
            }
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return files;
    }

    private static final class ConsumerFailure extends IOException {
        private static final long serialVersionUID = 1L;
        final IOException consumerException;

        ConsumerFailure(IOException cause) {
            super(cause);
            this.consumerException = cause;
        }
    }

}
