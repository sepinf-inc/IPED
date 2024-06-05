package iped.properties;

import java.util.Arrays;
import java.util.List;

import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Metadados extras produzidos pelos parsers do pacote.
 *
 * @author Nassif
 *
 */
public class ExtraProperties {

    public static final String GLOBAL_ID = "globalId"; //$NON-NLS-1$

    public static final String TIKA_PARSER_USED = TikaCoreProperties.TIKA_PARSED_BY.getName();

    public static final String DATASOURCE_READER = "X-Reader"; //$NON-NLS-1$

    public static final String EMBEDDED_FOLDER = "IpedEmbeddeFolder"; //$NON-NLS-1$

    public static final Property ACCESSED = Property.internalDate("IpedLastAccessedDate"); //$NON-NLS-1$

    public static final Property VISIT_DATE = Property.internalDate("visitDate"); //$NON-NLS-1$

    public static final Property DOWNLOAD_DATE = Property.internalDate("downloadDate"); //$NON-NLS-1$

    public static final String DOWNLOAD_TOTAL_BYTES = "totalBytes"; //$NON-NLS-1$

    public static final String DOWNLOAD_RECEIVED_BYTES = "receivedBytes"; //$NON-NLS-1$

    public static final String DELETED = "IpedDeletedEmbeddedItem"; //$NON-NLS-1$

    public static final String MESSAGE_PREFIX = "Message-"; //$NON-NLS-1$

    public static final String MESSAGE_SUBJECT = MESSAGE_PREFIX + "Subject"; //$NON-NLS-1$

    public static final String COMMUNICATION_PREFIX = "Communication:";

    public static final String COMMUNICATION_FROM = COMMUNICATION_PREFIX + "From";

    public static final String COMMUNICATION_TO = COMMUNICATION_PREFIX + "To";

    public static final Property COMMUNICATION_DATE = Property.internalDate(COMMUNICATION_PREFIX + "Date"); //$NON-NLS-1$

    public static final Property MESSAGE_DATE = COMMUNICATION_DATE;

    public static final String PARTICIPANTS = COMMUNICATION_PREFIX + "Participants";

    public static final String GROUP_ID = "GroupID";

    public static final String MESSAGE_BODY = MESSAGE_PREFIX + "Body"; //$NON-NLS-1$

    public static final String MESSAGE_IS_ATTACHMENT = Message.MESSAGE_PREFIX + "IsEmailAttachment"; //$NON-NLS-1$

    public static final Property MESSAGE_ATTACHMENT_COUNT = Property.internalInteger(Message.MESSAGE_PREFIX + "AttachmentCount"); //$NON-NLS-1$

    public static final String CSAM_HASH_HITS = "childPornHashHits"; //$NON-NLS-1$

    public static final String P2P_REGISTRY_COUNT = "p2pHistoryEntries"; //$NON-NLS-1$

    public static final String SHARED_HASHES = "sharedHashes"; //$NON-NLS-1$

    public static final String SHARED_ITEMS = "sharedItems"; //$NON-NLS-1$

    public static final String LINKED_ITEMS = "linkedItems"; //$NON-NLS-1$

    public static final String GENERIC_META_PREFIX = "meta:"; //$NON-NLS-1$

    public static final String COMMON_META_PREFIX = "common:"; //$NON-NLS-1$

    public static final String AUDIO_META_PREFIX = "audio:"; //$NON-NLS-1$

    public static final String IMAGE_META_PREFIX = "image:"; //$NON-NLS-1$

    public static final String VIDEO_META_PREFIX = "video:"; //$NON-NLS-1$

    public static final String PDF_META_PREFIX = "pdf:"; //$NON-NLS-1$

    public static final String HTML_META_PREFIX = "html:"; //$NON-NLS-1$

    public static final String OFFICE_META_PREFIX = "office:"; //$NON-NLS-1$

    public static final String UFED_META_PREFIX = "ufed:"; //$NON-NLS-1$

    public static final String P2P_META_PREFIX = "p2p:"; //$NON-NLS-1$

    public static final String ITEM_VIRTUAL_ID = "itemVirtualIdentifier"; //$NON-NLS-1$

    public static final String PARENT_VIRTUAL_ID = "parentVirtualIdentifier"; //$NON-NLS-1$

    public static final String LOCATIONS = COMMON_META_PREFIX + "geo:locations"; //$NON-NLS-1$

    public static final String URL = "url"; //$NON-NLS-1$

    public static final String LOCAL_PATH = "localPath"; //$NON-NLS-1$

    public static final String USER_PHONE = "phoneNumber"; //$NON-NLS-1$

    public static final String USER_NAME = "userName"; //$NON-NLS-1$

    public static final String USER_ACCOUNT = "userAccount"; //$NON-NLS-1$

    public static final String CONTACT_OF_ACCOUNT = "contactOfAccount"; //$NON-NLS-1$

    public static final String USER_ACCOUNT_TYPE = "accountType"; //$NON-NLS-1$

    public static final String USER_EMAIL = "emailAddress"; //$NON-NLS-1$

    public static final String USER_ADDRESS = "userAddress"; //$NON-NLS-1$

    public static final String USER_ORGANIZATION = "userOrganization"; //$NON-NLS-1$

    public static final String USER_NOTES = "userNotes"; //$NON-NLS-1$

    public static final String USER_URLS = "userUrls"; //$NON-NLS-1$

    public static final Property USER_BIRTH = Property.internalDate("userBirthday"); //$NON-NLS-1$

    public static final String THUMBNAIL_BASE64 = "thumbnailBase64"; //$NON-NLS-1$

    public static final String PARENT_VIEW_POSITION = "parentViewPosition"; //$NON-NLS-1$

    public static final String CARVEDBY_METADATA_NAME = "CarvedBy"; //$NON-NLS-1$

    public static final Property CARVEDOFFSET_METADATA_NAME = Property.internalInteger("CarvedOffset"); //$NON-NLS-1$

    public static final String TRANSCRIPT_ATTR = AUDIO_META_PREFIX + "transcription";

    public static final String CONFIDENCE_ATTR = AUDIO_META_PREFIX + "transcriptConfidence";

    public static final String TIME_EVENT_ORDS = "timeEventOrds";

    public static final String TIME_EVENT_GROUPS = "timeEventGroups";

    public static final String DECODED_DATA = "isDecodedData";

    public static final String DOWNLOADED_DATA = "downloadedData";

    public static final String EXTRACTED_FILE = "extractedFile";

    public static final String FACE_COUNT = "face_count";

    public static final String FACE_LOCATIONS = "face_locations";

    public static final String FACE_ENCODINGS = "face_encodings";

    public static final List<String> COMMUNICATION_BASIC_PROPS = Arrays.asList(MESSAGE_SUBJECT, MESSAGE_BODY,
            Message.MESSAGE_CC, Message.MESSAGE_BCC, Message.MESSAGE_RECIPIENT_ADDRESS, MESSAGE_IS_ATTACHMENT,
            MESSAGE_ATTACHMENT_COUNT.getName());
}
