package iped.engine.util;

import java.util.List;

import iped.data.IItem;
import iped.engine.task.HashDBLookupTask;
import iped.parsers.util.ItemInfo;
import iped.properties.ExtraProperties;

public class ItemInfoFactory {

    public static ItemInfo getItemInfo(IItem evidence) {
        ItemInfo info = new ItemInfo(evidence.getId(), evidence.getHash(), evidence.getLabels(),
                evidence.getCategorySet(), evidence.getPath(), evidence.isCarved(), isKnown(evidence));
        // info.setEvidence(evidence);
        return info;
    }

    // Check if there is a single status, and it is "known"
    private static boolean isKnown(IItem evidence) {
        Object hashDbStatus = evidence.getExtraAttribute(ExtraProperties.HASHDB_STATUS);
        if (hashDbStatus != null) {
            if (hashDbStatus instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> status = (List<String>) hashDbStatus;
                if (status.size() == 1 && status.get(0).equals(HashDBLookupTask.KNOWN_VALUE)) {
                    return true;
                }
            } else if (hashDbStatus instanceof String) {
                String status = (String) hashDbStatus;
                if (status.equals(HashDBLookupTask.KNOWN_VALUE)) {
                    return true;
                }
            }
        }
        return false;
    }
}
