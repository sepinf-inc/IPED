package iped.engine.webapi.json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.annotations.ApiModelProperty;

/**
 * SourceToIDsSnippetsJSON is the snippet-enabled variant of
 * {@link SourceToIDsPageJSON} (design 07c): the exact same paginated body
 * ("data" groups with the same ids in the same order, same "total"), where
 * every group additionally carries a "snippets" map (id as string -> plain
 * text excerpt). It is used only when the snippet query parameter is greater
 * than zero, so the unpaginated legacy body ({@link SourceToIDsJSON}) and the
 * plain paginated body ({@link SourceToIDsPageJSON}) stay byte-identical to
 * the pre-feature responses.
 */
public class SourceToIDsSnippetsJSON extends SourceToIDsPageJSON {

    private Map<String, Map<String, String>> snippetsBySource = new HashMap<String, Map<String, String>>();

    public SourceToIDsSnippetsJSON() {
    }

    public SourceToIDsSnippetsJSON(List<DocIDJSON> docs, int total,
            Map<String, Map<String, String>> snippetsBySource) {
        super(docs, total);
        if (snippetsBySource != null) {
            this.snippetsBySource = snippetsBySource;
        }
    }

    @Override
    @ApiModelProperty
    public List<DocIDGroupJSON> getData() {
        List<DocIDGroupJSON> result = new ArrayList<DocIDGroupJSON>();
        for (DocIDGroupJSON group : super.getData()) {
            result.add(new DocIDGroupSnippetsJSON(group.getSource(), group.getIds(),
                    snippetsBySource.get(group.getSource())));
        }
        return result;
    }
}
