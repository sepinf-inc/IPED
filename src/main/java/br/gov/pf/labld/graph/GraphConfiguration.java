package br.gov.pf.labld.graph;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.sun.star.lang.IllegalArgumentException;

public class GraphConfiguration {

  @JsonAlias("default-entity")
  private String defaultEntity;

  @JsonAlias("default-person-entity")
  private String defaultPersonEntity;

  @JsonAlias("default-business-entity")
  private String defaultBusinessEntity;

  @JsonAlias("default-relationship")
  private String defaultRelationship;

  @JsonAlias("include-categories")
  private String includeCategories;

  private Pattern includeCategoriesPattern;

  @JsonAlias("exclude-categories")
  private String excludeCategories;
  
  @JsonAlias("post-generation-statements")
  private List<String> postGenerationStatements;

  private Pattern excludeCategoriesPattern;

  private List<GraphEntity> entities;

  @JsonIgnore
  private Map<String, GraphEntity> entityIndex;

  @JsonIgnore
  private Map<String, List<GraphEntity>> metadataIndex;

  private void index() {

    entityIndex = new HashMap<>();
    metadataIndex = new HashMap<>();

    for (GraphEntity entity : entities) {
      GraphEntity previous = entityIndex.put(entity.label, entity);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicated entity " + entity.label);
      }

      for (GraphEntityMetadata metadata : entity.metadata) {
        List<GraphEntity> list = metadataIndex.get(metadata.name);
        if (list == null) {
          list = new ArrayList<>();
          metadataIndex.put(metadata.name, list);
        }
        list.add(entity);
      }
      entity.index();
    }

    includeCategoriesPattern = Pattern.compile(includeCategories);
    excludeCategoriesPattern = Pattern.compile(excludeCategories);

  }

  public String getDefaultEntity() {
    return defaultEntity;
  }

  public void setDefaultEntity(String defaultEntity) {
    this.defaultEntity = defaultEntity;
  }

  public String getDefaultPersonEntity() {
    return defaultPersonEntity;
  }

  public void setDefaultPersonEntity(String defaultPersonEntity) {
    this.defaultPersonEntity = defaultPersonEntity;
  }

  public String getDefaultBusinessEntity() {
    return defaultBusinessEntity;
  }

  public void setDefaultBusinessEntity(String defaultBusinessEntity) {
    this.defaultBusinessEntity = defaultBusinessEntity;
  }

  public String getDefaultRelationship() {
    return defaultRelationship;
  }

  public void setDefaultRelationship(String defaultRelationship) {
    this.defaultRelationship = defaultRelationship;
  }

  public String getIncludeCategories() {
    return includeCategories;
  }

  public void setIncludeCategories(String includeCategories) {
    this.includeCategories = includeCategories;
  }

  public String getExcludeCategories() {
    return excludeCategories;
  }

  public void setExcludeCategories(String excludeCategories) {
    this.excludeCategories = excludeCategories;
  }

  public Pattern getExcludeCategoriesPattern() {
    return excludeCategoriesPattern;
  }

  public Pattern getIncludeCategoriesPattern() {
    return includeCategoriesPattern;
  }

  public List<String> getPostGenerationStatements() {
    return postGenerationStatements;
  }

  public List<GraphEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<GraphEntity> entities) {
    this.entities = entities;
  }

  public GraphEntity getEntity(String label) {
    return entityIndex.get(label);
  }

  public List<GraphEntity> getEntities(String metadataName) {
    return metadataIndex.get(metadataName);
  }

  public static GraphConfiguration loadFrom(File file) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ObjectReader reader = objectMapper.readerFor(GraphConfiguration.class);
    try (Reader in = new InputStreamReader(new BufferedInputStream(new FileInputStream(file)),
        Charset.forName("utf-8"))) {
      GraphConfiguration value = reader.readValue(in);
      value.index();
      return value;
    }
  }

  public static class GraphEntity {

    private String label;

    private List<GraphEntityMetadata> metadata;

    private Map<String, GraphEntityMetadata> metadataIndex;

    private void index() {
      metadataIndex = new HashMap<>(metadata.size());

      for (GraphEntityMetadata meta : metadata) {
        GraphEntityMetadata previous = metadataIndex.put(meta.name, meta);
        if (previous != null) {
          throw new IllegalArgumentException("Duplicated metadata " + meta.name + " at entity " + label);
        }
      }

    }

    public String getLabel() {
      return label;
    }

    public void setLabel(String label) {
      this.label = label;
    }

    public GraphEntityMetadata getMetadata(String metadataName) {
      return metadataIndex.get(metadataName);
    }

    public List<GraphEntityMetadata> getMetadata() {
      return metadata;
    }

    public void setMetadata(List<GraphEntityMetadata> metadata) {
      this.metadata = metadata;
    }

  }

  public static class GraphEntityMetadata {

    private String name;
    private String property;
    private String relationship;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getProperty() {
      return property;
    }

    public void setProperty(String property) {
      this.property = property;
    }

    public String getRelationship() {
      return relationship;
    }

    public void setRelationship(String relationship) {
      this.relationship = relationship;
    }

  }

}
