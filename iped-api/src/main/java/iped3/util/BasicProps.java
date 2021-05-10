package iped3.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class BasicProps {

    public static final String ID = "id"; //$NON-NLS-1$
    public static final String PARENTID = "parentId"; //$NON-NLS-1$
    public static final String PARENTIDs = "parentIds"; //$NON-NLS-1$
    public static final String EVIDENCE_UUID = "evidenceUUID"; //$NON-NLS-1$
    public static final String NAME = "name"; //$NON-NLS-1$
    public static final String TYPE = "type"; //$NON-NLS-1$
    public static final String LENGTH = "size"; //$NON-NLS-1$
    public static final String CREATED = "created"; //$NON-NLS-1$
    public static final String ACCESSED = "accessed"; //$NON-NLS-1$
    public static final String MODIFIED = "modified"; //$NON-NLS-1$
    public static final String RECORDDATE = "recordDate"; //$NON-NLS-1$
    public static final String PATH = "path"; //$NON-NLS-1$
    public static final String CATEGORY = "category"; //$NON-NLS-1$
    public static final String DELETED = "deleted"; //$NON-NLS-1$
    public static final String CONTENT = "content"; //$NON-NLS-1$
    public static final String EXPORT = "export"; //$NON-NLS-1$
    public static final String HASH = "hash"; //$NON-NLS-1$
    public static final String ISDIR = "isDir"; //$NON-NLS-1$
    public static final String ISROOT = "isRoot"; //$NON-NLS-1$
    public static final String HASCHILD = "hasChildren"; //$NON-NLS-1$
    public static final String CARVED = "carved"; //$NON-NLS-1$
    public static final String SUBITEM = "subitem"; //$NON-NLS-1$
    public static final String SUBITEMID = "subitemId"; //$NON-NLS-1$
    public static final String OFFSET = "offset"; //$NON-NLS-1$
    public static final String DUPLICATE = "duplicate"; //$NON-NLS-1$
    public static final String TIMEOUT = "timeout"; //$NON-NLS-1$
    public static final String CONTENTTYPE = "contentType"; //$NON-NLS-1$
    public static final String TREENODE = "treeNode"; //$NON-NLS-1$
    public static final String THUMB = "thumbnail"; //$NON-NLS-1$
    public static final String SIMILARITY_FEATURES = "similarityFeatures"; //$NON-NLS-1$
    public static final String META_ADDRESS = "metaAddress";
    public static final String MFT_SEQUENCE = "MFTSequence";
    public static final String FILESYSTEM_ID = "fileSystemId";
    public static final String TIMESTAMP = "timeStamp";
    public static final String TIME_EVENT = "timeEvent";

    private static Map<String, String> toNonLocalizedMap = getNonLocalizationMap();
    private static Map<String, String> toLocalizedMap = invertMap(toNonLocalizedMap);

    private static final Map<String, String> getNonLocalizationMap() {
        Map<String, String> map = new HashMap<>();
        map.put(Messages.getString("BasicProps.name"), NAME);
        map.put(Messages.getString("BasicProps.type"), TYPE);
        map.put(Messages.getString("BasicProps.size"), LENGTH);
        map.put(Messages.getString("BasicProps.created"), CREATED);
        map.put(Messages.getString("BasicProps.accessed"), ACCESSED);
        map.put(Messages.getString("BasicProps.modified"), MODIFIED);
        map.put(Messages.getString("BasicProps.recordDate"), RECORDDATE);
        map.put(Messages.getString("BasicProps.path"), PATH);
        map.put(Messages.getString("BasicProps.category"), CATEGORY);
        map.put(Messages.getString("BasicProps.deleted"), DELETED);
        map.put(Messages.getString("BasicProps.content"), CONTENT);
        return map;
    }

    private static final Map<String, String> invertMap(Map<String, String> map) {
        Map<String, String> invertedMap = new HashMap<>();
        for (Entry<String, String> entry : map.entrySet()) {
            invertedMap.put(entry.getValue(), entry.getKey());
        }
        return invertedMap;
    }

    public static final String getNonLocalizedField(String localizedField) {
        return toNonLocalizedMap.getOrDefault(localizedField, localizedField);
    }

    public static final String getLocalizedField(String nonLocalizedField) {
        return toLocalizedMap.getOrDefault(nonLocalizedField, nonLocalizedField);
    }

    public static final Set<String> SET = getBasicProps();

    private static Set<String> getBasicProps() {
        HashSet<String> basicProps = new HashSet<>();
        basicProps.add(ID);
        basicProps.add(PARENTID);
        basicProps.add(PARENTIDs);
        basicProps.add(EVIDENCE_UUID);
        basicProps.add(NAME);
        basicProps.add(TYPE);
        basicProps.add(LENGTH);
        basicProps.add(CREATED);
        basicProps.add(ACCESSED);
        basicProps.add(MODIFIED);
        basicProps.add(RECORDDATE);
        basicProps.add(PATH);
        basicProps.add(CATEGORY);
        basicProps.add(DELETED);
        basicProps.add(CONTENT);
        basicProps.add(EXPORT);
        basicProps.add(HASH);
        basicProps.add(ISDIR);
        basicProps.add(ISROOT);
        basicProps.add(HASCHILD);
        basicProps.add(CARVED);
        basicProps.add(SUBITEM);
        basicProps.add(SUBITEMID);
        basicProps.add(OFFSET);
        basicProps.add(DUPLICATE);
        basicProps.add(TIMEOUT);
        basicProps.add(CONTENTTYPE);
        basicProps.add(TREENODE);
        basicProps.add(THUMB);
        basicProps.add(SIMILARITY_FEATURES);
        basicProps.add(TIMESTAMP);
        basicProps.add(TIME_EVENT);
        return basicProps;
    }

}
