package iped.engine.task;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.BytesRef;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.CmdLineArgs;
import iped.engine.config.ConfigurationManager;
import iped.engine.datasource.UfedXmlReader;
import iped.engine.lucene.DocValuesUtil;
import iped.engine.lucene.SlowCompositeReaderWrapper;
import iped.engine.task.carver.BaseCarveTask;
import iped.engine.task.index.IndexItem;
import iped.engine.task.index.IndexTask;
import iped.engine.util.Util;
import iped.exception.IPEDException;
import iped.properties.BasicProps;
import iped.utils.HashValue;

/**
 * Task to ignore already commited files into index. Commited containers without
 * all their subitems commited are not ignored to be processed again. Redefines
 * ids and parentIds of incomming items to be equal of commited items if they
 * have same trackID.
 * 
 * @author Luis Nassif
 *
 */
public class SkipCommitedTask extends AbstractTask {

    public static final String PARENTS_WITH_LOST_SUBITEMS = "PARENTS_WITH_LOST_SUBITEMS";

    public static final String DATASOURCE_NAMES = "CMD_LINE_DATASOURCE_NAMES";

    public static final String trackID_ID_MAP = "trackID_ID_MAP";

    private static Logger logger = LogManager.getLogger(SkipCommitedTask.class);

    private static HashValue[] commitedtrackIDs;

    private static Set<HashValue> parentsWithLostSubitems = Collections.synchronizedSet(new TreeSet<>());

    private static Set<HashValue> removedParents = Collections.synchronizedSet(new TreeSet<>());

    private static Map<HashValue, Integer> globalToIdMap = new HashMap<>();

    private static HashMap<String, String> prevRootNameToEvidenceUUID = new HashMap<>();

    private static CmdLineArgs args;

    private static AtomicBoolean inited = new AtomicBoolean();

    public static boolean isAlreadyCommited(IItem item) {
        if (commitedtrackIDs == null) {
            return false;
        }
        HashValue trackID = new HashValue(Util.getTrackID(item));
        return Arrays.binarySearch(commitedtrackIDs, trackID) >= 0;
    }

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Collections.emptyList();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        if (inited.getAndSet(true)) {
            return;
        }

        try (IndexReader reader = DirectoryReader.open(worker.writer, true, true)) {
            LeafReader aReader = SlowCompositeReaderWrapper.wrap(reader);

            SortedDocValues evidenceUUIDs = aReader.getSortedDocValues(BasicProps.EVIDENCE_UUID);
            for (int doc = 0; doc < aReader.maxDoc(); doc++) {
                String uuid = DocValuesUtil.getVal(evidenceUUIDs, doc);
                if (uuid != null && !prevRootNameToEvidenceUUID.containsValue(uuid)) {
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

            SortedDocValues persistIds = aReader.getSortedDocValues(IndexItem.TRACK_ID);
            int size = persistIds == null ? 0 : persistIds.getValueCount();
            commitedtrackIDs = new HashValue[size];
            for (int ord = 0; ord < commitedtrackIDs.length; ord++) {
                String trackID = persistIds.lookupOrd(ord).utf8ToString();
                commitedtrackIDs[ord] = new HashValue(trackID);
            }
            // Arrays.sort(trackIDs);

            SortedDocValues globalParents = aReader.getSortedDocValues(IndexItem.PARENT_TRACK_ID);
            SortedDocValues hasChildValues = aReader.getSortedDocValues(IndexItem.HASCHILD);
            SortedDocValues isDirValues = aReader.getSortedDocValues(IndexItem.ISDIR);
            SortedDocValues isRootValues = aReader.getSortedDocValues(IndexItem.ISROOT);
            SortedDocValues hasSplittedText = aReader.getSortedDocValues(IndexTask.TEXT_SPLITTED);
            NumericDocValues prevParentIds = aReader.getNumericDocValues(IndexItem.PARENTID);
            NumericDocValues prevIds = aReader.getNumericDocValues(IndexItem.ID);
            for (int doc = 0; doc < aReader.maxDoc(); doc++) {
                String hashVal = globalParents == null ? null : DocValuesUtil.getVal(globalParents, doc);
                if (hashVal != null && !hashVal.isEmpty()) {
                    HashValue persistParent = new HashValue(hashVal);
                    if (prevParentIds != null && Arrays.binarySearch(commitedtrackIDs, persistParent) < 0) {
                        globalToIdMap.put(persistParent, DocValuesUtil.get(prevParentIds, doc).intValue());
                    }
                }
                boolean hasChild = hasChildValues != null && Boolean.valueOf(DocValuesUtil.getVal(hasChildValues, doc));
                boolean isDir = isDirValues != null && Boolean.valueOf(DocValuesUtil.getVal(isDirValues, doc));
                boolean isRoot = isRootValues != null && Boolean.valueOf(DocValuesUtil.getVal(isRootValues, doc));
                boolean isTexSplitted = hasSplittedText != null && Boolean.valueOf(DocValuesUtil.getVal(hasSplittedText, doc));
                if (prevIds != null && persistIds != null && (hasChild || isDir || isRoot || isTexSplitted)) {
                    HashValue trackID = new HashValue(DocValuesUtil.getVal(persistIds, doc));
                    globalToIdMap.put(trackID, DocValuesUtil.get(prevIds, doc).intValue());
                }
            }

            caseData.putCaseObject(trackID_ID_MAP, globalToIdMap);

            collectParentsWithoutAllSubitems(aReader, IndexItem.CONTAINER_TRACK_ID, ParsingTask.NUM_SUBITEMS);
            collectParentsWithoutAllSubitems(aReader, IndexItem.PARENT_TRACK_ID, BaseCarveTask.NUM_CARVED_AND_FRAGS);

            caseData.putCaseObject(PARENTS_WITH_LOST_SUBITEMS, parentsWithLostSubitems);

            logger.info("Commited items: {}", commitedtrackIDs.length);
            logger.info("Parents with lost subitems: {}", parentsWithLostSubitems.size());

        } catch (IndexNotFoundException e) {
            commitedtrackIDs = new HashValue[0];
        }

    }

    private void collectParentsWithoutAllSubitems(LeafReader aReader, String parentIdField, String subitemCountField)
            throws IOException {
        // reset doc values to iterate again
        SortedDocValues persistIds = aReader.getSortedDocValues(IndexItem.TRACK_ID);
        NumericDocValues ids = aReader.getNumericDocValues(IndexItem.ID);
        SortedDocValues parentContainers = aReader.getSortedDocValues(parentIdField);

        if (parentContainers == null || persistIds == null || ids == null) {
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
            Long longId = DocValuesUtil.get(ids, doc);
            if (longId == null) {
                continue;
            }
            int id = longId.intValue();
            int ord = DocValuesUtil.getOrd(parentContainers, doc);
            if (ord != -1 && !countedIds.get(id)) {
                if (parentIdField == IndexItem.CONTAINER_TRACK_ID || subitems == null
                        || !Boolean.valueOf(DocValuesUtil.getVal(subitems, doc))) {
                    referencingSubitems[ord]++;
                }
            }
            // splited items occur more than once, so we track seen ids
            countedIds.set(id);
        }

        for (int doc = 0; doc < aReader.maxDoc(); doc++) {
            Long subitemsCount = DocValuesUtil.get(numSubitems, doc);
            if (subitemsCount != null) {
                if (!persistIds.advanceExact(doc))
                    continue;

                BytesRef persistId = persistIds.lookupOrd(persistIds.ordValue());
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
        commitedtrackIDs = null;
        parentsWithLostSubitems.clear();
        removedParents.clear();
        globalToIdMap.clear();
        prevRootNameToEvidenceUUID.clear();
    }

    // Check again parents that are going to be processed in later processing queues
    // to avoid ignoring them in a second pass in this task.
    public static void checkAgainLaterProcessedParents(IItem item) {
        HashValue trackID = new HashValue(Util.getTrackID(item));
        if (removedParents.remove(trackID)) {
            parentsWithLostSubitems.add(trackID);
        }
    }

    @Override
    protected void process(IItem item) throws Exception {

        // must be calculated first, in all cases, to allow recovering in the future
        HashValue trackID = new HashValue(Util.getTrackID(item));

        if (item.getExtraAttribute(IndexItem.PARENT_TRACK_ID) == null && !item.isRoot()) {
            // this property is needed when resuming processing to get a previous parent id
            // referenced by subitems which parents were not commited, then when
            // reprocessing parents, their id can be updated to the previous value, so
            // parent-child relationships will be preserved.
            throw new RuntimeException(IndexItem.PARENT_TRACK_ID + " must be stored for all items!");
        }

        if (!args.isContinue()) {
            return;
        }

        // ignore already committed items. If they are containers without all their
        // subitems committed, process again
        if (Arrays.binarySearch(commitedtrackIDs, trackID) >= 0) {
            // we must "remove" seen containers from set below. It is possible for the same
            // container to be enqueued twice: if it is a subItem/carved of some allocated
            // parent being processed again, coming from some datasource reader, AND if it
            // was already committed, coming from the index.
            if (!parentsWithLostSubitems.remove(trackID)) {
                item.setToIgnore(true);
                return;
            } else {
                removedParents.add(trackID);
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
