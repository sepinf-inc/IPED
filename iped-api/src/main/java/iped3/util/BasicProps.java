package iped3.util;

import java.util.HashSet;
import java.util.Set;

public class BasicProps {

    public static final String ID = "id"; //$NON-NLS-1$
    public static final String PARENTID = "parentId"; //$NON-NLS-1$
    public static final String PARENTIDs = "parentIds"; //$NON-NLS-1$
    public static final String EVIDENCE_UUID = "evidenceUUID"; //$NON-NLS-1$
    public static final String NAME = Messages.getString("BasicProps.name"); //$NON-NLS-1$
    public static final String TYPE = Messages.getString("BasicProps.type"); //$NON-NLS-1$
    public static final String LENGTH = Messages.getString("BasicProps.size"); //$NON-NLS-1$
    public static final String CREATED = Messages.getString("BasicProps.created"); //$NON-NLS-1$
    public static final String ACCESSED = Messages.getString("BasicProps.accessed"); //$NON-NLS-1$
    public static final String MODIFIED = Messages.getString("BasicProps.modified"); //$NON-NLS-1$
    public static final String RECORDDATE = Messages.getString("BasicProps.recordDate"); //$NON-NLS-1$
    public static final String PATH = Messages.getString("BasicProps.path"); //$NON-NLS-1$
    public static final String CATEGORY = Messages.getString("BasicProps.category"); //$NON-NLS-1$
    public static final String DELETED = Messages.getString("BasicProps.deleted"); //$NON-NLS-1$
    public static final String CONTENT = Messages.getString("BasicProps.content"); //$NON-NLS-1$
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
        return basicProps;
    }

}
