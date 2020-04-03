package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import dpf.sp.gpinf.indexer.CmdLineArgs;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.HashValue;
import dpf.sp.gpinf.indexer.util.Util;
import iped3.IItem;
import iped3.util.BasicProps;

/**
 * Task to ignore already commited files into index.
 * Commited containers without all their subitems commited are not ignored to be processed again.
 * Redefines ids and parentIds of incomming items to be equal of commited items if they have same persistentId.
 * 
 * @author Luis Nassif
 *
 */
public class SkipCommitedTask extends AbstractTask{
    
    public static final String PARENTS_WITH_LOST_SUBITEMS = "PARENTS_WITH_LOST_SUBITEMS";
    
    private static HashValue[] commitedPersistentIds;
    
    private static Set<HashValue> parentsWithLostSubitems = Collections.synchronizedSet(new TreeSet<>());
    
    private static Map<HashValue, Long> persistentToIdMap = new HashMap<>();
    
    private static HashMap<String, String> rootNameToEvidenceUUID = new HashMap<>();
    
    private CmdLineArgs args;
    
    public static boolean isAlreadyCommited(IItem item) {
        if(commitedPersistentIds == null) {
            return false;
        }
        HashValue persistentId = new HashValue(Util.getPersistentId(item));
        return Arrays.binarySearch(commitedPersistentIds, persistentId) >= 0;
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        args = (CmdLineArgs) caseData.getCaseObject(CmdLineArgs.class.getName());
        if(!args.isContinue()) {
            return;
        }
        
        if(commitedPersistentIds != null) {
            return;
        }
        
        try(IndexReader reader = DirectoryReader.open(worker.writer, true)){
            AtomicReader aReader = SlowCompositeReaderWrapper.wrap(reader);
            SortedDocValues persistIds = aReader.getSortedDocValues(IndexItem.PERSISTENT_ID);
            commitedPersistentIds = new HashValue[persistIds.getValueCount()];
            for(int ord = 0; ord < commitedPersistentIds.length; ord++) {
                String persistentId = persistIds.lookupOrd(ord).utf8ToString();
                commitedPersistentIds[ord] = new HashValue(persistentId);
            }
            //Arrays.sort(persistentIds);
            
            SortedDocValues persistentParents = aReader.getSortedDocValues(IndexItem.PARENT_PERSISTENT_ID);
            SortedDocValues hasChildValues = aReader.getSortedDocValues(IndexItem.HASCHILD);
            SortedDocValues hasSplittedText = aReader.getSortedDocValues(IndexTask.TEXT_SPLITTED);
            SortedDocValues evidenceUUIDs = aReader.getSortedDocValues(BasicProps.EVIDENCE_UUID);
            NumericDocValues prevParentIds = aReader.getNumericDocValues(IndexItem.PARENTID);
            NumericDocValues prevIds = aReader.getNumericDocValues(IndexItem.ID);
            for(int doc = 0; doc < aReader.maxDoc(); doc++) {
                String hashVal = persistentParents.get(doc).utf8ToString();
                if(!hashVal.isEmpty()) {
                    HashValue persistParent = new HashValue(hashVal);
                    if(Arrays.binarySearch(commitedPersistentIds, persistParent) < 0) {
                        persistentToIdMap.put(persistParent, prevParentIds.get(doc));
                    }
                }
                boolean hasChild =  Boolean.valueOf(hasChildValues.get(doc).utf8ToString());
                boolean isTexSplitted = hasSplittedText != null ? Boolean.valueOf(hasSplittedText.get(doc).utf8ToString()) : false;
                if(hasChild || isTexSplitted) {
                    HashValue persistentId = new HashValue(persistIds.get(doc).utf8ToString());
                    persistentToIdMap.put(persistentId, prevIds.get(doc));
                }
                
                //TODO abort processing if user defines 2 evidences with same name
                String uuid = evidenceUUIDs.get(doc).utf8ToString();
                if(!rootNameToEvidenceUUID.containsValue(uuid)) {
                    Document luceneDoc = aReader.document(doc);
                    String path = luceneDoc.get(BasicProps.PATH);
                    rootNameToEvidenceUUID.put(getRootPrefix(path), uuid);
                }
            }
            
            collectParentsWithoutAllSubitems(aReader, persistIds, prevIds, IndexItem.CONTAINER_PERSISTENT_ID, ParsingTask.NUM_SUBITEMS);
            collectParentsWithoutAllSubitems(aReader, persistIds, prevIds, IndexItem.PARENT_PERSISTENT_ID, BaseCarveTask.NUM_CARVED_AND_FRAGS);
            
            caseData.putCaseObject(PARENTS_WITH_LOST_SUBITEMS, parentsWithLostSubitems);
            
        }catch(IndexNotFoundException e) {
            commitedPersistentIds = new HashValue[0];
        }
        
    }
    
    private static String getRootPrefix(String path) {
        int fromIndex = path.charAt(0) == '/' || path.charAt(0) == '\\' ? 1 : 0;
        int slashIdx = path.indexOf('/', fromIndex);
        int backSlashIndx = path.indexOf('\\', fromIndex);
        int expanderIdx = path.indexOf(">>", fromIndex);
        if(slashIdx == -1) {
            slashIdx = path.length();
        }
        if(backSlashIndx == -1) {
            backSlashIndx = path.length();
        }
        if(expanderIdx == -1) {
            expanderIdx = path.length();
        }
        int endIndex = Math.min(slashIdx, Math.min(backSlashIndx, expanderIdx));
        return path.substring(0, endIndex);
    }
    
    private void collectParentsWithoutAllSubitems(AtomicReader aReader, SortedDocValues persistIds, NumericDocValues ids, String parentIdField, String subitemCountField) throws IOException {
        SortedDocValues parentContainers = aReader.getSortedDocValues(parentIdField);
        if(parentContainers == null) {
            return;
        }
        NumericDocValues numSubitems = aReader.getNumericDocValues(subitemCountField);
        if(numSubitems == null) {
            return;
        }
        
        int[] referencingSubitems = new int[parentContainers.getValueCount()];
        
        BitSet countedIds = new BitSet();
        for(int doc = 0; doc < aReader.maxDoc(); doc++) {
            int id = (int) ids.get(doc);
            int ord = parentContainers.getOrd(doc);
            if(ord != -1 && !countedIds.get(id)) {
                referencingSubitems[ord]++;
            }
            //splited items occur more than once, so we track seen ids
            countedIds.set(id);
        }
        
        Bits docsWithField = aReader.getDocsWithField(subitemCountField);
        for(int doc = 0; doc < aReader.maxDoc(); doc++) {
            if(docsWithField.get(doc)) {
                int subitemsCount = (int)numSubitems.get(doc);
                BytesRef persistId = persistIds.get(doc);
                int ord = parentContainers.lookupTerm(persistId);
                int carvedIgnored = 0;
                if(subitemCountField == BaseCarveTask.NUM_CARVED_AND_FRAGS) {
                    carvedIgnored = stats.getCarvedIgnoredNum(new HashValue(persistId.utf8ToString()));
                }
                if(ord < 0 || subitemsCount != referencingSubitems[ord] + carvedIgnored) {
                    parentsWithLostSubitems.add(new HashValue(persistId.utf8ToString()));
                    //System.out.println("Parent with lost child " + persistId.utf8ToString());
                }
            }
        }
    }

    @Override
    public void finish() throws Exception {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void process(IItem item) throws Exception {
        
        //must be calculated first, in all cases, to allow recovering in the future
        HashValue persistentId = new HashValue(Util.getPersistentId(item));
        String parentPersistentId = Util.getParentPersistentId(item);
        
        if(!args.isContinue()) {
            return;
        }
        
        //ignore already commited items. If they are containers without all their subitems commited, process again
        if(Arrays.binarySearch(commitedPersistentIds, persistentId) >= 0) {
            if(!parentsWithLostSubitems.remove(persistentId)) {
                item.setToIgnore(true);
                return;
            }
        }
        
        //reset number of carved ignored subitems because parent will be processed again
        stats.resetCarvedIgnored(item);
        
        //changes id to previous processing id
        Long previousId = persistentToIdMap.get(persistentId);
        if(previousId != null) {
            item.setId(previousId.intValue());
        }else {
            String splittedTextId = Util.generatePersistentIdForTextFrag(Util.getPersistentId(item), 1);
            previousId = persistentToIdMap.get(new HashValue(splittedTextId));
            if(previousId != null) {
                item.setId(previousId.intValue());
            }
        }
        
        //changes parentId to previous processing parentId
        if(parentPersistentId != null) {
            Long previousParentId = persistentToIdMap.get(new HashValue(parentPersistentId));
            if(previousParentId != null) {
                Integer parentId = item.getParentId();
                item.setParentId(previousParentId.intValue());
                List<Integer> parentIds = item.getParentIds();
                for(int i = 0; i < parentIds.size(); i++) {
                    if(parentIds.get(i).equals(parentId)) {
                        parentIds.set(i, previousParentId.intValue());
                        break;
                    }
                }
            }
        }
        
        //change evidenceUUID to previous processing evidenceUUID
        String rootPrefix = getRootPrefix(item.getPath());
        String oldUUID = rootNameToEvidenceUUID.get(rootPrefix);
        if(oldUUID != null) {
            item.getDataSource().setUUID(oldUUID);
        }
    }

}
