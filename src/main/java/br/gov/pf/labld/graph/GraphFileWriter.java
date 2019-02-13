package br.gov.pf.labld.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

public class GraphFileWriter implements Closeable, Flushable {

  private Map<String, CSVWriter> nodeWriters = new HashMap<>();
  private Map<String, CSVWriter> relationshipWriters = new HashMap<>();

  private File root;
  private String suffix;

  public GraphFileWriter(File root, String suffix) {
    super();
    this.root = root;
    this.suffix = suffix;
    root.mkdirs();
  }

  private CSVWriter openNodeWriter(Label... labels) throws IOException {
    return new CSVWriter(root, "nodes", labels, suffix);
  }

  private CSVWriter openRelationshipWriter(RelationshipType type) throws IOException {
    return new CSVWriter(root, "relationships", type.name(), suffix);
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
    LinkedHashSet<String> fields = out.getFieldPositions();
    try (BufferedWriter writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(file), Charset.forName("utf-8")))) {
      String collected = fields.stream().collect(Collectors.joining(","));
      writer.write(collected);
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
    record.put("nodeId:ID", id);
    record.put(":LABEL", labelsNames);
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
    record.put(":START_ID", uniqueId1);
    record.put(":END_ID", uniqueId2);
    record.put(":TYPE", relationshipType.name());
    record.putAll(properties);

    CSVWriter out = getRelationshipWriter(relationshipType);
    out.write(record);

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
    record.put("nodeId:ID", id);
    record.put(":LABEL", label.name());
    record.putAll(properties);

    CSVWriter out = getNodeWriter(label);
    out.write(record);
  }

  private String uniqueId(Label label, String uniquePropertyName, String uniquePropertyValue) {
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
    close(nodeWriters.values());
    close(relationshipWriters.values());
    writeArgsFile();
    deduplicate();
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

    private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\r|\n");

    /**
     * 5MB buffer size
     */
    private static final int BUFFER_SIZE = 1024 * 1000 * 5;

    private Writer out;
    private LinkedHashSet<String> fieldPositions = new LinkedHashSet<>();

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

    public synchronized void write(Map<String, Object> record) throws IOException {
      fieldPositions.addAll(record.keySet());
      Iterator<String> iterator = fieldPositions.iterator();
      while (iterator.hasNext()) {
        String field = iterator.next();
        Object value = record.get(field);
        if (value != null) {
          out.write("\"");
          String strVal = value.toString();
          strVal = QUOTE_PATTERN.matcher(strVal).replaceAll("'");
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

    public LinkedHashSet<String> getFieldPositions() {
      return fieldPositions;
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
