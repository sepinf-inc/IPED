package iped.engine.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;

/**
 * Read-only {@link Path} snapshot backed by a Neo4j Bolt driver path. Iteration yields the
 * alternating node/relationship sequence (node, rel, node, ...), and each relationship carries
 * its start/end node (resolved from the driver path segments). See {@link BoltEntity}.
 */
public class BoltPath implements Path {

    private final List<Node> nodes = new ArrayList<>();
    private final List<Relationship> relationships = new ArrayList<>();
    private final List<Entity> sequence = new ArrayList<>();

    static BoltPath from(org.neo4j.driver.types.Path path) {
        BoltPath result = new BoltPath();
        BoltNode start = BoltNode.from(path.start());
        result.nodes.add(start);
        result.sequence.add(start);
        for (org.neo4j.driver.types.Path.Segment segment : path) {
            BoltNode segStart = BoltNode.from(segment.start());
            BoltNode segEnd = BoltNode.from(segment.end());
            BoltRelationship rel = BoltRelationship.from(segment.relationship(), segStart, segEnd);
            result.relationships.add(rel);
            result.sequence.add(rel);
            result.nodes.add(segEnd);
            result.sequence.add(segEnd);
        }
        return result;
    }

    @Override
    public Node startNode() {
        return nodes.get(0);
    }

    @Override
    public Node endNode() {
        return nodes.get(nodes.size() - 1);
    }

    @Override
    public Relationship lastRelationship() {
        return relationships.isEmpty() ? null : relationships.get(relationships.size() - 1);
    }

    @Override
    public Iterable<Relationship> relationships() {
        return relationships;
    }

    @Override
    public Iterable<Relationship> reverseRelationships() {
        List<Relationship> reversed = new ArrayList<>(relationships);
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public Iterable<Node> nodes() {
        return nodes;
    }

    @Override
    public Iterable<Node> reverseNodes() {
        List<Node> reversed = new ArrayList<>(nodes);
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public int length() {
        return relationships.size();
    }

    @Override
    public Iterator<Entity> iterator() {
        return sequence.iterator();
    }

    @Override
    public String toString() {
        return "BoltPath[length=" + length() + "]";
    }
}
