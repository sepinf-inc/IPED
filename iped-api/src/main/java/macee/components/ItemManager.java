package macee.components;

import macee.CaseItem;
import macee.Resolver;
import macee.collection.CaseItemCollection;

public interface ItemManager extends Resolver<SimpleItemRef, CaseItem> {

    CaseItemCollection getListedItems();

    CaseItemCollection getHighlightedItems();

    CaseItemCollection getCheckedItems();

    CaseItemCollection getAllItems();

    CaseItemCollection getItemsFromCase(String caseGuid);

    CaseItemCollection getItemsFromEvidenceItem(String evidenceGuid);

    CaseItemCollection getItemsFromDataSource(String dataSourceGuid);
}
