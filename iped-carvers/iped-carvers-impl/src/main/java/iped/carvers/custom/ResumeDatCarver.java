package iped.carvers.custom;

import java.util.Map;

public class ResumeDatCarver extends TorrentCarver {
    @Override
    public boolean isValid(Map<String, Object> dict) {
        for (String key : dict.keySet()) {
            Object value = dict.get(key);
            if (value != null && value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                if (subMap.containsKey("rootdir") || subMap.containsKey("path")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected String getCarvedNamePrefix() {
        return "Carved-Resume-";
    }

    @Override
    protected String getCarvedNameSuffix() {
        return ".dat";
    }
}
