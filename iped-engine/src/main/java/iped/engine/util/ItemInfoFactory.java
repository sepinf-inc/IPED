package iped.engine.util;

import iped.IItem;
import iped.parsers.util.ItemInfo;

public class ItemInfoFactory {

    public static ItemInfo getItemInfo(IItem evidence) {
        ItemInfo info = new ItemInfo(evidence.getId(), evidence.getHash(), evidence.getLabels(),
                evidence.getCategorySet(), evidence.getPath(), evidence.isCarved());
        // info.setEvidence(evidence);
        return info;
    }

}