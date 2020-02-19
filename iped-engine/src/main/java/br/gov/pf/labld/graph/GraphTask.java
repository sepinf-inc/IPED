package br.gov.pf.labld.graph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

import br.gov.pf.labld.cases.IpedCase;
import br.gov.pf.labld.cases.IpedCase.IpedDatasourceType;
import br.gov.pf.labld.graph.GraphConfiguration.GraphEntity;
import br.gov.pf.labld.graph.GraphConfiguration.GraphEntityMetadata;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import iped3.IItem;
import iped3.util.BasicProps;

public class GraphTask extends AbstractTask {

  public static final String DB_NAME = "graph.db";
  public static final String DB_PATH = "neo4j/databases";
  public static final String GENERATED_PATH = "neo4j/generated";

  public static final String ENABLE_PARAM = "enableGraphGeneration";

  public static final String CONFIG_PATH = "GraphConfig.json";

  private GraphConfiguration configuration;

  private boolean enabled = false;

  private static GraphFileWriter graphFileWriter;

  @Override
  public void init(Properties confParams, File confDir) throws Exception {
    enabled = isGraphGenerationEnabled(confParams);
    if (enabled) {
      configuration = loadConfiguration(confDir);

      if (graphFileWriter == null) {
          LocalConfig ipedConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                  .iterator().next();
          graphFileWriter = new GraphFileWriter(new File(ipedConfig.getIndexerTemp(), GENERATED_PATH), "iped",
            configuration.getDefaultEntity());
      }
    }
  }

  public static boolean isGraphGenerationEnabled(Properties confParams) {
    boolean enabled = false;
    String value = confParams.getProperty(ENABLE_PARAM);
    if (value != null && !value.trim().isEmpty()) {
      enabled = Boolean.valueOf(value.trim());
    }
    return enabled;
  }

  public static GraphConfiguration loadConfiguration(File confDir) throws IOException {
    File file = new File(confDir, CONFIG_PATH);
    return GraphConfiguration.loadFrom(file);
  }

  @Override
  public void finish() throws Exception {
    if (graphFileWriter != null) {
      synchronized (graphFileWriter) {
        if (graphFileWriter != null) {
          graphFileWriter.close();
        }
      }
      graphFileWriter = null;
    }
  }

  @Override
  public void process(IItem evidence) throws Exception {
    if (!isEnabled()) {
      return;
    }

    processEvidence(evidence);
  }

  private void processEvidence(IItem evidence) throws IOException {
    if (!isEnabled()) {
      return;
    }

    IpedCase ipedCase = (IpedCase) caseData.getCaseObject(IpedCase.class.getName());
    List<Integer> parentIds = evidence.getParentIds();

    boolean isIpedCase = ipedCase != null;
    boolean isDir = evidence.isDir();
    boolean isCaseRoot = isIpedCase && parentIds.size() < 3;
    boolean isGraphDatasource = false;

    if (isDir && !isCaseRoot) {
      return;
    }

    Map<String, Object> nodeProperties = new HashMap<>();

    Object entityType = evidence.getExtraAttribute("X-EntityType");

    List<Label> labels = new ArrayList<>(1);
    Label label;
    String propertyName = "evidenceId";
    Object identifier = evidence.getId();
    if (entityType != null) {
      IpedDatasourceType type = IpedDatasourceType.valueOf(entityType.toString());

      GraphEntity entity;
      if (type == IpedDatasourceType.PERSON) {
        entity = configuration.getEntity(configuration.getDefaultPersonEntity());
      } else if (type == IpedDatasourceType.BUSINESS) {
        entity = configuration.getEntity(configuration.getDefaultBusinessEntity());
      } else if (type == IpedDatasourceType.GENERIC) {
        entity = configuration.getEntity(configuration.getDefaultEntity());
      } else {
        throw new IllegalArgumentException("Unknown X-EntityType:" + entityType);
      }

      labels.add(DynLabel.label("DATASOURCE"));
      isGraphDatasource = true;

      nodeProperties.put("type", type.name());

      Object entityProperty = evidence.getExtraAttribute("X-EntityProperty");
      Object entityPropertyValue = evidence.getExtraAttribute("X-EntityPropertyValue");

      label = DynLabel.label(entity.getLabel());
      if (entityProperty != null) {
        propertyName = entityProperty.toString();
      } else {
        propertyName = "evidenceId";
      }

      if (entityPropertyValue != null) {
        identifier = entityPropertyValue.toString();
      } else {
        identifier = evidence.getId();
      }

      nodeProperties.put(propertyName, entityPropertyValue);
    } else {
      label = DynLabel.label(configuration.getDefaultEntity());
      propertyName = "evidenceId";
      identifier = evidence.getId();
    }

    nodeProperties.put("evidenceId", evidence.getId());
    nodeProperties.put("name", evidence.getName());
    nodeProperties.put("path", evidence.getPath());

    Object category = evidence.getTempAttribute(BasicProps.CATEGORY);
    if (category == null) {
      if (!evidence.getCategorySet().isEmpty()) {
        category = evidence.getCategorySet().iterator().next();
      }
    }

    String categoryValue;
    if (category != null) {
      categoryValue = category.toString();
    } else {
      categoryValue = null;
    }
    nodeProperties.put("category", categoryValue);

    String hash = evidence.getHash();
    if (hash != null && !hash.isEmpty()) {
      nodeProperties.put("hash", hash);
    }

    Object reader = evidence.getExtraAttribute("X-Reader");
    if (reader != null && !reader.toString().isEmpty()) {
      nodeProperties.put("source", reader.toString());
    }

    nodeProperties.put(propertyName, identifier);
    
    String nodeId = graphFileWriter.writeCreateNode(propertyName, identifier, nodeProperties, label,
        labels.toArray(new Label[labels.size()]));
    
    if (isGraphDatasource) {
      graphFileWriter.writeNodeReplace(DynLabel.label(configuration.getDefaultEntity()), "evidenceId", evidence.getId(),
          nodeId);
    }
    
    RelationshipType relationshipType = DynRelationshipType.withName(configuration.getDefaultRelationship());

    if (isIpedCase && parentIds.size() > 2) {
      // Cria vinculo da evidencia com a entrada do datasource se ipedCase.
      Integer inputId = parentIds.get(2);
      graphFileWriter.writeCreateRelationship(label, "evidenceId", inputId, label, propertyName, identifier,
          relationshipType);
    } else if (isIpedCase && parentIds.size() == 2) {
      // Cria vinculo da entrada com o datasource se ipedCase.
      Integer datasourceId = parentIds.get(1);
      graphFileWriter.writeCreateRelationship(DynLabel.label(configuration.getDefaultEntity()), "evidenceId",
          datasourceId, label, propertyName, identifier, relationshipType);
    }

    processMetadata(evidence, label, propertyName, identifier);
  }

  @SuppressWarnings("unchecked")
  private void processMetadata(IItem evidence, Label evidenceLabel, String evidenceProp, Object evidenceId)
      throws IOException {
    Map<String, Object> extraAttributeMap = evidence.getExtraAttributeMap();
    if (includeEvidence(evidence) && extraAttributeMap != null) {
      Set<Entry<String, Object>> entries = extraAttributeMap.entrySet();
      Set<String> relationsAdded = new HashSet<>();
      for (Entry<String, Object> entry : entries) {
        String key = entry.getKey();
        List<GraphEntity> entities = configuration.getEntities(key);
        if (entities != null) {
          for (GraphEntity entity : entities) {
              GraphEntityMetadata metadata = entity.getMetadata(key);
              for (Entry<String, Object> entry2 : entries) {
                  String key2 = entry2.getKey();
                  List<GraphEntity> entities2 = configuration.getEntities(key2);
                  if (entities2 != null) {
                    for (GraphEntity entity2 : entities2) {
                      GraphEntityMetadata metadata2 = entity2.getMetadata(key2);
                      processMatches(entity, metadata, (List<Object>) entry.getValue(), entity2, metadata2, (List<Object>) entry2.getValue(), evidence, relationsAdded);
                    }
                  }
              }
          }
        }
      }
    }
  }

  private boolean includeEvidence(IItem evidence) {
    boolean include = true;

    Pattern includeCategoriesPattern = configuration.getIncludeCategoriesPattern();
    Pattern excludeCategoriesPattern = configuration.getExcludeCategoriesPattern();

    for (String category : evidence.getCategorySet()) {
      include = include && includeCategoriesPattern.matcher(category).matches();
      include = include && !excludeCategoriesPattern.matcher(category).matches();
    }

    return include;
  }

  private void processMatches(GraphEntity entity, GraphEntityMetadata metadata, List<Object> matches, 
          GraphEntity entity2, GraphEntityMetadata metadata2, List<Object> matches2, IItem evidence, Set<String> relationsAdded) throws IOException {
    String labelName = entity.getLabel();
    String propertyName = metadata.getProperty();
    Label label = DynLabel.label(labelName);
    
    String labelName2 = entity2.getLabel();
    String propertyName2 = metadata2.getProperty();
    Label label2 = DynLabel.label(labelName2);

    if (matches != null && matches2 != null) {
      //RelationshipType relationshipType = DynRelationshipType.withName(metadata.getRelationship());
      RelationshipType relationshipType = DynRelationshipType.withName(configuration.getDefaultRelationship());
      
      Map<String, Object> relProps = new HashMap<>();
      relProps.put("relId", evidence.getId());
      
      Set<String> controlSet = new HashSet<>();
      for (Object match : matches) {
          for (Object match2 : matches2) {
              String propertyValue = match.toString();
              String propertyValue2 = match2.toString();
              if(propertyValue.equals(propertyValue2)) {
                  continue;
              }
              
              String relId = propertyValue.compareTo(propertyValue2) < 0 ? propertyValue + "-" + propertyValue2 : propertyValue2 + "-" + propertyValue;
              if(!relationsAdded.add(relId))
                  continue;
              
              if (controlSet.add(propertyValue) && controlSet.add(propertyValue2)) {
                graphFileWriter.writeMergeNode(label, propertyName, propertyValue);
                graphFileWriter.writeMergeNode(label2, propertyName2, propertyValue2);
                graphFileWriter.writeRelationship(label, propertyName, propertyValue, label2, propertyName2, propertyValue2, relationshipType, relProps);
              }
          }
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

}
