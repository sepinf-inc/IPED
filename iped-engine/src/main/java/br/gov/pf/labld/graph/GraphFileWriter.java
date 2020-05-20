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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.lucene.util.IOUtils;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

public class GraphFileWriter implements Closeable, Flushable {
    
    private static final String NODE_CSV_PREFIX = "nodes";
    private static final String REL_CSV_PREFIX = "relationships";
    private static final String HEADER_CSV_STR = "_headers_";

  private Map<String, CSVWriter> nodeWriters = new HashMap<>();
  private Map<String, CSVWriter> relationshipWriters = new HashMap<>();

  private Map<String, String> replaces = new HashMap<>();
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
    openExistingCSVs();
  }

  private void initReplaceWriter(File root) {
    try {
      replaceFile = new File(root, "replace.csv");
      if(replaceFile.exists()) {
          replaces = loadReplaces();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void configureDefaultEntityFields(String defaultEntity) {
    try {
      CSVWriter writer = getNodeWriter(DynLabel.label(defaultEntity));
      writer.fieldPositions.addAll(
          Arrays.asList("nodeId", "label", "path", "evidenceId", "name", "categories", "hash"));

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
  
  private void openExistingCSVs() {
      try {
          File[] files = root.listFiles();
          if(files != null) {
              for(File file : files) {
                  String[] frags = file.getName().split(CSVWriter.SEPARATOR);
                  if(frags.length != 3 || file.getName().contains(HEADER_CSV_STR)) continue;
                  if(NODE_CSV_PREFIX.equals(frags[0])) {
                      getNodeWriter(DynLabel.label(frags[1]));
                  }
                  if(REL_CSV_PREFIX.equals(frags[0])) {
                      getRelationshipWriter(DynRelationshipType.withName(frags[1]));
                  }
              }
          }
      } catch (IOException e) {
          throw new RuntimeException(e);
      }
  }

  private CSVWriter openNodeWriter(Label... labels) throws IOException {
    CSVWriter writer = new CSVWriter(root, NODE_CSV_PREFIX, labels, suffix);

    writer.fieldPositions.addAll(Arrays.asList("nodeId", "label"));
    writer.fieldTypes.put("nodeId", "ID");
    writer.fieldTypes.put("label", "LABEL");

    return writer;
  }

  private CSVWriter openRelationshipWriter(RelationshipType type) throws IOException {
    CSVWriter writer = new CSVWriter(root, REL_CSV_PREFIX, type.name(), suffix);

    writer.fieldPositions.addAll(Arrays.asList("start", "end", "type", "relId"));
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
    writer.write(" \"");
    writer.write(headerFile.getAbsolutePath());
    writer.write(",");
    writer.write(dataFile.getAbsolutePath());
    writer.write("\"\r\n");
  }

  private File writeHeaderFile(CSVWriter out) throws IOException {
    String fileName = out.getPrefix() + CSVWriter.SEPARATOR + out.getName() + HEADER_CSV_STR + out.getSuffix() + ".csv";
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
    if(val instanceof Collection) {
        ((Collection<Object>) val).add(uniquePropertyValue);
    }else {
        if(val != null && !val.equals(uniquePropertyValue))
            properties.put(uniquePropertyName, Arrays.asList(uniquePropertyValue, val));
        else if(val == null)
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
  
  private static String getLastReplace(Map<String, String> replaces, String id) {
      String tmp, key = id;
      while((tmp = replaces.get(key)) != null) {
          key = tmp;
      }
      return key;
  }

  public void writeNodeReplace(Label label, String propName, Object propId, String nodeId) throws IOException {
    String uniqueId1 = uniqueId(label, propName, propId.toString());
    synchronized (replaces) {
        if(!uniqueId1.equals(nodeId)) {
            String key = getLastReplace(replaces, uniqueId1);
            String replace = getLastReplace(replaces, nodeId);
            if(!key.equals(replace)) {
                replaces.put(key, replace);
            }
        }
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
    flushReplaceWriter();
    close(nodeWriters.values());
    close(relationshipWriters.values());
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
      synchronized(replaces) {
          try(Writer replaceWriter = new BufferedWriter(
                  new OutputStreamWriter(new FileOutputStream(replaceFile), Charset.forName("UTF-8")))){
              for(Entry<String, String> entry : replaces.entrySet()) {
                  replaceWriter.write(entry.getKey());
                  replaceWriter.write(",");
                  replaceWriter.write(entry.getValue());
                  replaceWriter.write("\r\n");
              }
          }
          IOUtils.fsync(replaceFile, false);
      }
  }
  
  private static class IgnoreCaseComparator implements Comparator<String>{
      
      static IgnoreCaseComparator INSTANCE = new IgnoreCaseComparator();
      
      @Override
      public int compare(String o1, String o2) {
          return o1.compareToIgnoreCase(o2);
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
    private static final int MIN_SIZE_TO_FLUSH = 1024 * 1024;

    private Writer out;
    private StringBuilder sb = new StringBuilder();
    private LinkedHashSet<String> fieldPositions = new LinkedHashSet<>();
    private HashMap<String, String> fieldTypes = new HashMap<>();

    private String prefix;
    private String name;
    private String suffix;
    private File output;
    private File temp;
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
      this.temp = new File(root, fileName + ".tmp");
      Files.deleteIfExists(temp.toPath());
      this.out = new OutputStreamWriter(new FileOutputStream(output, output.exists()), StandardCharsets.UTF_8);
      this.prefix = prefix;
      this.name = name;
      this.suffix = suffix;
      this.isNodeWriter = prefix.equals(NODE_CSV_PREFIX);
      loadFieldData();
    }

    @SuppressWarnings("unchecked")
    public synchronized void write(Map<String, Object> record) throws IOException {
      if(sb.length() >= MIN_SIZE_TO_FLUSH) {
          writeToTemp();
      }
      fieldPositions.addAll(record.keySet());
      Iterator<String> iterator = fieldPositions.iterator();
      while (iterator.hasNext()) {
        String field = iterator.next();
        Object value = record.get(field);
        sb.append("\"");
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
          sb.append(strVal);
        }
        sb.append("\"");
        if (iterator.hasNext()) {
            sb.append(",");
        }
      }
      sb.append("\r\n");
    }
    
    public void normalize(Map<String, String> replaces) throws IOException {
        if(isNodeWriter) {
            normalizeNodes(replaces);
        }else {
            replaceRels(replaces);
            deduplicateLines();
        }
    }

    public void replaceRels(Map<String, String> replaces) throws IOException {
      BufferedReader reader = null;
      BufferedWriter writer = null;
      File tmp = new File(output.getParentFile(), output.getName() + ".tmp");
      try {
        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("utf-8")));
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(output), Charset.forName("utf-8")));
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
    
    private void deduplicateLines() throws IOException {
        List<String> lines = Files.readAllLines(output.toPath());
        Collections.sort(lines);
        try(Writer writer = Files.newBufferedWriter(output.toPath(), StandardOpenOption.TRUNCATE_EXISTING)){
            String prevLine = "";
            for(String line : lines) {
                if(!line.equals(prevLine)) {
                    writer.write(line);
                    writer.write("\r\n");
                }
                prevLine = line;
            }
        }
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
          if(id.equals(newId)) finalIds.add(id);
          String prevLine = uniques.get(newId);
          if (prevLine == null) {
              uniques.put(newId, line);
          }else {
              TreeMap<Integer, Set<String>> map = new TreeMap<>();
              String[][] valss = {split(line, "\",\""), split(prevLine, "\",\"")};
              for(String[] vals : valss) {
                  for(int i = 0; i < vals.length; i++) {
                      Set<String> vs = map.get(i);
                      if(vs == null) {
                          vs = new TreeSet<>(IgnoreCaseComparator.INSTANCE);
                          map.put(i, vs);
                      }
                      if(i == 0) vs.add(newId);
                      else vs.addAll(Arrays.asList(vals[i].replaceAll("\"", "").split(";")));
                  }
              }
              StringBuilder sb = new StringBuilder();
              int i = 0;
              for(Set<String> set : map.values()) {
                  sb.append("\"");
                  sb.append(set.stream().filter(a -> !a.isEmpty()).collect(Collectors.joining(";")));
                  sb.append("\"");
                  if(++i < map.size())
                      sb.append(",");
              }
              uniques.put(newId, sb.toString());
          }
        }
      }

      try (BufferedWriter writer = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(output), Charset.forName("utf-8")))) {
        for (Entry<String, String> entry : uniques.entrySet()) {
          if(finalIds.contains(entry.getKey())) {
              writer.write(entry.getValue());
              writer.write("\r\n");    
          }
        }
      }
      
    }
    
    private String[] split(String string, String pattern) {
        ArrayList<String> strs = new ArrayList<>();
        int idx = 0 - pattern.length(), i = 0;
        while((i = string.indexOf(pattern, idx + pattern.length())) != -1) {
            strs.add(string.substring(idx + pattern.length(), i));
            idx = i;
        }
        strs.add(string.substring(idx + pattern.length()));
        return strs.toArray(new String[strs.size()]);
    }
    
    private synchronized void writeToTemp() throws IOException {
        try(Writer writer = Files.newBufferedWriter(temp.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)){
            writer.write(sb.toString());
            sb = new StringBuilder();
        }
    }

    @Override
    public synchronized void flush() throws IOException {
      if(temp.exists()) {
          try(Reader reader = Files.newBufferedReader(temp.toPath())){
              char[] cbuf = new char[1 << 20];
              int read = 0;
              while((read = reader.read(cbuf)) != -1) {
                  out.write(cbuf, 0, read);
              }
          }
          temp.delete();
      }
      out.write(sb.toString());
      sb = new StringBuilder();
      out.flush();
      IOUtils.fsync(output, false);
      flushFieldData();
    }

    @Override
    public void close() throws IOException {
      flush();
      out.close();
    }
    
    private static class FieldData implements Serializable{
        private static final long serialVersionUID = 1L;
        private HashMap<String, String> fieldTypes;
        private LinkedHashSet<String> fieldPositions;
    }
    
    private synchronized void flushFieldData() throws IOException {
        FieldData data = new FieldData();
        data.fieldPositions = this.fieldPositions;
        data.fieldTypes = this.fieldTypes;
        try(ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(fieldData.toPath()))){
            oos.writeObject(data);
        }
        IOUtils.fsync(fieldData, false);
    }
    
    @SuppressWarnings("unchecked")
    private void loadFieldData() throws IOException {
        if(fieldData.exists()) {
            try(ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(fieldData.toPath()))){
                FieldData data = (FieldData)ois.readObject();
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
      return Arrays.stream(labels).map(l -> l.name().replace(SEPARATOR, "-")).sorted().collect(Collectors.joining("-"));
    }

  }

}
