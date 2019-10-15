package iped3.util;

import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Property;

/**
 * Metadados extras produzidos pelos parsers do pacote.
 * 
 * @author Nassif
 *
 */
public class ExtraProperties {

    public static final String TIKA_PARSER_USED = "X-Parsed-By"; //$NON-NLS-1$
    
    // public static String EMBEDDED_PATH = "INDEXER_EMBEDDED_PATH";
    public static final String EMBEDDED_FOLDER = "IpedEmbeddeFolder"; //$NON-NLS-1$
    // public static String INDEXER_ID = "INDEXER_ID";\
    public static final Property ACCESSED = Property.internalDate("IpedLastAccessedDate"); //$NON-NLS-1$
    
    public static final Property VISIT_DATE = Property.internalDate("visitDate"); //$NON-NLS-1$
    
    public static final Property DOWNLOAD_DATE = Property.internalDate("downloadDate"); //$NON-NLS-1$

    public static final String DELETED = "IpedDeletedEmbeddedItem"; //$NON-NLS-1$

    public static final String MESSAGE_PREFIX = "Message-"; //$NON-NLS-1$

    public static final String MESSAGE_SUBJECT = MESSAGE_PREFIX + "Subject"; //$NON-NLS-1$

    public static final Property MESSAGE_DATE = Property.internalDate(MESSAGE_PREFIX + "Date"); //$NON-NLS-1$

    public static final String MESSAGE_BODY = MESSAGE_PREFIX + "Body"; //$NON-NLS-1$
    
    public static final String PST_ATTACH = "pst_attachment"; //$NON-NLS-1$
    
    public static final String PST_EMAIL_HAS_ATTACHS = "pst_email_has_attachs"; //$NON-NLS-1$

    public static final String WKFF_HITS = "ledKffHits"; //$NON-NLS-1$

    public static final String P2P_REGISTRY_COUNT = "p2pHistoryEntries"; //$NON-NLS-1$

    public static final String SHARED_HASHES = "sharedHashes"; //$NON-NLS-1$

    public static final String SHARED_ITEMS = "sharedItems"; //$NON-NLS-1$

    public static final String LINKED_ITEMS = "linkedItems"; //$NON-NLS-1$

    public static final String AUDIO_META_PREFIX = "audio:"; //$NON-NLS-1$

    public static final String IMAGE_META_PREFIX = "image:"; //$NON-NLS-1$

    public static final String VIDEO_META_PREFIX = "video:"; //$NON-NLS-1$

    public static final String PDF_META_PREFIX = "pdf:"; //$NON-NLS-1$

    public static final String HTML_META_PREFIX = "html:"; //$NON-NLS-1$

    public static final String OFFICE_META_PREFIX = "office:"; //$NON-NLS-1$

    public static final String UFED_META_PREFIX = "ufed:"; //$NON-NLS-1$

    public static final String ITEM_VIRTUAL_ID = "itemVirtualIdentifier"; //$NON-NLS-1$

    public static final String PARENT_VIRTUAL_ID = "parentVirtualIdentifier"; //$NON-NLS-1$

    public static final String LOCATIONS = "locations"; //$NON-NLS-1$
    
    public static final String URL = "url"; //$NON-NLS-1$
    
    public static final String LOCAL_PATH = "localPath"; //$NON-NLS-1$
    
    public static final String[] EMAIL_PROPS = { MESSAGE_SUBJECT, MESSAGE_DATE.getName(),
            MESSAGE_BODY, Message.MESSAGE_FROM, Message.MESSAGE_TO, Message.MESSAGE_CC,
            Message.MESSAGE_BCC, PST_ATTACH, PST_EMAIL_HAS_ATTACHS};
}
