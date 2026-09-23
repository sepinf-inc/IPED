package iped.engine.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterable;

/**
 * Read-only {@link Node} snapshot backed by a Neo4j Bolt driver node. See {@link BoltEntity}.
 */
public class BoltNode extends BoltEntity implements Node {

    private final List<Label> labels;

    /** Node degree captured by the producing query (COUNT {{ (n)--() }}), or -1 when not requested. */
    private final int degree;

    public BoltNode(long id, String elementId, Iterable<String> labelNames, Map<String, Object> properties) {
        this(id, elementId, labelNames, properties, -1);
    }

    public BoltNode(long id, String elementId, Iterable<String> labelNames, Map<String, Object> properties,
            int degree) {
        super(id, elementId, properties);
        this.degree = degree;
        this.labels = new ArrayList<>();
        if (labelNames != null) {
            for (String name : labelNames) {
                labels.add(DynLabel.label(name));
            }
        }
    }

    static BoltNode from(org.neo4j.driver.types.Node node) {
        return new BoltNode(node.id(), node.elementId(), node.labels(), node.asMap());
    }

    static BoltNode from(org.neo4j.driver.types.Node node, int degree) {
        return new BoltNode(node.id(), node.elementId(), node.labels(), node.asMap(), degree);
    }

    @Override
    public Iterable<Label> getLabels() {
        return labels;
    }

    @Override
    public boolean hasLabel(Label label) {
        for (Label l : labels) {
            if (l.name().equals(label.name())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "BoltNode[" + id + "]";
    }

    // --- live traversal / mutation: unsupported on detached Bolt snapshots ---

    @Override
    public ResourceIterable<Relationship> getRelationships() {
        throw unsupported();
    }

    @Override
    public boolean hasRelationship() {
        throw unsupported();
    }

    @Override
    public ResourceIterable<Relationship> getRelationships(RelationshipType... types) {
        throw unsupported();
    }

    @Override
    public ResourceIterable<Relationship> getRelationships(Direction direction, RelationshipType... types) {
        throw unsupported();
    }

    @Override
    public boolean hasRelationship(RelationshipType... types) {
        throw unsupported();
    }

    @Override
    public boolean hasRelationship(Direction direction, RelationshipType... types) {
        throw unsupported();
    }

    @Override
    public ResourceIterable<Relationship> getRelationships(Direction direction) {
        throw unsupported();
    }

    @Override
    public boolean hasRelationship(Direction direction) {
        throw unsupported();
    }

    @Override
    public Relationship getSingleRelationship(RelationshipType type, Direction direction) {
        throw unsupported();
    }

    @Override
    public Relationship createRelationshipTo(Node otherNode, RelationshipType type) {
        throw unsupported();
    }

    @Override
    public Iterable<RelationshipType> getRelationshipTypes() {
        throw unsupported();
    }

    // Degree is read by the graph UI (GraphModel) only to size nodes. It is served from the snapshot
    // value carried by the producing query; when that query did not request it we report 0 so
    // rendering falls back to the base node size instead of failing. The direction/type-filtered
    // variants cannot be answered from a detached snapshot, so they report the same total degree.

    @Override
    public int getDegree() {
        return degree < 0 ? 0 : degree;
    }

    @Override
    public int getDegree(RelationshipType type) {
        return getDegree();
    }

    @Override
    public int getDegree(Direction direction) {
        return getDegree();
    }

    @Override
    public int getDegree(RelationshipType type, Direction direction) {
        return getDegree();
    }

    @Override
    public void addLabel(Label label) {
        throw unsupported();
    }

    @Override
    public void removeLabel(Label label) {
        throw unsupported();
    }
}
