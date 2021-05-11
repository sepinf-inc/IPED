package dpf.sp.gpinf.indexer.parsers.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.OCRParser;
import dpf.sp.gpinf.indexer.parsers.RawStringParser;
import dpf.sp.gpinf.indexer.parsers.ufed.UFEDChatParser;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

public class MetadataUtil {

    private static Set<String> generalKeys = getGeneralKeys();

    private static Map<String, Property> compositeProps = getCompositeProps();

    private static Set<String> keysToIgnore = getIgnoreKeys();

    private static MediaTypeRegistry registry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();

    private static Map<String, String> metaCaseMap = new HashMap<String, String>();

    public static Set<String> ignorePreviewMetas = getIgnorePreviewMetas();

    private static final Map<String, String> renameMap = getRenameMap();

    private static Map<String, String> getRenameMap() {
        Map<String, String> rename = new HashMap<String, String>();
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.EQUIPMENT_MAKE.getName(), ExtraProperties.IMAGE_META_PREFIX + "Make");
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.EQUIPMENT_MODEL.getName(), ExtraProperties.IMAGE_META_PREFIX + "Model");
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.IMAGE_WIDTH.getName(), ExtraProperties.IMAGE_META_PREFIX + "Width");
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.IMAGE_LENGTH.getName(), ExtraProperties.IMAGE_META_PREFIX + "Height");
        rename.put(ExtraProperties.VIDEO_META_PREFIX + TIFF.IMAGE_WIDTH.getName(), ExtraProperties.VIDEO_META_PREFIX + "Width");
        rename.put(ExtraProperties.VIDEO_META_PREFIX + TIFF.IMAGE_LENGTH.getName(), ExtraProperties.VIDEO_META_PREFIX + "Height");
        return rename;
    }

    private static Set<String> getIgnorePreviewMetas() {
        ignorePreviewMetas = new HashSet<>();
        ignorePreviewMetas.add(Metadata.RESOURCE_NAME_KEY);
        ignorePreviewMetas.add(Metadata.CONTENT_LENGTH);
        ignorePreviewMetas.add(Metadata.CONTENT_TYPE);
        ignorePreviewMetas.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
        ignorePreviewMetas.add(ExtraProperties.TIKA_PARSER_USED);
        ignorePreviewMetas.add(UFEDChatParser.CHILD_MSG_IDS);
        return ignorePreviewMetas;
    }

    private static Set<String> getGeneralKeys() {
        Set<String> generalKeys = new HashSet<String>();

        generalKeys.add(Metadata.CONTENT_TYPE);
        generalKeys.add(Metadata.RESOURCE_NAME_KEY);
        generalKeys.add(Metadata.CONTENT_LENGTH);
        generalKeys.add(Metadata.EMBEDDED_RELATIONSHIP_ID);
        generalKeys.add(TikaCoreProperties.TIKA_META_PREFIX);
        generalKeys.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME.getName());
        generalKeys.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING.getName());
        generalKeys.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
        generalKeys.add(IndexerDefaultParser.ENCRYPTED_DOCUMENT);
        generalKeys.add(IndexerDefaultParser.PARSER_EXCEPTION);
        generalKeys.add(IndexerDefaultParser.INDEXER_TIMEOUT);
        generalKeys.add(ExtraProperties.DELETED);
        generalKeys.add(ExtraProperties.EMBEDDED_FOLDER);
        generalKeys.add(ExtraProperties.ACCESSED.getName());
        generalKeys.add(ExtraProperties.P2P_REGISTRY_COUNT);
        generalKeys.add(ExtraProperties.SHARED_HASHES);
        generalKeys.add(ExtraProperties.SHARED_ITEMS);
        generalKeys.add(ExtraProperties.LINKED_ITEMS);
        generalKeys.add(ExtraProperties.MESSAGE_SUBJECT);
        generalKeys.add(ExtraProperties.CSAM_HASH_HITS);
        generalKeys.add(ExtraProperties.PST_ATTACH);
        generalKeys.add(ExtraProperties.PST_EMAIL_HAS_ATTACHS);
        generalKeys.add(ExtraProperties.ITEM_VIRTUAL_ID);
        generalKeys.add(ExtraProperties.PARENT_VIRTUAL_ID);
        generalKeys.add(ExtraProperties.LOCATIONS);
        generalKeys.add(ExtraProperties.URL);
        generalKeys.add(ExtraProperties.LOCAL_PATH);
        generalKeys.add(ExtraProperties.DOWNLOAD_DATE.getName());
        generalKeys.add(ExtraProperties.DOWNLOAD_TOTAL_BYTES);
        generalKeys.add(ExtraProperties.DOWNLOAD_RECEIVED_BYTES);
        generalKeys.add(ExtraProperties.TIKA_PARSER_USED);
        generalKeys.add(ExtraProperties.CARVEDBY_METADATA_NAME);
        generalKeys.add(ExtraProperties.CARVEDOFFSET_METADATA_NAME.getName());
        generalKeys.add(ExtraProperties.CONTACT_OF_ACCOUNT);
        generalKeys.add(ExtraProperties.USER_ACCOUNT);
        generalKeys.add(ExtraProperties.USER_ACCOUNT_TYPE);
        generalKeys.add(ExtraProperties.USER_EMAIL);
        generalKeys.add(ExtraProperties.USER_NAME);
        generalKeys.add(ExtraProperties.USER_BIRTH.getName());
        generalKeys.add(ExtraProperties.USER_PHONE);
        generalKeys.add(ExtraProperties.USER_ADDRESS);
        generalKeys.add(ExtraProperties.USER_ORGANIZATION);
        generalKeys.add(ExtraProperties.USER_URLS);
        generalKeys.add(ExtraProperties.USER_NOTES);
        generalKeys.add(ExtraProperties.USER_THUMB);
        generalKeys.add(OCRParser.OCR_CHAR_COUNT);
        generalKeys.add(RawStringParser.COMPRESS_RATIO);

        return generalKeys;
    }

    private static Map<String, Property> getCompositeProps() {
        ArrayList<Property> props = new ArrayList<Property>();

        props.add(TikaCoreProperties.CREATOR);
        props.add(TikaCoreProperties.CREATED);
        props.add(TikaCoreProperties.MODIFIED);
        props.add(TikaCoreProperties.COMMENTS);
        props.add(TikaCoreProperties.KEYWORDS);
        props.add(TikaCoreProperties.FORMAT);
        props.add(TikaCoreProperties.IDENTIFIER);
        props.add(TikaCoreProperties.CONTRIBUTOR);
        props.add(TikaCoreProperties.COVERAGE);
        props.add(TikaCoreProperties.MODIFIER);
        props.add(TikaCoreProperties.LANGUAGE);
        props.add(TikaCoreProperties.PUBLISHER);
        props.add(TikaCoreProperties.RELATION);
        props.add(TikaCoreProperties.RIGHTS);
        props.add(TikaCoreProperties.SOURCE);
        props.add(TikaCoreProperties.TYPE);
        props.add(TikaCoreProperties.TITLE);
        props.add(TikaCoreProperties.DESCRIPTION);
        props.add(TikaCoreProperties.PRINT_DATE);
        props.add(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        props.add(IPTC.COPYRIGHT_OWNER_ID);
        props.add(IPTC.IMAGE_CREATOR_ID);
        props.add(IPTC.IMAGE_SUPPLIER_ID);
        props.add(IPTC.LICENSOR_ID);

        HashMap<String, Property> map = new HashMap<String, Property>();
        for (Property prop : props)
            map.put(prop.getName(), prop);
        return map;
    }

    private static Set<String> getIgnoreKeys() {
        Set<String> ignoredMetadata = new HashSet<String>();
        ignoredMetadata.add("File Name"); //$NON-NLS-1$
        ignoredMetadata.add("File Modified Date"); //$NON-NLS-1$
        ignoredMetadata.add("File Size"); //$NON-NLS-1$
        ignoredMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID);
        ignoredMetadata.add(ExtraProperties.PARENT_VIRTUAL_ID);
        ignoredMetadata.add(BasicProps.HASCHILD);
        return ignoredMetadata;
    }

    private static final void removeIgnorable(Metadata metadata) {
        for (String key : keysToIgnore)
            metadata.remove(key);
    }

    public static final void normalizeMetadata(Metadata metadata) {
        // remove possible null key (#329)
        metadata.remove(null);
        normalizeMSGMetadata(metadata);
        removeDuplicateKeys(metadata);
        removeIgnorable(metadata);
        removeInvalidGPSMeta(metadata);
        normalizeCase(metadata);
        prefixAudioMetadata(metadata);
        prefixImageMetadata(metadata);
        prefixVideoMetadata(metadata);
        prefixPDFMetadata(metadata);
        prefixDocMetadata(metadata);
        prefixBasicMetadata(metadata);
        removeDuplicateValues(metadata);
        renameKeys(metadata);
    }

    private static void removeDuplicateKeys(Metadata metadata) {
        for (String key : metadata.names()) {
            Property prop = compositeProps.get(key);
            if (prop != null && prop.getSecondaryExtractProperties() != null) {
                for (Property p : prop.getSecondaryExtractProperties())
                    metadata.remove(p.getName());
            }
        }
    }

    private static void removeInvalidGPSMeta(Metadata metadata) {
        String lat = metadata.get(Metadata.LATITUDE);
        String lon = metadata.get(Metadata.LONGITUDE);
        boolean remove = false;
        try {
            if (lat != null && Float.valueOf(lat) == 0.0 && lon != null && Float.valueOf(lon) == 0.0) {
                remove = true;
            }
        } catch (NumberFormatException e) {
            remove = true;
        }
        if (remove) {
            metadata.remove(Metadata.LATITUDE.getName());
            metadata.remove(Metadata.LONGITUDE.getName());
            metadata.remove(Metadata.ALTITUDE.getName());
        }
    }

    private static void removeDuplicateValues(Metadata metadata) {
        for (String key : metadata.names()) {
            String[] values = metadata.getValues(key);
            Arrays.sort(values);
            ArrayList<String> unique = new ArrayList<>();
            String prev = null;
            for (String val : values) {
                if (!val.isEmpty() && !val.equals(prev))
                    unique.add(val);
                prev = val;
            }
            if (unique.size() != values.length) {
                metadata.remove(key);
                for (String val : unique)
                    metadata.add(key, val);
            }
        }
    }

    private static void normalizeCase(Metadata metadata) {
        for (String key : metadata.names()) {
            String lower = key.toLowerCase();
            String first = null;
            synchronized (metaCaseMap) {
                first = metaCaseMap.get(lower);
                if (first == null) {
                    metaCaseMap.put(lower, key);
                    continue;
                }
            }
            if (first.equals(key))
                continue;
            String[] values = metadata.getValues(key);
            metadata.remove(key);
            for (String val : values)
                metadata.add(first, val);
        }
    }

    private static void normalizeKeys2(Metadata metadata) {
        HashSet<String> newkeys = new HashSet<String>();
        for (String key : metadata.names()) {
            if (generalKeys.contains(key))
                continue;
            String newKey = key;
            int i = key.lastIndexOf(':');
            if (i != -1)
                newKey = key.substring(i + 1);
            newKey = newKey.toLowerCase();
            if (!newkeys.contains(newKey)) {
                newkeys.add(newKey);
                if (!newKey.equals(key)) {
                    String[] values = metadata.getValues(key);
                    metadata.remove(key);
                    for (String val : values)
                        metadata.add(newKey, val);
                }
            } else {
                String[] values1 = metadata.getValues(newKey);
                String[] values2 = metadata.getValues(key);
                if (values1.length != values2.length)
                    continue;
                boolean equal = true;
                for (int k = 0; k < values1.length; k++)
                    if (!values1[k].equals(values2[k])) {
                        equal = false;
                        break;
                    }
                if (equal)
                    metadata.remove(key);
            }
        }
    }

    private static void normalizeMSGMetadata(Metadata metadata) {
        if (!metadata.get(Metadata.CONTENT_TYPE).equals("application/vnd.ms-outlook")) //$NON-NLS-1$
            return;

        String subject = metadata.get(TikaCoreProperties.TITLE);
        if (subject == null || subject.isEmpty())
            subject = Messages.getString("MetadataUtil.NoSubject"); //$NON-NLS-1$
        metadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);

        if (metadata.get(TikaCoreProperties.CREATED) != null)
            metadata.set(ExtraProperties.MESSAGE_DATE, metadata.get(TikaCoreProperties.CREATED));

        String value = metadata.get(Message.MESSAGE_FROM);
        if (value == null)
            value = ""; //$NON-NLS-1$
        if (metadata.get(Message.MESSAGE_FROM_NAME) != null
                && !value.toLowerCase().contains(metadata.get(Message.MESSAGE_FROM_NAME).toLowerCase()))
            value += " " + metadata.get(Message.MESSAGE_FROM_NAME); //$NON-NLS-1$
        if (metadata.get(Message.MESSAGE_FROM_EMAIL) != null
                && !value.toLowerCase().contains(metadata.get(Message.MESSAGE_FROM_EMAIL).toLowerCase()))
            value += " \"" + metadata.get(Message.MESSAGE_FROM_EMAIL) + "\""; //$NON-NLS-1$ //$NON-NLS-2$
        metadata.set(Message.MESSAGE_FROM, value);
        // TODO remove metadata until that is consistent across email parsers
        metadata.remove(Message.MESSAGE_FROM_NAME.getName());
        metadata.remove(Message.MESSAGE_FROM_EMAIL.getName());

        normalizeRecipients(metadata, Message.MESSAGE_TO, Message.MESSAGE_TO_NAME, Message.MESSAGE_TO_DISPLAY_NAME,
                Message.MESSAGE_TO_EMAIL);
        normalizeRecipients(metadata, Message.MESSAGE_CC, Message.MESSAGE_CC_NAME, Message.MESSAGE_CC_DISPLAY_NAME,
                Message.MESSAGE_CC_EMAIL);
        normalizeRecipients(metadata, Message.MESSAGE_BCC, Message.MESSAGE_BCC_NAME, Message.MESSAGE_BCC_DISPLAY_NAME,
                Message.MESSAGE_BCC_EMAIL);

    }

    private static void normalizeRecipients(Metadata metadata, String destMeta, Property recipMetaName,
            Property recipMetaDisplayName, Property recipMetaEmail) {
        String[] recipientNames = metadata.getValues(recipMetaName);
        String[] recipientsDisplay = metadata.getValues(recipMetaDisplayName);
        String[] recipientsEmails = metadata.getValues(recipMetaEmail);
        metadata.remove(destMeta);
        // TODO remove metadata until that is consistent across email parsers
        metadata.remove(recipMetaName.getName());
        metadata.remove(recipMetaDisplayName.getName());
        metadata.remove(recipMetaEmail.getName());
        for (int i = 0; i < recipientNames.length; i++) {
            String value = recipientNames[i];
            if (value == null)
                value = ""; //$NON-NLS-1$
            if (!value.toLowerCase().contains(recipientsDisplay[i].toLowerCase()))
                value += " " + recipientsDisplay[i]; //$NON-NLS-1$
            if (!value.toLowerCase().contains(recipientsEmails[i].toLowerCase()))
                value += " \"" + recipientsEmails[i] + "\""; //$NON-NLS-1$ //$NON-NLS-2$
            metadata.add(destMeta, value);
        }
    }

    private static void prefixBasicMetadata(Metadata metadata) {
        for (String key : metadata.names()) {
            if (BasicProps.SET.contains(key)) {
                String[] values = metadata.getValues(key);
                metadata.remove(key);
                for (String val : values) {
                    metadata.add(ExtraProperties.GENERIC_META_PREFIX + key, val);
                }
            }
        }
    }

    private static void includePrefix(Metadata metadata, String prefix) {
        String[] keys = metadata.names();
        for (String key : keys) {
            if (generalKeys.contains(key) || key.toLowerCase().startsWith(prefix.toLowerCase())
                    || key.startsWith(ExtraProperties.UFED_META_PREFIX))
                continue;
            String[] values = metadata.getValues(key);
            metadata.remove(key);
            for (String val : values)
                metadata.add(prefix + key, val);
        }
    }

    private static void prefixAudioMetadata(Metadata metadata) {
        if (metadata.get(Metadata.CONTENT_TYPE).startsWith("audio")) //$NON-NLS-1$
            includePrefix(metadata, ExtraProperties.AUDIO_META_PREFIX);
    }

    private static void prefixImageMetadata(Metadata metadata) {
        if (metadata.get(Metadata.CONTENT_TYPE).startsWith("image")) //$NON-NLS-1$
            includePrefix(metadata, ExtraProperties.IMAGE_META_PREFIX);
    }

    public static boolean isVideoType(MediaType mediaType) {
        return mediaType.getType().equals("video") //$NON-NLS-1$
                || mediaType.getBaseType().toString().equals("application/vnd.rn-realmedia"); //$NON-NLS-1$
    }

    private static void prefixVideoMetadata(Metadata metadata) {
        if (isVideoType(MediaType.parse(metadata.get(Metadata.CONTENT_TYPE)))
                || isVideoType(MediaType.parse(metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE))))
            includePrefix(metadata, ExtraProperties.VIDEO_META_PREFIX);
    }

    private static void prefixPDFMetadata(Metadata metadata) {
        if (metadata.get(Metadata.CONTENT_TYPE).equals("application/pdf")) //$NON-NLS-1$
            includePrefix(metadata, ExtraProperties.PDF_META_PREFIX);
    }

    private static void prefixDocMetadata(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        MediaType mediaType = MediaType.parse(contentType);

        if (contentType.startsWith("message") || //$NON-NLS-1$
                contentType.equals("application/vnd.ms-outlook")) //$NON-NLS-1$
            return;

        while (mediaType != null) {
            if (isHtmlMediaType(mediaType)) {
                cleanHtmlMeta(metadata);
                includePrefix(metadata, ExtraProperties.HTML_META_PREFIX);
                break;
            }
            if (mediaType.toString().equals("application/x-tika-msoffice") || //$NON-NLS-1$
                    mediaType.toString().equals("application/x-tika-ooxml") || //$NON-NLS-1$
                    mediaType.toString().equals("application/rtf") //$NON-NLS-1$
            ) {
                includePrefix(metadata, ExtraProperties.OFFICE_META_PREFIX);
                break;
            }
            mediaType = registry.getSupertype(mediaType);
        }
    }

    private static void cleanHtmlMeta(Metadata metadata) {
        String[] keys = metadata.names();
        for (String key : keys) {
            String newKey = null;
            if (key.startsWith("3D")) //$NON-NLS-1$
                newKey = key.substring(2);
            if (key.startsWith("\"") && key.endsWith("\"") && key.length() > 2) //$NON-NLS-1$ //$NON-NLS-2$
                newKey = key.substring(1, key.length() - 1);
            if (newKey != null && !newKey.isEmpty()) {
                String[] values = metadata.getValues(key);
                metadata.remove(key);
                for (String val : values)
                    metadata.add(newKey, val);
            }
        }
    }

    public static final boolean isHtmlMediaType(MediaType mediaType) {
        return mediaType != null && (mediaType.getBaseType().equals(MediaType.TEXT_HTML)
                || mediaType.getBaseType().equals(MediaType.application("xhtml+xml")) || //$NON-NLS-1$
                mediaType.getBaseType().equals(MediaType.application("xml"))); //$NON-NLS-1$
    }

    public static final boolean isHtmlSubType(MediaType mediaType) {
        do {
            boolean hasParam = mediaType != null && mediaType.hasParameters();
            mediaType = registry.getSupertype(mediaType);
            if (!hasParam && isHtmlMediaType(mediaType))
                return true;

        } while (mediaType != null);

        return false;
    }

    public static final Metadata clone(Metadata metadata) {
        Metadata clone = new Metadata();
        String[] keys = metadata.names();
        for (String key : keys) {
            String[] values = metadata.getValues(key);
            for (String val : values) {
                clone.add(key, val);
            }
        }
        return clone;
    }
    
    private static void renameKeys(Metadata metadata) {
        for (String oldName : renameMap.keySet()) {
            String[] values = metadata.getValues(oldName);
            if (values != null) {
                metadata.remove(oldName);
                String newName = renameMap.get(oldName);
                for (String val : values) {
                    metadata.add(newName, val);
                }
            }
        }
    }

}
