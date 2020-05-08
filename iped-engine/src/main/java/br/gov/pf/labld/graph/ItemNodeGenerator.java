package br.gov.pf.labld.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;

import br.gov.pf.labld.cases.IpedCase;
import br.gov.pf.labld.cases.IpedCase.IpedDatasourceType;
import br.gov.pf.labld.graph.GraphConfiguration.GraphEntity;
import iped3.ICaseData;
import iped3.IItem;
import iped3.util.BasicProps;

/**
 * Old code of GraphTask not used for now was moved to this class.
 * 
 * @author Filipe Simoes
 *
 */
public class ItemNodeGenerator {
    
    private ICaseData caseData;
    private GraphConfiguration configuration;
    private GraphFileWriter graphFileWriter;
    
    public ItemNodeGenerator(ICaseData caseData, GraphConfiguration configuration, GraphFileWriter graphFileWriter) {
        this.caseData = caseData;
        this.configuration = configuration;
        this.graphFileWriter = graphFileWriter;
    }
    
    public void generateNodeForItem(IItem evidence) throws IOException {
        
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
    }

}
