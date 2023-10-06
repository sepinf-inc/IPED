package iped.engine.util;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class PathPatternMap<V> extends HashMap<String, V> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;


    public V getPatternMatch(String keyPath) {
        V result = get(keyPath);

        if (result == null) {
            Set<String> collection = keySet();
            for (Iterator<String> iterator = collection.iterator(); iterator.hasNext();) {
                String value = iterator.next();
                if (value.contains("*")) {
                    String pattern = value;
                    if (pattern.startsWith("*/")) {
                        pattern = "*" + value;
                    }
                    if (FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(Path.of(keyPath))) {
                        return get(value);
                    }
                }
            }
        }

        return result;
    }
}
