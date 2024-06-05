package iped.engine.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

import iped.engine.util.Util;
import iped.utils.StringUtil;

public class GraphFileWriter implements Closeable, Flushable {

    public static final String NODE_CSV_PREFIX = "nodes";
    public static final String REPLACE_NAME = "replace.csv";

    private static final String REL_CSV_PREFIX = "relationships";
    private static final String HEADER_CSV_STR = "_headers_";
    private static final String SUFFIX = "iped";
    private static final String ARG_FILE_NAME = GraphImportRunner.ARGS_FILE_NAME + "-" + SUFFIX + ".txt";

    private Map<String, CSVWriter> nodeWriters = new HashMap<>();
    private Map<String, CSVWriter> relationshipWriters = new HashMap<>();

    private Map<String, String> replaces = new HashMap<>();
    private File replaceFile;

    private File root;

    public GraphFileWriter(File root, String defaultEntity) throws Exception {
        super();
        this.root = root;
        root.mkdirs();
        // needed for --append
        uncompressPreviousCSVFiles();
        initReplaceWriter(root);
        if (defaultEntity != null) {
            configureDefaultEntityFields(defaultEntity);
        }
        openExistingCSVs(root);
    }

    private void initReplaceWriter(File root) {
        try {
            replaceFile = new File(root, REPLACE_NAME);
            if (replaceFile.exists()) {
                replaces = loadReplaces();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void configureDefaultEntityFields(String defaultEntity) {
        try {
            CSVWriter writer = getNodeWriter(DynLabel.label(defaultEntity));
            writer.fieldPositions
                    .addAll(Arrays.asList("nodeId", "label", "path", "evidenceId", "name", "categories", "hash"));

            writer.fieldTypes.put("nodeId", "ID");
            writer.fieldTypes.put("label", "LABEL");
            writer.fieldTypes.put("path", "string");
            writer.fieldTypes.put("evidenceId", "string");
            writer.fieldTypes.put("name", "string");
            writer.fieldTypes.put("category", "string");
            writer.fieldTypes.put("type", "string");
            writer.fieldTypes.put("hash", "string");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void openExistingCSVs(File root) {
        try {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    String[] frags = file.getName().split(CSVWriter.SEPARATOR);
                    if (frags.length != 3 || file.getName().contains(HEADER_CSV_STR))
                        continue;
                    if (NODE_CSV_PREFIX.equals(frags[0])) {
                        getNodeWriter(DynLabel.label(frags[1]));
                    }
                    if (REL_CSV_PREFIX.equals(frags[0])) {
                        getRelationshipWriter(DynRelationshipType.withName(frags[1]));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CSVWriter openNodeWriter(Label... labels) throws IOException {
        CSVWriter writer = new CSVWriter(root, NODE_CSV_PREFIX, labels, SUFFIX);

        writer.fieldPositions.addAll(Arrays.asList("nodeId", "label"));
        writer.fieldTypes.put("nodeId", "ID");
        writer.fieldTypes.put("label", "LABEL");

        return writer;
    }

    private CSVWriter openRelationshipWriter(RelationshipType type) throws IOException {
        CSVWriter writer = new CSVWriter(root, REL_CSV_PREFIX, type.name(), SUFFIX);

        writer.fieldPositions.addAll(
                Arrays.asList("start", "end", "type", GraphTask.RELATIONSHIP_SOURCE, GraphTask.RELATIONSHIP_ID));
        writer.fieldTypes.put("start", "START_ID");
        writer.fieldTypes.put("end", "END_ID");
        writer.fieldTypes.put("type", "TYPE");
        writer.fieldTypes.put(GraphTask.RELATIONSHIP_SOURCE, "string");
        writer.fieldTypes.put(GraphTask.RELATIONSHIP_ID, "string");
        return writer;
    }

    private synchronized CSVWriter getRelationshipWriter(RelationshipType type) throws IOException {
        CSVWriter out = relationshipWriters.get(type.name());
        if (out == null) {
            out = openRelationshipWriter(type);
            relationshipWriters.put(type.name(), out);
        }
        return out;
    }

    private synchronized CSVWriter getNodeWriter(Label... labels) throws IOException {
        String labelsNames = CSVWriter.join(labels);
        CSVWriter out = nodeWriters.get(labelsNames);
        if (out == null) {
            out = openNodeWriter(labels);
            nodeWriters.put(labelsNames, out);
        }
        return out;
    }

    public void writeArgsFile() throws IOException {
        File file = new File(root, ARG_FILE_NAME);
        file.delete();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), Charset.forName("utf-8")))) {

            writeArgs(nodeWriters.values(), writer, NODE_CSV_PREFIX);
            writeArgs(relationshipWriters.values(), writer, REL_CSV_PREFIX);
        }
    }

    private void writeArgs(Collection<CSVWriter> outs, BufferedWriter writer, String type) throws IOException {
        for (CSVWriter out : outs) {
            writeArgs(out, writer, type);
        }
    }

    private void writeArgs(CSVWriter out, BufferedWriter writer, String type) throws IOException {
        File headerFile = writeHeaderFile(out);
        File dataFile = out.getOutput();
        writer.write("--");
        writer.write(type);
        writer.write("=");
        writer.write(headerFile.getName());
        writer.write(",");
        writer.write(dataFile.getName());
        writer.write("\r\n");
    }

    private File writeHeaderFile(CSVWriter out) throws IOException {
        String fileName = out.getPrefix() + CSVWriter.SEPARATOR + out.getName() + HEADER_CSV_STR + out.getSuffix()
                + ".csv";
        File file = new File(root, fileName);
        List<String> fields = new ArrayList<>(out.getFieldPositions());
        Map<String, String> types = out.getFieldTypes();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), Charset.forName("utf-8")))) {
            List<String> headers = new ArrayList<>(fields.size());
            for (String field : fields) {
                String type = types.get(field);
                if (type == null) {
                    type = "string";
                }
                headers.add(field + ":" + type);
            }
            String header = headers.stream().collect(Collectors.joining(","));
            writer.write(header);
        }
        return file;
    }

    public String writeCreateNode(String uniquePropertyName, Object uniquePropertyValue, Map<String, Object> properties,
            Label label, Label... labels) throws IOException {
        String id = uniqueId(label, uniquePropertyName, uniquePropertyValue.toString());
        List<Label> list = new ArrayList<>(Arrays.asList(labels));
        list.add(label);

        String labelsNames = list.stream().map(l -> l.name()).collect(Collectors.joining(";"));
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("nodeId", id);
        record.put("label", labelsNames);
        record.putAll(properties);

        CSVWriter out = getNodeWriter(label);
        out.write(record);
        return id;
    }

    @SuppressWarnings("unchecked")
    public String writeNode(Label label, String uniquePropertyName, Object uniquePropertyValue,
            Map<String, Object> properties) throws IOException {
        String id = uniqueId(label, uniquePropertyName, uniquePropertyValue.toString());
        Object val = properties.get(uniquePropertyName);
        if (val instanceof Collection) {
            ((Collection<Object>) val).add(uniquePropertyValue);
        } else {
            if (val != null && !val.equals(uniquePropertyValue))
                properties.put(uniquePropertyName, Arrays.asList(uniquePropertyValue, val));
            else if (val == null)
                properties.put(uniquePropertyName, uniquePropertyValue);
        }
        writeNodeId(id, label, properties);
        return id;
    }

    public String writeNode(Label label, String uniquePropertyName, Object uniquePropertyValue) throws IOException {
        HashMap<String, Object> properties = new HashMap<>();
        return writeNode(label, uniquePropertyName, uniquePropertyValue, properties);
    }

    public void writeRelationship(Label label1, String idProperty1, Object propertyValue1, Label label2,
            String idProperty2, Object propertyValue2, RelationshipType relationshipType,
            Map<String, Object> properties) throws IOException {
        String uniqueId1 = uniqueId(label1, idProperty1, propertyValue1.toString());
        writeRelationship(uniqueId1, label2, idProperty2, propertyValue2, relationshipType, properties);
    }

    public void writeRelationship(String uniqueId1, Label label2, String idProperty2, Object propertyValue2,
            RelationshipType relationshipType, Map<String, Object> properties) throws IOException {
        String uniqueId2 = uniqueId(label2, idProperty2, propertyValue2.toString());
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("start", uniqueId1);
        record.put("end", uniqueId2);
        record.put("type", relationshipType.name());
        record.putAll(properties);

        CSVWriter out = getRelationshipWriter(relationshipType);
        out.write(record);

    }

    private static String getLastReplace(Map<String, String> replaces, String id) {
        String tmp, key = id;
        while ((tmp = replaces.get(key)) != null) {
            key = tmp;
        }
        return key;
    }

    public void writeNodeReplace(Label label, String propName, Object propValue, String nodeId) throws IOException {
        String uniqueId1 = uniqueId(label, propName, propValue.toString());
        synchronized (replaces) {
            if (!uniqueId1.equals(nodeId)) {
                String key = getLastReplace(replaces, uniqueId1);
                String replace = getLastReplace(replaces, nodeId);
                if (!key.equals(replace)) {
                    replaces.put(key, replace);
                }
            }
        }
    }

    public void compressGeneratedCSVFiles() throws Exception {
        compressGeneratedCSVFiles(this.root);
    }

    private static void compressGeneratedCSVFiles(File root) throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        ArrayList<Future<?>> futures = new ArrayList<>();
        for (File f : root.listFiles()) {
            Runnable r = new Runnable() {
                public void run() {
                    File gzip = new File(f.getAbsolutePath() + ".gzip");
                    try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(gzip.toPath()))) {
                        Files.copy(f.toPath(), gzos);
                        f.delete();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            futures.add(executor.submit(r));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();
    }

    public void uncompressPreviousCSVFiles() throws Exception {
        uncompressPreviousCSVFiles(this.root);
    }

    private static void uncompressPreviousCSVFiles(File root) throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        ArrayList<Future<?>> futures = new ArrayList<>();
        for (File f : root.listFiles()) {
            int idx = f.getAbsolutePath().lastIndexOf(".gzip");
            if (idx == -1) {
                continue;
            }
            Runnable r = new Runnable() {
                public void run() {
                    File csv = new File(f.getAbsolutePath().substring(0, idx));
                    try (GZIPInputStream gzis = new GZIPInputStream(Files.newInputStream(f.toPath()))) {
                        Files.copy(gzis, csv.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    f.delete();
                }
            };
            futures.add(executor.submit(r));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();
    }

    public static int removeDeletedRelationships(String evidenceUUID, File csvRoot) throws Exception {
        if (!csvRoot.exists()) {
            return 0;
        }
        int deleted = 0;
        uncompressPreviousCSVFiles(csvRoot);
        for (File f : csvRoot.listFiles()) {
            if (f.getName().startsWith(REL_CSV_PREFIX) && f.getName().endsWith(".csv")) {
                File dest = new File(f.getAbsolutePath() + ".tmp");
                try (BufferedReader reader = Files.newBufferedReader(f.toPath());
                        Writer writer = Files.newBufferedWriter(dest.toPath())) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.contains(evidenceUUID)) {
                            writer.write(line);
                            writer.write("\r\n");
                        } else {
                            deleted++;
                        }
                    }
                }
                Files.move(dest.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        compressGeneratedCSVFiles(csvRoot);
        return deleted;
    }

    // TODO improve to merge duplicate nodes instead of just skip
    public static void prepareMultiCaseCSVs(File output, List<File> csvParents) throws Exception {
        AtomicInteger subDir = new AtomicInteger(-1);
        Set<String> ids = Collections.synchronizedSet(new HashSet<>());
        ExecutorService executor = Executors.newCachedThreadPool();
        ArrayList<Future<?>> futures = new ArrayList<>();
        for (File parent : csvParents) {
            Runnable r = new Runnable() {
                public void run() {
                    int num = subDir.incrementAndGet();
                    try {
                        File[] subFiles = parent.listFiles();
                        if (subFiles == null)
                            return;
                        for (File input : subFiles) {
                            File dest = new File(output, num + File.separator + input.getName().replace(".gzip", ""));
                            dest.getParentFile().mkdirs();
                            if (input.getName().startsWith(NODE_CSV_PREFIX) && !input.getName().contains(HEADER_CSV_STR) && input.getName().endsWith(".csv.gzip")) {
                                try (BufferedWriter writer = Files.newBufferedWriter(dest.toPath(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                                        BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(input.toPath())), StandardCharsets.UTF_8))) {
                                    String line = null;
                                    while ((line = reader.readLine()) != null) {
                                        String id = line.substring(0, line.indexOf(','));
                                        if (ids.add(id)) {
                                            writer.write(line);
                                            writer.write("\r\n");
                                        }
                                    }
                                }
                            } else
                                try (GZIPInputStream gzis = new GZIPInputStream(Files.newInputStream(input.toPath()))) {
                                    Files.copy(gzis, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                }
                        }
                        File importArgs = new File(output, num + "/" + ARG_FILE_NAME);
                        String args = new String(Files.readAllBytes(importArgs.toPath()), StandardCharsets.UTF_8);
                        args = args.replace("=", "=" + num + "/").replace(",", "," + num + "/");
                        Files.write(importArgs.toPath(), args.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);

                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            futures.add(executor.submit(r));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();
    }

    public void writeCreateRelationship(Label label1, String idProperty1, Object propertyValue1, Label label2,
            String idProperty2, Object propertyValue2, RelationshipType relationshipType) throws IOException {
        writeRelationship(label1, idProperty1, propertyValue1, label2, idProperty2, propertyValue2, relationshipType,
                Collections.emptyMap());
    }

    public void writeCreateRelationship(String uniqueId1, Label label2, String idProperty2, Object propertyValue2,
            RelationshipType relationshipType) throws IOException {
        writeRelationship(uniqueId1, label2, idProperty2, propertyValue2, relationshipType, Collections.emptyMap());
    }

    private void writeNodeId(String id, Label label, Map<String, Object> properties) throws IOException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("nodeId", id);
        record.put("label", label.name());
        record.putAll(properties);

        CSVWriter out = getNodeWriter(label);
        out.write(record);
    }

    private static String uniqueId(Label label, String uniquePropertyName, String uniquePropertyValue) {
        String unique = label.name() + "_" + uniquePropertyName + "_" + uniquePropertyValue;
        String id = DigestUtils.md5Hex(unique);
        return id;
    }

    public void normalize() throws IOException {
        normalize(nodeWriters.values());
        normalize(relationshipWriters.values());
    }

    private void normalize(Collection<CSVWriter> writers) throws IOException {
        for (CSVWriter writer : writers) {
            writer.normalize(replaces);
        }
    }

    @Override
    public void close() throws IOException {
        close(false);
    }

    public void close(boolean justFlush) throws IOException {
        flushReplaceWriter();
        close(nodeWriters.values());
        close(relationshipWriters.values());
        if (justFlush)
            return;
        normalize();
        writeArgsFile();
    }

    private Map<String, String> loadReplaces() throws IOException {
        Map<String, String> replaces = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(replaceFile), Charset.forName("UTF-8")))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                String id = values[0];
                String newId = values[1];
                if (!id.equals(newId)) {
                    replaces.put(id, newId);
                }
            }
        }

        return replaces;
    }

    private void close(Collection<? extends Closeable> closeables) throws IOException {
        for (Closeable out : closeables) {
            out.close();
        }
    }

    @Override
    public void flush() throws IOException {
        flush(nodeWriters.values());
        flush(relationshipWriters.values());
        flushReplaceWriter();
    }

    private synchronized void flush(Collection<? extends Flushable> flushables) throws IOException {
        for (Flushable out : flushables) {
            out.flush();
        }
    }

    private void flushReplaceWriter() throws IOException {
        synchronized (replaces) {
            try (Writer replaceWriter = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(replaceFile), Charset.forName("UTF-8")))) {
                for (Entry<String, String> entry : replaces.entrySet()) {
                    replaceWriter.write(entry.getKey());
                    replaceWriter.write(",");
                    replaceWriter.write(entry.getValue());
                    replaceWriter.write("\r\n");
                }
            }
            Util.fsync(replaceFile.toPath());
        }
    }

    private static class CSVWriter implements Closeable, Flushable {

        static final String SEPARATOR = "_";

        private static final Pattern SLASH_PATTERN = Pattern.compile("\\\\");
        private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
        private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\r|\n");

        /**
         * 5MB buffer size
         */
        private static final int MIN_SIZE_TO_FLUSH = 5 * 1024 * 1024;

        private Writer out;
        private StringBuilder sb = new StringBuilder();
        private LinkedHashSet<String> fieldPositions = new LinkedHashSet<>();
        private HashMap<String, String> fieldTypes = new HashMap<>();

        private Set<String> prevNodeRecords = Collections
                .newSetFromMap(new LinkedHashMap<String, Boolean>(16, 0.75f, true) {
                    /**
					 * 
					 */
					private static final long serialVersionUID = 1L;

					@Override
                    protected boolean removeEldestEntry(Entry<String, Boolean> entry) {
                        return this.size() > 10000;
                    }
                });

        private String prefix;
        private String name;
        private String suffix;
        private File output;
        private File commitLog;
        private File fieldData;
        private boolean isNodeWriter;

        public CSVWriter(File root, String prefix, Label[] labels, String suffix) throws IOException {
            this(root, prefix, join(labels), suffix);
        }

        public CSVWriter(File root, String prefix, String name, String suffix) throws IOException {
            super();
            name = name.replace(SEPARATOR, "-");
            String fileName = prefix + SEPARATOR + name + SEPARATOR + suffix + ".csv";
            this.output = new File(root, fileName);
            this.fieldData = new File(root, fileName + SEPARATOR + "fieldData");
            this.commitLog = new File(root, fileName + ".commit");
            if (commitLog.exists()) {
                byte[] bytes = Files.readAllBytes(commitLog.toPath());
                long size = Long.parseLong(new String(bytes, StandardCharsets.ISO_8859_1));
                try (FileOutputStream fos = new FileOutputStream(output, true); FileChannel fc = fos.getChannel()) {
                    fc.truncate(size);
                }
                Files.delete(commitLog.toPath());
            }
            this.out = new OutputStreamWriter(new FileOutputStream(output, output.exists()), StandardCharsets.UTF_8);
            this.prefix = prefix;
            this.name = name;
            this.suffix = suffix;
            this.isNodeWriter = prefix.equals(NODE_CSV_PREFIX);
            loadFieldData();
        }

        @SuppressWarnings("unchecked")
        public void write(Map<String, Object> record) throws IOException {
            String data = null;
            synchronized (this) {
                if (sb.length() >= MIN_SIZE_TO_FLUSH) {
                    data = sb.toString();
                    sb = new StringBuilder();
                }
            }
            if (data != null) {
                flush(data, false);
            }
            String[] fields;
            synchronized (fieldPositions) {
                fieldPositions.addAll(record.keySet());
                fields = fieldPositions.toArray(new String[fieldPositions.size()]);
            }

            StringBuilder line = new StringBuilder();
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                Object value = record.get(field);
                line.append("\"");
                if (value != null) {
                    String strVal = null;
                    if (value instanceof Collection) {
                        Collection<Object> col = (Collection<Object>) value;
                        strVal = col.stream().map(o -> o.toString()).collect(Collectors.joining(";"));
                    } else {
                        strVal = value.toString();
                    }
                    strVal = QUOTE_PATTERN.matcher(strVal).replaceAll("'");
                    strVal = SLASH_PATTERN.matcher(strVal).replaceAll("\\\\\\\\\\\\\\\\");
                    strVal = LINE_BREAK_PATTERN.matcher(strVal).replaceAll(" ");
                    line.append(strVal);
                }
                line.append("\"");
                if (i < fields.length - 1) {
                    line.append(",");
                }
            }
            line.append("\r\n");
            synchronized (this) {
                if (!isNodeWriter || prevNodeRecords.add(line.toString())) {
                    sb.append(line);
                }
            }
        }

        public void normalize(Map<String, String> replaces) throws IOException {
            if (isNodeWriter) {
                normalizeNodes(replaces);
            } else {
                replaceRels(replaces);
            }
        }

        public void replaceRels(Map<String, String> replaces) throws IOException {
            BufferedReader reader = null;
            BufferedWriter writer = null;
            File tmp = new File(output.getParentFile(), output.getName() + ".tmp");
            try {
                writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("utf-8")));
                reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(output), Charset.forName("utf-8")));
                String line;
                while ((line = reader.readLine()) != null) {
                    int firstIdx = line.indexOf("\",\"");
                    String id1 = line.substring(1, firstIdx).trim();
                    String id2 = line.substring(firstIdx + 3, line.indexOf("\",\"", firstIdx + 3)).trim();
                    String newId = getLastReplace(replaces, id1);
                    if (newId != null) {
                        line = line.replaceFirst(id1, newId);
                    }
                    String newId2 = getLastReplace(replaces, id2);
                    if (newId2 != null) {
                        line = line.replaceFirst(id2, newId2);
                    }
                    writer.write(line);
                    writer.write("\r\n");
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
                if (writer != null) {
                    writer.close();
                }
            }
            output.delete();
            tmp.renameTo(output);
        }

        public void normalizeNodes(Map<String, String> replaces) throws IOException {
            Map<String, String> uniques = new TreeMap<>();
            Set<String> finalIds = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(output), Charset.forName("utf-8")))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String id = line.substring(1, line.indexOf("\",\"")).trim();
                    String newId = getLastReplace(replaces, id);
                    if (id.equals(newId))
                        finalIds.add(id);
                    String prevLine = uniques.get(newId);
                    if (prevLine == null) {
                        uniques.put(newId, line);
                    } else {
                        TreeMap<Integer, Set<String>> map = new TreeMap<>();
                        String[][] valss = { split(line, "\",\""), split(prevLine, "\",\"") };
                        for (String[] vals : valss) {
                            for (int i = 0; i < vals.length; i++) {
                                Set<String> vs = map.get(i);
                                if (vs == null) {
                                    vs = new TreeSet<>(StringUtil.getIgnoreCaseComparator());
                                    map.put(i, vs);
                                }
                                if (i == 0)
                                    vs.add(newId);
                                else
                                    vs.addAll(Arrays.asList(vals[i].replaceAll("\"", "").split(";")));
                            }
                        }
                        StringBuilder sb = new StringBuilder();
                        int i = 0;
                        for (Set<String> set : map.values()) {
                            sb.append("\"");
                            sb.append(set.stream().filter(a -> !a.isEmpty()).collect(Collectors.joining(";")));
                            sb.append("\"");
                            if (++i < map.size())
                                sb.append(",");
                        }
                        uniques.put(newId, sb.toString());
                    }
                }
            }

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(output), Charset.forName("utf-8")))) {
                for (Entry<String, String> entry : uniques.entrySet()) {
                    if (finalIds.contains(entry.getKey())) {
                        writer.write(entry.getValue());
                        writer.write("\r\n");
                    }
                }
            }

        }

        private String[] split(String string, String pattern) {
            ArrayList<String> strs = new ArrayList<>();
            int idx = 0 - pattern.length(), i = 0;
            while ((i = string.indexOf(pattern, idx + pattern.length())) != -1) {
                strs.add(string.substring(idx + pattern.length(), i));
                idx = i;
            }
            strs.add(string.substring(idx + pattern.length()));
            return strs.toArray(new String[strs.size()]);
        }

        @Override
        public void flush() throws IOException {
            String data = null;
            synchronized (this) {
                data = sb.toString();
                sb = new StringBuilder();
            }
            flushFieldData();
            flush(data, true);
        }

        private void flush(String data, boolean commit) throws IOException {
            synchronized (out) {
                if (!commitLog.exists()) {
                    Long size = output.length();
                    Files.write(commitLog.toPath(), size.toString().getBytes(StandardCharsets.ISO_8859_1),
                            StandardOpenOption.CREATE);
                    Util.fsync(commitLog.toPath());
                }
                out.write(data);
                if (commit) {
                    out.flush();
                    Util.fsync(output.toPath());
                    Files.delete(commitLog.toPath());
                }
            }
        }

        @Override
        public void close() throws IOException {
            flush();
            out.close();
        }

        private static class FieldData implements Serializable {
            private static final long serialVersionUID = 1L;
            private HashMap<String, String> fieldTypes;
            private LinkedHashSet<String> fieldPositions;
        }

        private void flushFieldData() throws IOException {
            FieldData data = new FieldData();
            data.fieldPositions = this.fieldPositions;
            data.fieldTypes = this.fieldTypes;
            synchronized (this.fieldPositions) {
                try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(fieldData.toPath()))) {
                    oos.writeObject(data);
                }
            }
            Util.fsync(fieldData.toPath());
        }

        private void loadFieldData() throws IOException {
            if (fieldData.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(fieldData.toPath()))) {
                    FieldData data = (FieldData) ois.readObject();
                    this.fieldPositions = data.fieldPositions;
                    this.fieldTypes = data.fieldTypes;
                } catch (ClassNotFoundException e) {
                    throw new IOException(e);
                }
            }
        }

        public Set<String> getFieldPositions() {
            return fieldPositions;
        }

        public Map<String, String> getFieldTypes() {
            return fieldTypes;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getName() {
            return name;
        }

        public String getSuffix() {
            return suffix;
        }

        public File getOutput() {
            return output;
        }

        public static String join(Label... labels) {
            return Arrays.stream(labels).map(l -> l.name().replace(SEPARATOR, "-")).sorted()
                    .collect(Collectors.joining("-"));
        }

    }

}
