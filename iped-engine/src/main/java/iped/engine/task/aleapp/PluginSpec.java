package iped.engine.task.aleapp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jep.python.PyCallable;
import jep.python.PyObject;

/**
 * A Java representation of a Python PluginSpec object that retrieves attributes on-demand (lazily) when getter methods
 * are called.
 */
public class PluginSpec {

    private final PyObject pluginObject;

    private List<String> searchRegexes;

    public PluginSpec(PyObject pluginObject) {
        this.pluginObject = pluginObject;
    }

    // --- Getters that call getAttr() on-demand ---

    public String getName() {
        return this.pluginObject.getAttr("name", String.class);
    }

    public String getModuleName() {
        return this.pluginObject.getAttr("module_name", String.class);
    }

    public String getCategory() {
        return this.pluginObject.getAttr("category", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getSearchRegexes() {

        // mimics https://github.com/abrignoni/ALEAPP/blob/v3.4.0/aleapp.py#L353
        if (searchRegexes == null) {
            List<String> list = new ArrayList<>();
            Object search = this.pluginObject.getAttr("search");
            if (search instanceof String) {
                list.add((String) search);
            } else if (search instanceof Collection) {
                list.addAll((Collection<String>) search);
            } else if (search != null) {
                throw new IllegalArgumentException("Invalid plugin search: " + search.getClass() + " > " + search);
            }
            searchRegexes = list;
        }
        return searchRegexes;
    }

    public PyCallable getMethod() {
        return this.pluginObject.getAttr("method", PyCallable.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getArtifactInfo() {
        return this.pluginObject.getAttr("artifact_info", Map.class);
    }

    @Override
    public String toString() {
        return pluginObject.toString();
    }
}