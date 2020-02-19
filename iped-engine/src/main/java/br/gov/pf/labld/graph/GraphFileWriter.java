package br.gov.pf.labld.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

public class GraphFileWriter implements Closeable, Flushable {

  private Map<String, CSVWriter> nodeWriters = new HashMap<>();
  private Map<String, CSVWriter> relationshipWriters = new HashMap<>();

  private BufferedWriter replaceWriter = null;
  private File replaceFile;

  private File root;
  private String suffix;

  public GraphFileWriter(File root, String suffix, String defaultEntity) {
    super();
    this.root = root;
    this.suffix = suffix;
    root.mkdirs();
    initReplaceWriter(root);
    configureDefaultEntityFields(defaultEntity);
  }

  private void initReplaceWriter(File root) {
    try {
      replaceFile = new File(root, "replace.csv");
      replaceWriter = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(replaceFile), Charset.forName("UTF-8")));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void configureDefaultEntityFields(String defaultEntity) {
    try {
      CSVWriter writer = getNodeWriter(DynLabel.label(defaultEntity));
      writer.fieldPositions = new LinkedHashSet<>(
          Arrays.asList("nodeId", "label", "path", "evidenceId", "name", "categories", "hash"));

      writer.fieldTypes = new HashMap<>();

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

  private CSVWriter openNodeWriter(Label... labels) throws IOException {
    CSVWriter writer = new CSVWriter(root, "nodes", labels, suffix);

    writer.fieldPositions = new LinkedHashSet<>(Arrays.asList("nodeId", "label"));

    writer.fieldTypes = new HashMap<>();

    writer.fieldTypes.put("nodeId", "ID");
    writer.fieldTypes.put("label", "LABEL");

    return writer;
  }

  private CSVWriter openRelationshipWriter(RelationshipType type) throws IOException {
    CSVWriter writer = new CSVWriter(root, "relationships", type.name(), suffix);
    writer.fieldPositions = new LinkedHashSet<>(Arrays.asList("start", "end", "type", "relId"));

    writer.fieldTypes = new HashMap<>();

    writer.fieldTypes.put("start", "START_ID");
    writer.fieldTypes.put("end", "END_ID");
    writer.fieldTypes.put("type", "TYPE");
    writer.fieldTypes.put("relId", "string");
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
    File file = new File(root, GraphImportRunner.ARGS_FILE_NAME + "-" + suffix + ".txt");
    try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(file), Charset.forName("utf-8")))) {

      writeArgs(nodeWriters.values(), writer, "nodes");
      writeArgs(relationshipWriters.values(), writer, "relationships");
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
    writer.write(" \"");
    writer.write(headerFile.getAbsolutePath());
    writer.write(",");
    writer.write(dataFile.getAbsolutePath());
    writer.write("\"\r\n");
  }

  private File writeHeaderFile(CSVWriter out) throws IOException {
    String fileName = out.getPrefix() + "_" + out.getName() + "_headers_" + out.getSuffix() + ".csv";
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

  public void writeMergeNode(Label label, String uniquePropertyName, Object uniquePropertyValue,
      Map<String, Object> propeties) throws IOException {
    String id = uniqueId(label, uniquePropertyName, uniquePropertyValue.toString());
    writeMergeNode(id, label, propeties);
  }

  public void writeMergeNode(Label label, String uniquePropertyName, Object uniquePropertyValue) throws IOException {
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(uniquePropertyName, uniquePropertyValue);
    writeMergeNode(label, uniquePropertyName, uniquePropertyValue, properties);
  }

  public void writeRelationship(Label label1, String idProperty1, Object propertyValue1, Label label2,
      String idProperty2, Object propertyValue2, RelationshipType relationshipType, Map<String, Object> properties)
      throws IOException {
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

  public void writeNodeReplace(DynLabel label, String propName, Object propId, String nodeId) throws IOException {
    String uniqueId1 = uniqueId(label, propName, propId.toString());
    synchronized (replaceWriter) {
      replaceWriter.write("\"");
      replaceWriter.write(uniqueId1);
      replaceWriter.write("\"");
      replaceWriter.write(",");
      replaceWriter.write("\"");
      replaceWriter.write(nodeId);
      replaceWriter.write("\"\r\n");
    }
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

  public void writeMergeNode(String id, Label label, Map<String, Object> properties) throws IOException {
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

  public void deduplicate() throws IOException {
    deduplicate(nodeWriters.values());
  }

  private void deduplicate(Collection<CSVWriter> writers) throws IOException {
    for (CSVWriter writer : writers) {
      writer.deduplicate();
    }
  }

  @Override
  public void close() throws IOException {
    if (replaceWriter != null) {
      replaceWriter.close();
    }

    close(nodeWriters.values());
    close(relationshipWriters.values());
    replace();
    writeArgsFile();
    deduplicate();
  }

  private void replace() throws IOException {
    Map<String, String> replaces = loadReplaces();

    if (!replaces.isEmpty()) {
      for (CSVWriter writer : relationshipWriters.values()) {
        writer.replace(replaces);
      }
    }
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
  }

  private void flush(Collection<? extends Flushable> flushables) throws IOException {
    for (Flushable out : flushables) {
      out.flush();
    }
  }

  private static class CSVWriter implements Closeable, Flushable {

    private static final Pattern SLASH_PATTERN = Pattern.compile("\\\\");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\r|\n");

    /**
     * 5MB buffer size
     */
    private static final int BUFFER_SIZE = 1024 * 1000 * 5;

    private Writer out;
    private LinkedHashSet<String> fieldPositions = new LinkedHashSet<>();
    private Map<String, String> fieldTypes = new HashMap<>();

    private String prefix;
    private String name;
    private String suffix;
    private File output;

    public CSVWriter(File root, String prefix, Label[] labels, String suffix) throws IOException {
      this(root, prefix, join(labels), suffix);
    }

    public CSVWriter(File root, String prefix, String name, String suffix) throws IOException {
      super();
      String fileName = prefix + "_" + name + "_" + suffix + ".csv";
      output = new File(root, fileName);
      this.out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), Charset.forName("utf-8")),
          BUFFER_SIZE);
      this.prefix = prefix;
      this.name = name;
      this.suffix = suffix;
    }

    @SuppressWarnings("unchecked")
    public synchronized void write(Map<String, Object> record) throws IOException {
      fieldPositions.addAll(record.keySet());
      Iterator<String> iterator = fieldPositions.iterator();
      while (iterator.hasNext()) {
        String field = iterator.next();
        Object value = record.get(field);
        if (value != null) {
          out.write("\"");
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
          out.write(strVal);
          out.write("\"");
        }
        if (iterator.hasNext()) {
          out.write(",");
        }
      }
      out.write("\r\n");
    }

    public void replace(Map<String, String> replaces) throws IOException {
      BufferedReader reader = null;
      BufferedWriter writer = null;
      File tmp = new File(output.getParentFile(), output.getName() + ".tmp");
      try {
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("utf-8")));
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(output), Charset.forName("utf-8")));
        String line;
        while ((line = reader.readLine()) != null) {
          String id = line.substring(0, line.indexOf(",")).trim();
          String newId = replaces.get(id);
          if (newId != null) {
            line = line.replaceFirst(id, newId);
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

    public void deduplicate() throws IOException {
      Map<String, String> uniques = new HashMap<>();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(new FileInputStream(output), Charset.forName("utf-8")))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String id = line.substring(0, line.indexOf(",")).trim();
          if (!uniques.containsKey(id)) {
            uniques.put(id, line);
          }
        }
      }

      try (BufferedWriter writer = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(output), Charset.forName("utf-8")))) {
        for (String line : uniques.values()) {
          writer.write(line);
          writer.write("\r\n");
        }
      }

    }

    @Override
    public void flush() throws IOException {
      out.flush();
    }

    @Override
    public void close() throws IOException {
      out.close();
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
      return Arrays.stream(labels).map(l -> l.name()).sorted().collect(Collectors.joining("_"));
    }

  }

}
