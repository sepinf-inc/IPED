package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.datasource.UfedXmlReader;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.IPEDException;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;
import iped3.util.BasicProps;

/**
 * Task to ignore already commited files into index. Commited containers without
 * all their subitems commited are not ignored to be processed again. Redefines
 * ids and parentIds of incomming items to be equal of commited items if they
 * have same persistentId.
 * 
 * @author Luis Nassif
 *
 */
public class SkipCommitedTask extends AbstractTask {

    public static final String PARENTS_WITH_LOST_SUBITEMS = "PARENTS_WITH_LOST_SUBITEMS";

    public static final String DATASOURCE_NAMES = "CMD_LINE_DATASOURCE_NAMES";

    public static final String GLOBALID_ID_MAP = "GLOBALID_ID_MAP";

    private static HashValue[] commitedPersistentIds;

    private static Set<HashValue> parentsWithLostSubitems = Collections.synchronizedSet(new TreeSet<>());

    private static Map<HashValue, Integer> persistentToIdMap = new HashMap<>();

    private static HashMap<String, String> prevRootNameToEvidenceUUID = new HashMap<>();

    private static CmdLineArgs args;

    private static AtomicBoolean inited = new AtomicBoolean();

    public static boolean isAlreadyCommited(IItem item) {
        if (commitedPersistentIds == null) {
            return false;
        }
        HashValue persistentId = new HashValue(Util.getPersistentId(item));
        return Arrays.binarySearch(commitedPersistentIds, persistentId) >= 0;
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {

        if (inited.getAndSet(true)) {
            return;
        }

        try (IndexReader reader = DirectoryReader.open(worker.writer, true)) {
            LeafReader aReader = SlowCompositeReaderWrapper.wrap(reader);

            SortedDocValues evidenceUUIDs = aReader.getSortedDocValues(BasicProps.EVIDENCE_UUID);
            for (int doc = 0; doc < aReader.maxDoc(); doc++) {
                String uuid = evidenceUUIDs.get(doc).utf8ToString();
                if (!prevRootNameToEvidenceUUID.containsValue(uuid)) {
                    Document luceneDoc = aReader.document(doc);
                    String path = luceneDoc.get(BasicProps.PATH);
                    prevRootNameToEvidenceUUID.put(Util.getRootName(path), uuid);
                }
            }
            args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());

            Set<String> evidenceNames = (Set<String>) caseData.getCaseObject(SkipCommitedTask.DATASOURCE_NAMES);
            for (String name : evidenceNames) {
                if (!args.isContinue() && prevRootNameToEvidenceUUID.containsKey(name))
                    throw new IPEDException("Evidence name already exists in case: " + name);
            }

            if (!args.isContinue()) {
                return;
            }

            SortedDocValues persistIds = aReader.getSortedDocValues(IndexItem.PERSISTENT_ID);
            int size = persistIds == null ? 0 : persistIds.getValueCount();
            commitedPersistentIds = new HashValue[size];
            for (int ord = 0; ord < commitedPersistentIds.length; ord++) {
                String persistentId = persistIds.lookupOrd(ord).utf8ToString();
                commitedPersistentIds[ord] = new HashValue(persistentId);
            }
            // Arrays.sort(persistentIds);

            SortedDocValues persistentParents = aReader.getSortedDocValues(IndexItem.PARENT_PERSISTENT_ID);
            SortedDocValues hasChildValues = aReader.getSortedDocValues(IndexItem.HASCHILD);
            SortedDocValues isDirValues = aReader.getSortedDocValues(IndexItem.ISDIR);
            SortedDocValues isRootValues = aReader.getSortedDocValues(IndexItem.ISROOT);
            SortedDocValues hasSplittedText = aReader.getSortedDocValues(IndexTask.TEXT_SPLITTED);
            NumericDocValues prevParentIds = aReader.getNumericDocValues(IndexItem.PARENTID);
            NumericDocValues prevIds = aReader.getNumericDocValues(IndexItem.ID);
            for (int doc = 0; doc < aReader.maxDoc(); doc++) {
                String hashVal = persistentParents.get(doc).utf8ToString();
                if (!hashVal.isEmpty()) {
                    HashValue persistParent = new HashValue(hashVal);
                    if (Arrays.binarySearch(commitedPersistentIds, persistParent) < 0) {
                        persistentToIdMap.put(persistParent, (int) prevParentIds.get(doc));
                    }
                }
                boolean hasChild = Boolean.valueOf(hasChildValues.get(doc).utf8ToString());
                boolean isDir = Boolean.valueOf(isDirValues.get(doc).utf8ToString());
                boolean isRoot = Boolean.valueOf(isRootValues.get(doc).utf8ToString());
                boolean isTexSplitted = hasSplittedText != null
                        ? Boolean.valueOf(hasSplittedText.get(doc).utf8ToString())
                        : false;
                if (hasChild || isDir || isRoot || isTexSplitted) {
                    HashValue persistentId = new HashValue(persistIds.get(doc).utf8ToString());
                    persistentToIdMap.put(persistentId, (int) prevIds.get(doc));
                }
            }

            caseData.putCaseObject(GLOBALID_ID_MAP, persistentToIdMap);

            collectParentsWithoutAllSubitems(aReader, persistIds, prevIds, IndexItem.CONTAINER_PERSISTENT_ID,
                    ParsingTask.NUM_SUBITEMS);
            collectParentsWithoutAllSubitems(aReader, persistIds, prevIds, IndexItem.PARENT_PERSISTENT_ID,
                    BaseCarveTask.NUM_CARVED_AND_FRAGS);

            caseData.putCaseObject(PARENTS_WITH_LOST_SUBITEMS, parentsWithLostSubitems);

        } catch (IndexNotFoundException e) {
            commitedPersistentIds = new HashValue[0];
        }

    }

    private void collectParentsWithoutAllSubitems(LeafReader aReader, SortedDocValues persistIds, NumericDocValues ids,
            String parentIdField, String subitemCountField) throws IOException {
        SortedDocValues parentContainers = aReader.getSortedDocValues(parentIdField);
        if (parentContainers == null) {
            return;
        }
        NumericDocValues numSubitems = aReader.getNumericDocValues(subitemCountField);
        if (numSubitems == null) {
            return;
        }
        SortedDocValues subitems = aReader.getSortedDocValues(BasicProps.SUBITEM);

        int[] referencingSubitems = new int[parentContainers.getValueCount()];

        BitSet countedIds = new BitSet();
        for (int doc = 0; doc < aReader.maxDoc(); doc++) {
            int id = (int) ids.get(doc);
            int ord = parentContainers.getOrd(doc);
            if (ord != -1 && !countedIds.get(id)) {
                if (parentIdField == IndexItem.CONTAINER_PERSISTENT_ID || subitems == null
                        || !Boolean.valueOf(subitems.get(doc).utf8ToString())) {
                    referencingSubitems[ord]++;
                }
            }
            // splited items occur more than once, so we track seen ids
            countedIds.set(id);
        }

        Bits docsWithField = aReader.getDocsWithField(subitemCountField);
        for (int doc = 0; doc < aReader.maxDoc(); doc++) {
            if (docsWithField.get(doc)) {
                int subitemsCount = (int) numSubitems.get(doc);
                BytesRef persistId = persistIds.get(doc);
                int ord = parentContainers.lookupTerm(persistId);
                int carvedIgnored = 0;
                if (subitemCountField == BaseCarveTask.NUM_CARVED_AND_FRAGS) {
                    carvedIgnored = stats.getCarvedIgnoredNum(new HashValue(persistId.utf8ToString()));
                }
                int references = ord < 0 ? 0 : referencingSubitems[ord];
                if (subitemsCount != references + carvedIgnored) {
                    parentsWithLostSubitems.add(new HashValue(persistId.utf8ToString()));
                    // System.out.println("Parent with lost child " + persistId.utf8ToString() + "
                    // subitems " + subitemsCount +
                    // " carvedIgnored " + carvedIgnored + (ord >= 0 ? " references " +
                    // referencingSubitems[ord] : ""));
                }
            }
        }
    }

    @Override
    public void finish() throws Exception {
        commitedPersistentIds = null;
        parentsWithLostSubitems.clear();
        persistentToIdMap.clear();
        prevRootNameToEvidenceUUID.clear();
    }

    @Override
    protected void process(IItem item) throws Exception {

        // must be calculated first, in all cases, to allow recovering in the future
        HashValue persistentId = new HashValue(Util.getPersistentId(item));
        Util.computeParentPersistentId(item);

        if (!args.isContinue()) {
            return;
        }

        // ignore already commited items. If they are containers without all their
        // subitems commited, process again
        if (Arrays.binarySearch(commitedPersistentIds, persistentId) >= 0) {
            if (!parentsWithLostSubitems.contains(persistentId)) {
                item.setToIgnore(true);
                return;
            }
        }

        // reset number of carved ignored subitems because parent will be processed
        // again
        stats.resetCarvedIgnored(item);

        // change evidenceUUID to previous processing evidenceUUID
        String rootPrefix = Util.getRootName(item.getPath());
        String oldUUID = prevRootNameToEvidenceUUID.get(rootPrefix);
        if (oldUUID != null) {
            if (caseData.getCaseObject(UfedXmlReader.MSISDN_PROP + oldUUID) == null) {
                synchronized (caseData) {
                    Object msisdns = caseData.getCaseObject(UfedXmlReader.MSISDN_PROP + item.getDataSource().getUUID());
                    caseData.putCaseObject(UfedXmlReader.MSISDN_PROP + oldUUID, msisdns);
                }
            }
            item.getDataSource().setUUID(oldUUID);
        }
    }

}
