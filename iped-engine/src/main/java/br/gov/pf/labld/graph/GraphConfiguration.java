package br.gov.pf.labld.graph;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import dpf.sp.gpinf.indexer.config.LocaleConfig;

public class GraphConfiguration {
    
  public static final String PERSON_LABEL = "PERSON";
  public static final String ORGANIZATION_LABEL = "ORGANIZATION";
  public static final String PHONE_LABEL = "PHONE";
  public static final String EMAIL_LABEL = "EMAIL";
  public static final String CAR_LABEL = "CAR";
  public static final String DOCUMENT_LABEL = "DOCUMENT";
  public static final String BANK_ACCOUNT_LABEL = "BANK_ACCOUNT";
  public static final String MONEY_TRANSFER_LABEL = "MONEY_TRANSFER";
  public static final String DATASOURCE_LABEL = "DATASOURCE";
  public static final String CONTACT_GROUP_LABEL = "CONTACT_GROUP";

  @JsonAlias("phone-region")
  private String phoneRegion;
  
  @JsonAlias("process-proximity-relationships")
  private boolean processProximityRelationships;

  @JsonAlias("proximity-relationship-name")
  private String defaultRelationship;
  
  @JsonAlias("_comment_")
  private String comment;
  
  @JsonAlias("max-proximity-distance")
  private int maxProximityDistance;
  
  @JsonAlias("default-entity")
  private String defaultEntity;

  @JsonAlias("default-person-entity")
  private String defaultPersonEntity;

  @JsonAlias("default-business-entity")
  private String defaultBusinessEntity;

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
  private Map<String, GraphEntity> metadataIndex;

  private void index() {

    entityIndex = new HashMap<>();
    metadataIndex = new HashMap<>();

    for (GraphEntity entity : entities) {
      GraphEntity previous = entityIndex.put(entity.label, entity);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicated entity " + entity.label);
      }

      for (GraphEntityMetadata metadata : entity.metadata) {
        GraphEntity prev = metadataIndex.put(metadata.name, entity);
        if (prev != null) {
            throw new IllegalArgumentException("Duplicated metadata " + metadata.name + " at entities " + prev.label + " & " + entity.label);
        }
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
  
  public boolean getProcessProximityRelationships() {
    return processProximityRelationships;
  }

  public String getDefaultRelationship() {
    return defaultRelationship;
  }
  
  public int getMaxProximityDistance() {
    return maxProximityDistance;
  }
  
  public String getPhoneRegion() {
      if(phoneRegion.equals("auto")) {
          String country = LocaleConfig.getHostCountry();
          if(country.length() == 2)
              return country;
          else
              throw new IllegalArgumentException("phone-region=\"auto\" did not work in " + GraphTask.CONFIG_PATH + 
                      " config file. Please specify an explicity 2-letter region code.");
      }
      return phoneRegion;
  }
  
  public String getComment() {
      return comment;
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

  public GraphEntity getEntityWithMetadata(String metadataName) {
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

    /**
     * equal to regex name by default
     */
    private String name;
    
    private String property;

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

  }

}
