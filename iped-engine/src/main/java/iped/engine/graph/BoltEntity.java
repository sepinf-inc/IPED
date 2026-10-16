package iped.engine.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.NotFoundException;

/**
 * Base for the detached, read-only {@code org.neo4j.graphdb} snapshots returned by
 * {@link GraphServiceImpl} over Bolt. Keeping the {@code graphdb-api} interfaces as the
 * boundary type lets the graph UI stay unchanged while the Neo4j engine runs out of
 * process. Mutation and live traversal are unsupported on this side. Java 21 migration.
 */
abstract class BoltEntity implements Entity {

    protected final long id;
    protected final String elementId;
    protected final Map<String, Object> properties;

    BoltEntity(long id, String elementId, Map<String, Object> properties) {
        this.id = id;
        this.elementId = elementId;
        this.properties = properties != null ? properties : Collections.emptyMap();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getElementId() {
        return elementId;
    }

    @Override
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    @Override
    public Object getProperty(String key) {
        // Match the org.neo4j.graphdb.Entity contract: callers (e.g. GraphModel.getLabel) rely on
        // NotFoundException to probe for absent properties; returning null would surface as an NPE.
        if (!properties.containsKey(key)) {
            throw new NotFoundException("No such property '" + key + "'.");
        }
        return properties.get(key);
    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        return properties.containsKey(key) ? properties.get(key) : defaultValue;
    }

    @Override
    public Iterable<String> getPropertyKeys() {
        return properties.keySet();
    }

    @Override
    public Map<String, Object> getProperties(String... keys) {
        if (keys == null || keys.length == 0) {
            return getAllProperties();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            if (properties.containsKey(key)) {
                result.put(key, properties.get(key));
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> getAllProperties() {
        return new HashMap<>(properties);
    }

    @Override
    public void setProperty(String key, Object value) {
        throw unsupported();
    }

    @Override
    public Object removeProperty(String key) {
        throw unsupported();
    }

    @Override
    public void delete() {
        throw unsupported();
    }

    static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Graph entities read over Bolt are detached read-only snapshots; "
                + "mutation and live traversal are not supported on the client side.");
    }
}
