package dpf.sp.gpinf.indexer.util;

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.Item;

public class ItemInfoFactory {

  public static ItemInfo getItemInfo(Item evidence) {
    ItemInfo info = new ItemInfo(evidence.getId(), evidence.getHash(), evidence.getCategorySet(), evidence.getPath(), evidence.isCarved());
    //info.setEvidence(evidence);
    return info;
  }

}
