package dpf.sp.gpinf.indexer.util;

import gpinf.dev.data.EvidenceFile;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;

public class ItemInfoFactory {

	public static ItemInfo getItemInfo(EvidenceFile evidence){
		ItemInfo info = new ItemInfo(evidence.getId(), evidence.getCategorySet(), evidence.getPath(), evidence.isCarved());
		//info.setEvidence(evidence);
		return info;
	}

}
