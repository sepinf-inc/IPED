package iped.parsers.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.apple.BPListDetector;
import org.apache.tika.metadata.IPTC;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

import iped.data.IItem;
import iped.parsers.image.TiffPageParser;
import iped.parsers.ocr.OCRParser;
import iped.parsers.standard.RawStringParser;
import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.properties.MediaTypes;
import iped.utils.StringUtil;

public class MetadataUtil {

    private static Map<String, Class<?>> typesMap = Collections.synchronizedMap(new TreeMap<String, Class<?>>(StringUtil.getIgnoreCaseComparator()));

    private static Set<String> generalKeys = getGeneralKeys();

    private static Set<String> commonKeys = getCommonKeys();

    private static Map<String, Property> compositeProps = getCompositeProps();

    private static Set<String> keysToIgnore = getIgnoreKeys();

    public static Set<String> ignorePreviewMetas = getIgnorePreviewMetas();

    private static final Map<String, String> renameMap = getRenameMap();

    private static final Map<String, String> renameOrRemoveMap = getRenameOrRemoveMap();
    
    private static final Set<String> singleValueKeys = getSingleValKeys();

    private static Map<String, String> metaCaseMap = getMetaCaseMap();

    private static final Set<String> BASIC_MAIL_HEADERS = getBasicHeaders();

    private static final Set<String> RAW_MAIL_HEADERS = getRawMailHeaders();

    private static Pattern emailPattern = Pattern.compile("[0-9a-zA-Z\\+\\.\\_\\%\\-\\#\\!]+\\@[0-9a-zA-Z\\-\\.]+");

    private static final Set<String> customMetadataPrefixes = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static Map<String, Class<?>> getMetadataTypes() {
        return Collections.unmodifiableMap(typesMap);
    }

    public static void setMetadataType(String metadataName, Class<?> metadataType) {
        typesMap.put(metadataName, metadataType);
    }

    /**
     * Method to add a new metadata prefix. Can be called from parsers to install a
     * new meta prefix.
     * 
     * @param metaPrefix
     */
    public static void addCustomMetadataPrefix(String metaPrefix) {
        customMetadataPrefixes.add(metaPrefix);
    }

    private static final Set<String> getBasicHeaders() {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Set<String> set = new TreeSet<>(collator);
        set.addAll(Arrays.asList("From", "Subject", "To", "Bcc", "Cc", "Date"));
        return Collections.unmodifiableSet(set);
    }

    private static Set<String> getRawMailHeaders() {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Set<String> headers = new TreeSet<>(collator);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                MetadataUtil.class.getResourceAsStream("/AllowedRawMailHeaders.txt"), StandardCharsets.UTF_8))) {
            headers.addAll(reader.lines().collect(Collectors.toList()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Collections.unmodifiableSet(headers);
    }

    public static boolean isToAddRawMailHeader(String header) {
        return !BASIC_MAIL_HEADERS.contains(header) && (RAW_MAIL_HEADERS.contains(header)
                || (header.length() > 3 && header.toUpperCase().startsWith("X-")));
    }

    public static void fillRecipientAddress(Metadata metadata, String recipient) {
        if (recipient != null) {
            Matcher matcher = emailPattern.matcher(recipient);
            while (matcher.find()) {
                metadata.add(Message.MESSAGE_RECIPIENT_ADDRESS, matcher.group());
            }
        }
    }

    private static Map<String, String> getMetaCaseMap() {
        Map<String, String> metaCaseMap = new HashMap<String, String>();
        for(String key : renameOrRemoveMap.values()) {
            key = removePrefix(key);
            metaCaseMap.put(key.toLowerCase(), key);
        }
        for(String key : renameMap.values()) {
            key = removePrefix(key);
            metaCaseMap.put(key.toLowerCase(), key);
        }
        return metaCaseMap; 
    }
    
    private static String removePrefix(String key) {
        // UFED prefix doesn't need to be removed because it is not added by this class
        if (key.startsWith(ExtraProperties.IMAGE_META_PREFIX))
            return key.substring(ExtraProperties.IMAGE_META_PREFIX.length());
        if (key.startsWith(ExtraProperties.VIDEO_META_PREFIX))
            return key.substring(ExtraProperties.VIDEO_META_PREFIX.length());
        if (key.startsWith(ExtraProperties.AUDIO_META_PREFIX))
            return key.substring(ExtraProperties.AUDIO_META_PREFIX.length());
        if (key.startsWith(ExtraProperties.PDF_META_PREFIX))
            return key.substring(ExtraProperties.PDF_META_PREFIX.length());
        if (key.startsWith(ExtraProperties.HTML_META_PREFIX))
            return key.substring(ExtraProperties.HTML_META_PREFIX.length());
        if (key.startsWith(ExtraProperties.OFFICE_META_PREFIX))
            return key.substring(ExtraProperties.OFFICE_META_PREFIX.length());
        if (key.startsWith(ExtraProperties.GENERIC_META_PREFIX))
            return key.substring(ExtraProperties.GENERIC_META_PREFIX.length());
        if (key.startsWith(ExtraProperties.COMMON_META_PREFIX))
            return key.substring(ExtraProperties.COMMON_META_PREFIX.length());
        return key;
    }
    
    private static Set<String> getSingleValKeys() {
        Set<String> singleValueKeys = new HashSet<>();
        singleValueKeys.add(ExtraProperties.IMAGE_META_PREFIX + "Make");
        singleValueKeys.add(ExtraProperties.IMAGE_META_PREFIX + "Model");
        singleValueKeys.add(ExtraProperties.IMAGE_META_PREFIX + "Width");
        singleValueKeys.add(ExtraProperties.IMAGE_META_PREFIX + "Height");
        singleValueKeys.add(ExtraProperties.VIDEO_META_PREFIX + "Width");
        singleValueKeys.add(ExtraProperties.VIDEO_META_PREFIX + "Height");
        return singleValueKeys;
    }

    // if this map is updated, new prefixes should be put in removePrefix(key) see #522
    private static Map<String, String> getRenameMap() {
        Map<String, String> rename = new HashMap<String, String>();
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.EQUIPMENT_MAKE.getName(), ExtraProperties.IMAGE_META_PREFIX + "Make");
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.EQUIPMENT_MODEL.getName(), ExtraProperties.IMAGE_META_PREFIX + "Model");
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.IMAGE_WIDTH.getName(), ExtraProperties.IMAGE_META_PREFIX + "Width");
        rename.put(ExtraProperties.IMAGE_META_PREFIX + TIFF.IMAGE_LENGTH.getName(), ExtraProperties.IMAGE_META_PREFIX + "Height");
        rename.put(ExtraProperties.VIDEO_META_PREFIX + TIFF.IMAGE_WIDTH.getName(), ExtraProperties.VIDEO_META_PREFIX + "Width");
        rename.put(ExtraProperties.VIDEO_META_PREFIX + TIFF.IMAGE_LENGTH.getName(), ExtraProperties.VIDEO_META_PREFIX + "Height");
        rename.put(ExtraProperties.UFED_META_PREFIX + "Altitude", ExtraProperties.COMMON_META_PREFIX + TikaCoreProperties.ALTITUDE.getName());
        rename.put(Message.MESSAGE_FROM, ExtraProperties.COMMUNICATION_FROM);
        rename.put(Message.MESSAGE_TO, ExtraProperties.COMMUNICATION_TO);
        return rename;
    }

    private static Map<String, String> getRenameOrRemoveMap() {
        // Properties here are renamed if is no value already associated with the new name, otherwise they are simply removed. 
        Map<String, String> renameOrRemove = new HashMap<String, String>();
        renameOrRemove.put(ExtraProperties.IMAGE_META_PREFIX + "Image Width", ExtraProperties.IMAGE_META_PREFIX + "Width");
        renameOrRemove.put(ExtraProperties.IMAGE_META_PREFIX + "Image Height", ExtraProperties.IMAGE_META_PREFIX + "Height");
        return renameOrRemove;
    }
    
    private static Set<String> getIgnorePreviewMetas() {
        ignorePreviewMetas = new HashSet<>();
        ignorePreviewMetas.add(TikaCoreProperties.RESOURCE_NAME_KEY);
        ignorePreviewMetas.add(Metadata.CONTENT_LENGTH);
        ignorePreviewMetas.add(Metadata.CONTENT_TYPE);
        ignorePreviewMetas.add(StandardParser.INDEXER_CONTENT_TYPE);
        ignorePreviewMetas.add(ExtraProperties.TIKA_PARSER_USED);
        return ignorePreviewMetas;
    }

    private static Set<String> getGeneralKeys() {
        Set<String> generalKeys = new HashSet<String>();

        generalKeys.add(Metadata.CONTENT_TYPE);
        generalKeys.add(TikaCoreProperties.RESOURCE_NAME_KEY);
        generalKeys.add(Metadata.CONTENT_LENGTH);
        generalKeys.add(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
        generalKeys.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME.getName());
        generalKeys.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING.getName());
        generalKeys.add(TikaCoreProperties.TIKA_META_EXCEPTION_EMBEDDED_STREAM.getName());
        generalKeys.add(StandardParser.INDEXER_CONTENT_TYPE);
        generalKeys.add(StandardParser.ENCRYPTED_DOCUMENT);
        generalKeys.add(StandardParser.PARSER_EXCEPTION);
        generalKeys.add(StandardParser.INDEXER_TIMEOUT);
        generalKeys.add(ExtraProperties.DELETED);
        generalKeys.add(ExtraProperties.EMBEDDED_FOLDER);
        generalKeys.add(ExtraProperties.ACCESSED.getName());
        generalKeys.add(ExtraProperties.P2P_REGISTRY_COUNT);
        generalKeys.add(ExtraProperties.SHARED_HASHES);
        generalKeys.add(ExtraProperties.SHARED_ITEMS);
        generalKeys.add(ExtraProperties.LINKED_ITEMS);
        generalKeys.add(ExtraProperties.CSAM_HASH_HITS);
        generalKeys.add(ExtraProperties.MESSAGE_SUBJECT);
        generalKeys.add(ExtraProperties.MESSAGE_IS_ATTACHMENT);
        generalKeys.add(ExtraProperties.MESSAGE_ATTACHMENT_COUNT.getName());
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
        generalKeys.add(ExtraProperties.THUMBNAIL_BASE64);
        generalKeys.add(ExtraProperties.DOWNLOADED_DATA);
        generalKeys.add(ExtraProperties.TRANSCRIPT_ATTR);
        generalKeys.add(ExtraProperties.CONFIDENCE_ATTR);
        generalKeys.add(OCRParser.OCR_CHAR_COUNT);
        generalKeys.add(RawStringParser.COMPRESS_RATIO);
        generalKeys.add(ExtraProperties.PARENT_VIEW_POSITION);

        return generalKeys;
    }

    private static Map<String, Property> getCompositeProps() {
        ArrayList<Property> props = new ArrayList<Property>();

        props.add(TikaCoreProperties.CREATOR);
        props.add(TikaCoreProperties.CREATED);
        props.add(TikaCoreProperties.MODIFIED);
        props.add(TikaCoreProperties.COMMENTS);
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
        props.add(Office.KEYWORDS);
        props.add(IPTC.COPYRIGHT_OWNER_ID);
        props.add(IPTC.IMAGE_CREATOR_ID);
        props.add(IPTC.IMAGE_SUPPLIER_ID);
        props.add(IPTC.LICENSOR_ID);

        HashMap<String, Property> map = new HashMap<String, Property>();
        for (Property prop : props)
            map.put(prop.getName(), prop);
        return map;
    }

    private static Set<String> getCommonKeys() {
        Set<String> props = new HashSet<String>();
        /*
         * Commented properties below are set only by 1 parser or rarely set by other
         * parsers using Tika-1.27/iped-4.0.0, this can change in future releases.
         */
        props.add(TikaCoreProperties.CREATOR.getName());
        props.add(TikaCoreProperties.CREATED.getName());
        props.add(TikaCoreProperties.MODIFIED.getName());
        props.add(TikaCoreProperties.COMMENTS.getName());
        props.add(Office.KEYWORDS.getName());

        // set by PDF and rarely set by OpenOffice/DcXML parsers today
        // props.add(TikaCoreProperties.FORMAT.getName());

        props.add(TikaCoreProperties.MODIFIER.getName());
        props.add(TikaCoreProperties.LANGUAGE.getName());

        // set by Office and rarely set by DcXML/Iptc parsers today
        // props.add(TikaCoreProperties.PUBLISHER.getName());

        // below properties are rarely set by parsers
        // props.add(TikaCoreProperties.IDENTIFIER.getName());
        // props.add(TikaCoreProperties.CONTRIBUTOR.getName());
        // props.add(TikaCoreProperties.COVERAGE.getName());
        // props.add(TikaCoreProperties.RELATION.getName());
        // props.add(TikaCoreProperties.RIGHTS.getName());
        // props.add(TikaCoreProperties.SOURCE.getName());
        // props.add(TikaCoreProperties.TYPE.getName());

        props.add(TikaCoreProperties.TITLE.getName());
        props.add(TikaCoreProperties.DESCRIPTION.getName());

        // set only by Office parsers today
        // props.add(TikaCoreProperties.PRINT_DATE.getName());

        props.add(TikaCoreProperties.CREATOR_TOOL.getName());

        // set only by PDFParser today
        // props.add(TikaCoreProperties.METADATA_DATE.getName());

        props.add(ExtraProperties.LOCATIONS);
        props.add(TikaCoreProperties.ALTITUDE.getName());

        // set only by PDFParser today
        // props.add(TikaCoreProperties.RATING.getName());

        // set by PDFParser and rarely set by OpenOffice parser today
        // props.add(TikaCoreProperties.HAS_SIGNATURE.getName());

        return props;
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
        // removes ignorable keys
        for (String key : keysToIgnore) {
            metadata.remove(key);
        }
        for (String key : metadata.names()) {
            String[] values = metadata.getValues(key);
            // paranoid cleaning
            if (values == null || values.length == 0) {
                metadata.remove(key);
                continue;
            }
            // removes null or empty values
            ArrayList<String> nonEmpty = new ArrayList<>(values.length);
            for (String val : values) {
                if (val != null && !(val = val.strip()).isEmpty()) {
                    nonEmpty.add(val);
                }
            }
            if (nonEmpty.size() < values.length) {
                metadata.remove(key);
                for (String val : nonEmpty) {
                    metadata.add(key, val);
                }
            }
        }
    }

    public static final void normalizeMetadata(Metadata metadata) {
        // remove possible null key (#329)
        metadata.remove(null);
        String thumb = metadata.get(ExtraProperties.THUMBNAIL_BASE64);
        removeIgnorable(metadata);
        normalizeMSGMetadata(metadata);
        removeDuplicateKeys(metadata);
        normalizeGPSMeta(metadata);
        normalizeCase(metadata);
        prefixCommonMetadata(metadata);
        if (!prefixVideoMetadata(metadata)) {
            if (!prefixAudioMetadata(metadata)) {
                if (!prefixImageMetadata(metadata)) {
                    prefixPDFMetadata(metadata);
                }
            }
        }
        prefixDocMetadata(metadata);
        prefixBasicMetadata(metadata);
        removeDuplicateValues(metadata);
        renameKeys(metadata);
        removeExtraValsFromSingleValueKeys(metadata);
        fixImageDimensions(metadata);
        // add again removed empty/corrupted thumbnails
        if (thumb != null && thumb.isEmpty()) {
            metadata.set(ExtraProperties.THUMBNAIL_BASE64, "");
        }
    }

    private static void removeDuplicateKeys(Metadata metadata) {
        for (String key : metadata.names()) {
            Property prop = compositeProps.get(key);
            String[] values = metadata.getValues(key);
            if (prop != null && prop.getSecondaryExtractProperties() != null) {
                for (Property p : prop.getSecondaryExtractProperties()) {
                    String[] secValues = metadata.getValues(p.getName());
                    if (values.length == secValues.length) {
                        boolean equal = true;
                        for (int i = 0; i < values.length; i++) {
                            if (!values[i].equals(secValues[i])) {
                                equal = false;
                                break;
                            }
                        }
                        if (equal) {
                            metadata.remove(p.getName());
                        }
                    }
                }
            }
        }
        if (metadata.get(TiffPageParser.propNumPages) != null) {
            metadata.remove(TiffPageParser.propExifPageCount);
        }
    }

    private static void normalizeGPSMeta(Metadata metadata) {
        String lat = StringUtils.firstNonBlank(metadata.get(Metadata.LATITUDE),
                metadata.get(ExtraProperties.UFED_META_PREFIX + "Latitude"),
                metadata.get(ExtraProperties.UFED_META_PREFIX + "Associated Location Latitude"));

        String lon = StringUtils.firstNonBlank(metadata.get(Metadata.LONGITUDE),
                metadata.get(ExtraProperties.UFED_META_PREFIX + "Longitude"),
                metadata.get(ExtraProperties.UFED_META_PREFIX + "Associated Location Longitude"));

        boolean valid = StringUtils.isNoneBlank(lat, lon);
        if (valid) {
            lat = lat.replace(',', '.');
            lon = lon.replace(',', '.');
            try {
                Float lati = Float.valueOf(lat);
                Float longit = Float.valueOf(lon);
                if ((lati < -90 || lati > 90 || longit < -180 || longit > 180 || Float.isNaN(lati)
                        || Float.isNaN(longit)) || (lati == 0.0 && longit == 0.0)) {
                    valid = false;
                }
            } catch (NumberFormatException e) {
                valid = false;
            }
        }
        if (valid) {
            metadata.add(ExtraProperties.LOCATIONS, lat + ";" + lon);

            // always remove these, if valid, they were stored above
            metadata.remove(Metadata.LATITUDE.getName());
            metadata.remove(Metadata.LONGITUDE.getName());
            metadata.remove(ExtraProperties.UFED_META_PREFIX + "Latitude");
            metadata.remove(ExtraProperties.UFED_META_PREFIX + "Longitude");
            metadata.remove(ExtraProperties.UFED_META_PREFIX + "Associated Location Latitude");
            metadata.remove(ExtraProperties.UFED_META_PREFIX + "Associated Location Longitude");
        } else {
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
        if (!metadata.get(Metadata.CONTENT_TYPE).equals(MediaTypes.OUTLOOK_MSG.toString()))
            return;

        String subject = metadata.get(TikaCoreProperties.TITLE);
        if (subject == null || subject.isEmpty())
            subject = Messages.getString("MetadataUtil.NoSubject"); //$NON-NLS-1$
        metadata.set(ExtraProperties.MESSAGE_SUBJECT, subject);

        if (metadata.get(TikaCoreProperties.CREATED) != null)
            metadata.set(ExtraProperties.COMMUNICATION_DATE, metadata.get(TikaCoreProperties.CREATED));

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

        // deduplicate basic headers
        metadata.remove("subject"); //$NON-NLS-1$
        for (String meta : BASIC_MAIL_HEADERS) {
            metadata.remove(Message.MESSAGE_RAW_HEADER_PREFIX + meta);
        }

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
        int length = Math.max(Math.max(recipientNames.length, recipientsDisplay.length), recipientsEmails.length);
        for (int i = 0; i < length; i++) {
            String value = "";
            if (i < recipientNames.length && recipientNames[i] != null)
                value = recipientNames[i].trim();
            if (i < recipientsDisplay.length && recipientsDisplay[i] != null
                    && !value.toLowerCase().contains(recipientsDisplay[i].toLowerCase().trim()))
                value += " " + recipientsDisplay[i].trim(); //$NON-NLS-1$
            if (i < recipientsEmails.length && recipientsEmails[i] != null
                    && !value.toLowerCase().contains(recipientsEmails[i].toLowerCase().trim()))
                value += " \"" + recipientsEmails[i].trim() + "\""; //$NON-NLS-1$ //$NON-NLS-2$
            metadata.add(destMeta, value);
        }
    }

    // used to prefix metadata keys with names already used by basic item properties
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
        outer: for (String key : keys) {
            if (generalKeys.contains(key) || key.toLowerCase().startsWith(prefix.toLowerCase())
                    || key.startsWith(ExtraProperties.UFED_META_PREFIX)
                    || key.startsWith(ExtraProperties.COMMON_META_PREFIX)
                    || key.startsWith(ExtraProperties.CONVERSATION_PREFIX)
                    || key.startsWith(ExtraProperties.COMMUNICATION_PREFIX)
                    || key.startsWith(TikaCoreProperties.TIKA_META_PREFIX)) {
                continue;
            }
            for (String customPrefix : customMetadataPrefixes) {
                if (key.startsWith(customPrefix)) {
                    continue outer;
                }
            }
            String[] values = metadata.getValues(key);
            metadata.remove(key);
            for (String val : values)
                metadata.add(prefix + key, val);
        }
    }

    private static void prefixCommonMetadata(Metadata metadata) {
        for (String key : metadata.names()) {
            if (commonKeys.contains(key) && !key.startsWith(ExtraProperties.COMMON_META_PREFIX)) {
                String[] values = metadata.getValues(key);
                metadata.remove(key);
                for (String val : values) {
                    metadata.add(ExtraProperties.COMMON_META_PREFIX + key, val);
                }
            }
        }
    }

    private static boolean prefixAudioMetadata(Metadata metadata) {
        if (metadata.get(Metadata.CONTENT_TYPE).startsWith("audio")) {
            includePrefix(metadata, ExtraProperties.AUDIO_META_PREFIX);
            return true;
        }
        return false;
    }

    private static boolean prefixImageMetadata(Metadata metadata) {
        if (isImageType(metadata.get(Metadata.CONTENT_TYPE))) {
            includePrefix(metadata, ExtraProperties.IMAGE_META_PREFIX);
            return true;
        }
        return false;
    }

    public static boolean isImageSequence(String mediaType) {
        return mediaType.equals("image/heic-sequence") || mediaType.equals("image/heif-sequence");
    }

    /**
     * Check if the item is a multiple frame image. It works only after VideoThumbTask
     * is executed.
     */
    public static boolean isAnimationImage(IItem item) {
        return MetadataUtil.isImageSequence(item.getMediaType().toString()) ||
                item.getMetadata().get(ExtraProperties.ANIMATION_FRAMES_PROP) != null;
    }


    public static boolean isImageType(MediaType mediaType) {
        return mediaType == null ? false : isImageType(mediaType.toString());
    }

    public static boolean isImageType(String mediaType) {
        return mediaType == null ? false
                : mediaType.startsWith("image/") || mediaType.equals("application/coreldraw")
                        || mediaType.equals("application/x-vnd.corel.zcf.draw.document+zip");
    }

    public static boolean isVideoType(MediaType mediaType) {
        return mediaType == null ? false : isVideoType(mediaType.toString());
    }

    public static boolean isVideoType(String mediaType) {
        return mediaType == null ? false
                : mediaType.startsWith("video/") || mediaType.startsWith("application/vnd.rn-realmedia");
    }

    private static boolean prefixVideoMetadata(Metadata metadata) {
        if (isVideoType(metadata.get(Metadata.CONTENT_TYPE))
                || isVideoType(metadata.get(StandardParser.INDEXER_CONTENT_TYPE))) {
            includePrefix(metadata, ExtraProperties.VIDEO_META_PREFIX);
            return true;
        }
        return false;
    }

    private static boolean prefixPDFMetadata(Metadata metadata) {
        if (metadata.get(Metadata.CONTENT_TYPE).equals("application/pdf")) {
            includePrefix(metadata, ExtraProperties.PDF_META_PREFIX);
            return true;
        }
        return false;
    }

    private static void prefixDocMetadata(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        MediaType mediaType = MediaType.parse(contentType);

        if (contentType.startsWith("message") || //$NON-NLS-1$
                MediaTypes.OUTLOOK_MSG.toString().equals(contentType) ||
                MediaTypes.UFED_EMAIL_MIME.toString().equals(contentType) ||
                MediaTypes.UFED_MESSAGE_MIME.toString().equals(contentType) ||
                BPListDetector.PLIST.toString().equals(contentType))
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
            mediaType = MediaTypes.getParentType(mediaType);
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
            mediaType = MediaTypes.getParentType(mediaType);
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
            if (values != null && values.length > 0) {
                metadata.remove(oldName);
                String newName = renameMap.get(oldName);
                for (String val : values) {
                    metadata.add(newName, val);
                }
            }
        }
        for (String oldName : renameOrRemoveMap.keySet()) {
            String[] oldValues = metadata.getValues(oldName);
            if (oldValues != null && oldValues.length > 0) {
                metadata.remove(oldName);
                String newName = renameOrRemoveMap.get(oldName);
                String[] newValues = metadata.getValues(newName);
                if (newValues == null || newValues.length == 0) {
                    // Add old values only if there is no values associated with the new name 
                    for (String val : oldValues) {
                        metadata.add(newName, val);
                    }
                }
            }
        }
    }

    // currently keeps just last value, how to choose?
    private static void removeExtraValsFromSingleValueKeys(Metadata metadata) {
        for (String key : singleValueKeys) {
            String[] values = metadata.getValues(key);
            if (values != null && values.length > 1) {
                metadata.set(key, values[values.length - 1]);
            }
        }
    }

    public static void normalizeTerms(List<String> l) {
        Set<String> set = new HashSet<>();
        for (String term : l) {
            String s = normalizeTerm(term);
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        l.clear();
        l.addAll(set);
    }

    public static String normalizeTerm(String s) {
        char[] chars = s.toCharArray();
        int pos = 0;
        boolean upper = true;
        for (int i = 0; i < chars.length; i++) {
            Character c = chars[i];
            if (Character.isLetterOrDigit(c)) {
                if (upper) {
                    c = Character.toUpperCase(c);
                    upper = false;
                }
                chars[pos++] = c;
            } else {
                upper = true;
            }
        }
        return new String(chars, 0, pos);
    }

    private static void fixImageDimensions(Metadata metadata) {
        fixImageDimension(metadata, ExtraProperties.IMAGE_META_PREFIX + "Width");
        fixImageDimension(metadata, ExtraProperties.IMAGE_META_PREFIX + "Height");
    }

    private static void fixImageDimension(Metadata metadata, String key) {
        String value = metadata.get(key);
        if (value != null) {
            int pos = value.toLowerCase().indexOf("pixels");
            if (pos > 0) {
                value = value.substring(0, pos).strip();
                if (!value.isEmpty()) {
                    metadata.set(key, value);
                }
            }

        }
    }
}
