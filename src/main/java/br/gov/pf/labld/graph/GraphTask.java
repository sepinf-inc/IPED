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

import org.apache.tika.mime.MediaType;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

import br.gov.pf.labld.cases.IpedCase;
import br.gov.pf.labld.cases.IpedCase.IpedDatasourceType;
import br.gov.pf.labld.graph.GraphConfiguration.GraphEntity;
import br.gov.pf.labld.graph.GraphConfiguration.GraphEntityMetadata;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import gpinf.dev.data.EvidenceFile;

public class GraphTask extends AbstractTask {

  public static final String DB_NAME = "graph.db";
  public static final String DB_PATH = "neo4j/databases";
  public static final String GENERATED_PATH = "neo4j/generated";

  public static final String ENABLE_PARAM = "enableGraphGeneration";

  public static final String CONFIG_PATH = "GraphConfig.json";

  private GraphConfiguration configuration;

  private boolean enabled = false;

//  private GraphService graphService;

  private static GraphFileWriter graphFileWriter;

  @Override
  public void init(Properties confParams, File confDir) throws Exception {
    enabled = isGraphGenerationEnabled(confParams);
    if (enabled) {
      configuration = loadConfiguration(confDir);
      if (graphFileWriter == null) {
        graphFileWriter = new GraphFileWriter(new File(Configuration.indexerTemp, GENERATED_PATH), "iped");
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
      graphFileWriter.close();
      graphFileWriter.writeArgsFile();
      graphFileWriter.deduplicate();
      graphFileWriter = null;
    }
  }

  @Override
  public void process(EvidenceFile evidence) throws Exception {
    if (!isEnabled()) {
      return;
    }

    processEvidence(evidence);
  }

  private void processEvidence(EvidenceFile evidence) throws IOException {
    if (!isEnabled()) {
      return;
    }

    IpedCase ipedCase = (IpedCase) caseData.getCaseObject(IpedCase.class.getName());
    List<Integer> parentIds = evidence.getParentIds();

    boolean isIpedCase = ipedCase != null;
    boolean isDir = evidence.isDir();
    boolean isCaseRoot = isIpedCase && parentIds.size() < 3;

    if (isDir && !isCaseRoot) {
      return;
    }

    Map<String, Object> nodeProperties = new HashMap<>();

    Object entityType = evidence.getExtraAttribute("X-EntityType");

    List<Label> labels = new ArrayList<>(1);
    Label label;
    String propertyName = "evidenceId";
    Object property = evidence.getId();
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

//      batch.setData("evidence_" + evidence.getId() + "_label", entity.getLabel());
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
        property = entityPropertyValue.toString();
      } else {
        property = evidence.getId();
      }

      nodeProperties.put(propertyName, entityPropertyValue);
    } else {
      label = DynLabel.label(configuration.getDefaultEntity());
      propertyName = "evidenceId";
      property = evidence.getId();
    }

    nodeProperties.put("evidenceId", evidence.getId());
    nodeProperties.put("name", evidence.getName());
    nodeProperties.put("path", evidence.getPath());

    MediaType mediaType = evidence.getMediaType();
    if (mediaType != null) {
      nodeProperties.put("type", mediaType.getType());
      nodeProperties.put("subType", mediaType.getSubtype());
    }

    String hash = evidence.getHash();
    if (hash != null && !hash.isEmpty()) {
      nodeProperties.put("hash", hash);
    }

    Object reader = evidence.getExtraAttribute("X-Reader");
    if (reader != null && !reader.toString().isEmpty()) {
      nodeProperties.put("source", reader.toString());
    }

    nodeProperties.put(propertyName, property);
    graphFileWriter.writeCreateNode(propertyName, property, nodeProperties, label, labels.toArray(new Label[labels.size()]));

    RelationshipType relationshipType = DynRelationshipType.withName(configuration.getDefaultRelationship());

    if (isIpedCase && parentIds.size() > 2) {
      // Cria vinculo com a entrada do datasource se ipedCase.
      Integer inputId = parentIds.get(2);
      graphFileWriter.writeRelationship(label, "evidenceId", inputId, label, propertyName, property, relationshipType);
    } else if (isIpedCase && parentIds.size() == 2) {
      // Cria vinculo da entrada com o datasource se ipedCase.
      Integer datasourceId = parentIds.get(1);
      graphFileWriter.writeRelationship(label, "evidenceId", datasourceId, label, propertyName, property,
          relationshipType);
    }

    processMetadata(evidence, label);
  }

  @SuppressWarnings("unchecked")
  private void processMetadata(EvidenceFile evidence, Label evidenceLabel) throws IOException {
    Map<String, Object> extraAttributeMap = evidence.getExtraAttributeMap();
    if (includeEvidence(evidence) && extraAttributeMap != null) {
      Set<Entry<String, Object>> entries = extraAttributeMap.entrySet();
      for (Entry<String, Object> entry : entries) {
        String key = entry.getKey();
        List<GraphEntity> entities = configuration.getEntities(key);
        if (entities != null) {
          for (GraphEntity entity : entities) {
            GraphEntityMetadata metadata = entity.getMetadata(key);
            processMatches(entity, metadata, evidence, (List<Object>) entry.getValue(), evidenceLabel);
          }
        }
      }
    }
  }

  private boolean includeEvidence(EvidenceFile evidence) {
    boolean include = true;

    Pattern includeCategoriesPattern = configuration.getIncludeCategoriesPattern();
    Pattern excludeCategoriesPattern = configuration.getExcludeCategoriesPattern();

    for (String category : evidence.getCategorySet()) {
      include = include && includeCategoriesPattern.matcher(category).matches();
      include = include && !excludeCategoriesPattern.matcher(category).matches();
    }

    return include;
  }

  private void processMatches(GraphEntity entity, GraphEntityMetadata metadata, EvidenceFile evidence,
      List<Object> matches, Label evidenceLabel) throws IOException {
    String labelName = entity.getLabel();
    String propertyName = metadata.getProperty();

    Label label = DynLabel.label(labelName);

    if (matches != null) {
      RelationshipType relationshipType = DynRelationshipType.withName(metadata.getRelationship());

      Set<String> controlSet = new HashSet<>();
      for (Object match : matches) {
        String propertyValue = match.toString();
        if (controlSet.add(propertyValue)) {
          graphFileWriter.writeMergeNode(label, propertyName, propertyValue);
          graphFileWriter.writeRelationship(evidenceLabel, "evidenceId", evidence.getId(), label, propertyName,
              propertyValue, relationshipType);
        }
      }
    }
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

}
