package br.gov.pf.labld.cases;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

public class IpedCase {

  public static final String IPEDCASE_JSON = "ipedcase.json";

  private String name;
  private String version;
  private String output;
  private List<IpedDatasource> datasources = new ArrayList<>();

  public IpedCase() {
    super();
  }

  public static File getCaseFile(File output) {
    return new File(output, IPEDCASE_JSON);
  }

  @JsonIgnore
  public File getCaseFile() {
    return getCaseFile(new File(output));
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getOutput() {
    return output;
  }

  public void setOutput(String output) {
    this.output = output;
  }

  public void addDatasource(IpedDatasource datasource) {
    datasource.index = datasources.size();
    datasources.add(datasource);
  }

  public List<IpedDatasource> getDatasources() {
    return datasources;
  }

  public void setDatasources(List<IpedDatasource> datasources) {
    this.datasources = datasources;
    Collections.sort(this.datasources);
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @JsonIgnore
  public boolean isProcessed() {
    File caseFile = getCaseFile();
    File caseDir = caseFile.getParentFile();
    File indexFolder = new File(caseDir, "indexador");
    return indexFolder.exists();
  }

  public void saveTo(File file) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter().forType(this.getClass());
    try (Writer out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(file)),
        Charset.forName("utf-8"))) {
      objectWriter.writeValue(out, this);
    }
  }

  public static IpedCase loadFrom(File file) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectReader reader = objectMapper.readerFor(IpedCase.class);
    try (Reader in = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)),
        Charset.forName("utf-8"))) {
      return reader.readValue(in);
    }
  }

  public static class IpedDatasource implements Comparable<IpedDatasource> {

    @JsonInclude
    private int index = 0;
    private String name;
    private IpedDatasourceType type;
    private List<IpedInput> inputs = new ArrayList<>();

    private Map<String, String> extras = new HashMap<>();

    public IpedDatasource() {
      super();
    }

    @JsonInclude
    public int getIndex() {
      return index;
    }

    protected void setIndex(int index) {
      this.index = index;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public void addInput(String input) {
      this.inputs.add(new IpedInput(inputs.size(), input));
    }

    public void setInputs(List<IpedInput> inputs) {
      this.inputs = inputs;
      Collections.sort(inputs);
    }

    public List<IpedInput> getInputs() {
      return inputs;
    }

    public void addExtra(String key, String value) {
      extras.put(key, value);
    }

    public Map<String, String> getExtras() {
      return extras;
    }

    protected void setExtras(Map<String, String> extras) {
      this.extras = extras;
    }

    public IpedDatasourceType getType() {
      return type;
    }

    public void setType(IpedDatasourceType type) {
      this.type = type;
    }

    @Override
    public int compareTo(IpedDatasource o) {
      return index - o.index;
    }

  }

  public static enum IpedDatasourceType {

    GENERIC, PERSON, BUSINESS

  }

  public static class IpedInput implements Comparable<IpedInput> {

    @JsonInclude
    private int index = 0;
    private String path;

    protected IpedInput() {
      super();
    }

    private IpedInput(int index, String path) {
      super();
      this.index = index;
      this.path = path;
    }

    @JsonInclude
    public int getIndex() {
      return index;
    }

    protected void setIndex(int index) {
      this.index = index;
    }

    public String getPath() {
      return path;
    }

    public void setPath(String path) {
      this.path = path;
    }

    @Override
    public int compareTo(IpedInput o) {
      return index - o.index;
    }

  }

}
