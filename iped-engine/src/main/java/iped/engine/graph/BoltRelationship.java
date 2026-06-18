package iped.engine.graph;

import java.util.Map;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

/**
 * Read-only {@link Relationship} snapshot backed by a Neo4j Bolt driver relationship.
 * Start/end nodes are carried only when the originating query returned them (queries that
 * feed {@code getStartNode()}/{@code getEndNode()} add {@code startNode(r)}/{@code endNode(r)});
 * otherwise those accessors throw. See {@link BoltEntity}.
 */
public class BoltRelationship extends BoltEntity implements Relationship {

    private final RelationshipType type;
    private final Node startNode;
    private final Node endNode;

    public BoltRelationship(long id, String elementId, String type, Node startNode, Node endNode,
            Map<String, Object> properties) {
        super(id, elementId, properties);
        this.type = DynRelationshipType.withName(type);
        this.startNode = startNode;
        this.endNode = endNode;
    }

    static BoltRelationship from(org.neo4j.driver.types.Relationship rel, Node startNode, Node endNode) {
        return new BoltRelationship(rel.id(), rel.elementId(), rel.type(), startNode, endNode, rel.asMap());
    }

    @Override
    public RelationshipType getType() {
        return type;
    }

    @Override
    public boolean isType(RelationshipType type) {
        return this.type.name().equals(type.name());
    }

    @Override
    public Node getStartNode() {
        if (startNode == null) {
            throw unsupported();
        }
        return startNode;
    }

    @Override
    public Node getEndNode() {
        if (endNode == null) {
            throw unsupported();
        }
        return endNode;
    }

    @Override
    public Node getOtherNode(Node node) {
        if (startNode != null && node.getId() == startNode.getId()) {
            return endNode;
        }
        return startNode;
    }

    @Override
    public Node[] getNodes() {
        return new Node[] { startNode, endNode };
    }

    @Override
    public String toString() {
        return "BoltRelationship[" + id + "]";
    }
}
