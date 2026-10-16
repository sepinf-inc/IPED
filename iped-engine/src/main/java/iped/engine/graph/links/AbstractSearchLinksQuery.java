package iped.engine.graph.links;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import iped.engine.graph.GraphService;
import iped.engine.graph.PathQueryListener;

public abstract class AbstractSearchLinksQuery implements SearchLinksQuery {

    @Override
    public void search(String start, String end, GraphService graphService, PathQueryListener listener) {
        Map<String, Object> params = new HashMap<>(2);
        params.put("start", start);
        params.put("end", end);

        // The .cypher resource returns a column named "path"; the query now runs over Bolt.
        graphService.searchPaths(getQuery(), params, listener);
    }

    protected String getQuery() {
        try (InputStream in = new BufferedInputStream(openQueryFile())) {
            return IOUtils.toString(in, "utf-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected InputStream openQueryFile() {
        String resourceName = this.getClass().getSimpleName() + ".cypher";
        return this.getClass().getResourceAsStream(resourceName);
    }

}
