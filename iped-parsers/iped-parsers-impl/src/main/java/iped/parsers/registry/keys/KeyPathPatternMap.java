package iped.parsers.registry.keys;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

public class KeyPathPatternMap<V> extends HashMap<String, V> {

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
                    if (matches(keyPath, value)) {
                        return get(value);
                    }
                }
            }
        }

        return result;
    }

    static public boolean matches(String test, String pattern) {
        StringTokenizer st = new StringTokenizer(pattern, "*");

        String tpart = test.replace("\\", "/");
        while (st.hasMoreTokens()) {
            String ppart = st.nextToken();
            if (!tpart.startsWith(ppart))
                return false;
            tpart = tpart.substring(ppart.length());
            int slachIndex = tpart.indexOf("/");
            if (slachIndex >= 0) {
                tpart = tpart.substring(slachIndex);
            }
        }

        return true;
    }
}
