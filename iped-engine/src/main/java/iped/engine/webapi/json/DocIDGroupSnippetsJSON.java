package iped.engine.webapi.json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;

/**
 * DocIDGroupSnippetsJSON is a {@link DocIDGroupJSON} group extended with the
 * per-id snippet map (design 07c):
 * { "source": "A", "ids": [1, 2], "snippets": { "1": "...text excerpt..." } }
 * Keys are item ids as strings; ids without an available snippet are absent
 * from the map (never an empty or null value), so the consumer correlates
 * snippets with ids and falls back to /text for the missing ones.
 */
public class DocIDGroupSnippetsJSON extends DocIDGroupJSON {

    private Map<String, String> snippets = new LinkedHashMap<String, String>();

    public DocIDGroupSnippetsJSON() {
    }

    public DocIDGroupSnippetsJSON(String source, List<Integer> ids, Map<String, String> snippets) {
        super(source, ids);
        if (snippets != null) {
            this.snippets = snippets;
        }
    }

    @ApiModelProperty
    public Map<String, String> getSnippets() {
        return snippets;
    }

    public void setSnippets(Map<String, String> snippets) {
        this.snippets = snippets;
    }
}
