package iped.engine.task.leapp;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.task.leapp.conversation.Conversation;
import iped.engine.task.leapp.conversation.ConversationCreator;
import iped.engine.task.leapp.conversation.ConversationMessage;
import iped.engine.task.leapp.conversation.ConversationViewSpec;
import iped.engine.task.leapp.interceptors.IlapfuncsEmbeddedMediaInterceptor;
import iped.parsers.util.MetadataUtil;
import iped.properties.ExtraProperties;

/**
 * Turns the results of one LEAPP plugin run into IPED subitems.
 *
 * <p>
 * This is where the plugin output is actually interpreted. It receives the two raw values a plugin produces —
 * {@code data_headers} (the column list) and {@code data_list} (the rows) — and for each row creates a subitem of the
 * plugin evidence, storing every cell as "&lt;tool&gt;:&lt;header&gt;" metadata (e.g. "aleapp:Date").
 *
 * <p>
 * Some columns are additionally mapped to IPED standard properties. Location, media and file-path columns are mapped
 * for every plugin, but the communication properties (Message:Date, Communication:From/To, Message:Body) are only
 * filled for plugins that produce conversations, where they carry their intended meaning.
 *
 * <p>
 * The values are captured from the Python side by
 * {@link iped.engine.task.leapp.interceptors.LavaInsertSqliteDataInterceptor}, which only forwards them here.
 */
public class PluginResultsProcessor {

    // Fallback classification for UNTYPED (plain string) headers, calibrated against
    // ALEAPP v2026.1.0 sources. Date columns vary a lot across plugins, so dates use
    // exact names plus a suffix family. Sender, recipient and body use exact matches
    // only: mislabeling those is forensically costly (e.g. "Account" is usually the
    // device owner, who is the receiver of incoming records, not the sender).
    // "Date of Birth"-style personal dates must not become the record's event date,
    // so there is no "date " prefix rule — "date *" event columns are listed explicitly
    // ("date of sleep", "birthday" and "birthdate (mm-dd)" are deliberately left out).
    // "creationtime" has no separator, so the " time" suffix rule does not catch it.
    private static final Set<String> DATE_HEADERS = Set.of( //
            "datetime", "date/time", "date", "created", "created at", "updated at", //
            "time created", "creationtime", "last updated", "last login", "last modified", //
            "last access", "last accessed", //
            "date added", "date created", "date modified", "date sent", "date stored", "date taken", //
            "time", "date received", "date last used", "last update", "last contacted");

    // Since ALEAPP v2026.4 real date columns are declared as ('name', 'datetime'), so untyped columns named like a date
    // are often something else: the value as it is stored or printed (not converted), a duration or a status.
    private static final Pattern NOT_A_DATE = Pattern.compile(
            "\\b(as (stored|printed|recorded|logged)|raw|millis|ms|seconds|status|basis|records|position|relative|no zone)\\b|\\(s\\)");
    private static final Set<String> DURATION_HEADERS = Set.of("sleep time", "total sleep time", "round trip time",
            "watch time", "played time", "max played time");
    private static final Set<String> FROM_HEADERS = Set.of("sender", "from", "author", "sender name", "message sender");
    private static final Set<String> TO_HEADERS = Set.of("recipient", "to", "receiver", "recipients", "recipient name");
    private static final Set<String> BODY_HEADERS = Set.of("message", "body", "text", "content", "message text",
            "message body", "message content");

    // UNTYPED columns whose value is a device file path but which ALEAPP did NOT declare as ('name', 'media').
    // These cells are not rewritten by the media interception (check_in_media), so their value stays as the raw
    // device path (e.g. chromeOfflinePages "File Path", WhatsApp "Local Path To Media"). When the seeker did not
    // export the file, the fallback below still links it to its original case item via IItemSearcher.
    // The suffix rules in isFilePathHeader cover the many "* Path" columns across plugins (Download Path, Source
    // File Path, Save Path, Full Path, Original Path, Screenshot Path, Code Path, file_path, *_filepath, ...); this
    // set only holds the few file-path column names those rules do not catch.
    private static final Set<String> FILE_PATH_HEADERS = Set.of("local path to media");

    // "* Path" columns that are not paths of the device file system
    private static final Pattern NOT_A_FILE_PATH = Pattern.compile("\\b(url|json|xpath|cloud|remote|server)\\b");

    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";

    // coordinate columns can come in more than one pair per row ("Start Latitude"/"Start Longitude", "End ..."): the
    // pairs are told apart by what comes before the word. These ones are not places, but the limits of an area.
    private static final Set<String> NOT_A_PLACE_PREFIXES = Set.of("min", "max");

    // types used in LEAPP data_headers tuples (see lavafuncs.get_sql_type and ilapfuncs.get_media_header_info)
    private static final String LEAPP_TYPE_DATETIME = "datetime";
    private static final String LEAPP_TYPE_DATE = "date";
    private static final String LEAPP_TYPE_MEDIA = "media";

    private enum StandardField {
        DATE, FROM, TO, BODY, LATITUDE, LONGITUDE, MEDIA, FILE_PATH, NONE
    }

    /** A data_headers entry: plain string or (name, type[, style]) tuple. */
    private static class Header {
        final String name;
        final String type;

        Header(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private final LeappContext context;

    /**
     * Whether this plugin run produces conversations. Only then are recognized columns promoted to the communication
     * properties (Message:Date, Communication:From/To, Message:Body) — see {@link #createSubItem}.
     */
    private boolean conversationPlugin;

    /** Ids of the subitems created by this plugin run. */
    private final AtomicInteger subitemIdSeq = new AtomicInteger();

    public PluginResultsProcessor(LeappContext context) {
        this.context = context;
    }

    /**
     * Creates the subitems for one plugin run.
     *
     * @param rawHeaders the plugin's data_headers: column names, each a plain string or a (name, type) tuple
     * @param dataList the plugin's data_list: one entry per row, holding the cell values
     */
    public void process(List<Object> rawHeaders, List<List<Object>> dataList) throws Exception {

        // set hasChildren, so plugin will not be ignored in AbstractLeappTask.processPluginEvidence()
        // and category will not be ignored in AbstractLeappTask.processCategoryEvidence()
        context.getPluginItem().setHasChildren(true);
        IItem categoryItem = (IItem) context.getPluginItem().getTempAttribute(AbstractLeappTask.LEAPP_PLUGIN_CATEGORY_KEY);
        categoryItem.setHasChildren(true);

        addLinkedItems();

        String artifactName = StringUtils.firstNonBlank(
                (String) context.getPlugin().getArtifactInfo().get("name"), context.getPlugin().getName());
        String pluginName = context.getPluginItem().getMetadata().get(context.getTool().getPluginMetaPrefix() + "key");
        String pluginModule = context.getPlugin().getModuleName();
        // headers are constant across rows: parse and classify each column once
        Header[] headers = new Header[rawHeaders.size()];
        StandardField[] standardFields = new StandardField[rawHeaders.size()];
        for (int i = 0; i < rawHeaders.size(); i++) {
            headers[i] = parseHeader(rawHeaders.get(i));
            standardFields[i] = classifyHeader(headers[i]);
            // a column named like a file path may still be a non-file path in a specific plugin
            // (e.g. the cookie "Path"): drop it back to NONE so no file lookup is attempted
            if (standardFields[i] == StandardField.FILE_PATH && context.getTool().isNonFilePathColumn(pluginModule, headers[i].name)) {
                standardFields[i] = StandardField.NONE;
            }
            if (standardFields[i] == StandardField.DATE) {
                // declares the column as a date metadata, so it is indexed as a real date instead of
                // being stored as text plus a duplicated "...:date" field created by IndexItem.
                MetadataUtil.setMetadataType(context.getTool().getMetaPrefix() + headers[i].name, Date.class);
            }
        }

        // a "conversation" data view groups the rows into chats: each conversation
        // becomes a chat-preview child item of the plugin evidence (with a UFED-like
        // HTML rendering) and the row subitems become children of their conversation
        ConversationViewSpec view = ConversationViewSpec.from(context.getPlugin().getArtifactInfo());
        int discriminatorIdx = view == null ? -1 : indexOfColumn(headers, view.getDiscriminatorColumn());
        conversationPlugin = discriminatorIdx >= 0;

        MediaType mediaType = context.getTool().resolveRowMediaType(pluginModule, pluginName, artifactName, conversationPlugin);

        if (conversationPlugin) {
            createConversations(mediaType, artifactName, headers, standardFields, dataList, view, discriminatorIdx, subitemIdSeq);
        } else {
            for (int index = 0; index < dataList.size(); index++) {
                Item subItem = createSubItem(context.getPluginItem(), mediaType, artifactName, index,
                        subitemIdSeq.getAndIncrement(), headers, standardFields, dataList.get(index));
                context.getWorker().processNewItem(subItem, ProcessTime.LATER);
            }
        }
    }

    /**
     * Groups the data rows by the discriminator column into {@link Conversation}s and delegates item/HTML creation to
     * {@link ConversationCreator}. The row subitems are still created by {@link #createSubItem} (via the factory), so
     * their metadata is identical to the non-conversation case: only their parent changes.
     */
    private void createConversations(MediaType mediaType, String artifactName, Header[] headers,
            StandardField[] standardFields, List<List<Object>> dataList, ConversationViewSpec view,
            int discriminatorIdx, AtomicInteger subitemIdSeq) throws Exception {

        int labelIdx = indexOfColumn(headers, view.getLabelColumn());
        int textIdx = indexOfColumn(headers, view.getTextColumn());
        int directionIdx = indexOfColumn(headers, view.getDirectionColumn());
        int timeIdx = indexOfColumn(headers, view.getTimeColumn());
        int senderIdx = indexOfColumn(headers, view.getSenderColumn());
        int mediaIdx = indexOfColumn(headers, view.getMediaColumn());

        // extra columns to show with each message: header index -> column name
        Map<Integer, String> extraColumns = new LinkedHashMap<>();
        for (String column : view.getExtraColumns()) {
            int idx = indexOfColumn(headers, column);
            if (idx >= 0) {
                extraColumns.put(idx, headers[idx].name);
            }
        }

        Map<String, Conversation> conversations = new LinkedHashMap<>();

        for (int index = 0; index < dataList.size(); index++) {
            List<Object> data = dataList.get(index);

            String conversationId = StringUtils.defaultString(cellString(data, discriminatorIdx));
            String label = cellString(data, labelIdx);
            Conversation conversation = conversations.computeIfAbsent(conversationId,
                    id -> new Conversation(id, label, artifactName));

            // null (unknown) when the view declares no direction column/value: the
            // Communication:Direction metadata is only set when the direction is known
            Boolean outgoing = (directionIdx < 0 || view.getDirectionSentValue() == null) ? null
                    : view.getDirectionSentValue().equalsIgnoreCase(StringUtils.trim(cellString(data, directionIdx)));

            // with a static label, the sender column of a sent message holds the other party, not the sender
            String sender = cellString(data, senderIdx);
            String recipient = null;
            if (Boolean.TRUE.equals(outgoing) && view.getSentMessageStaticLabel() != null) {
                recipient = sender;
                sender = view.getSentMessageStaticLabel();
            }

            ConversationMessage message = new ConversationMessage(index, sender, cellString(data, textIdx), outgoing,
                    cellString(data, timeIdx));
            message.setRecipient(recipient);

            for (Map.Entry<Integer, String> extra : extraColumns.entrySet()) {
                String value = cellString(data, extra.getKey());
                if (StringUtils.isNotBlank(value)) {
                    message.getExtras().put(extra.getValue(), value);
                }
            }

            Map<String, String[]> coordinates = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                Object value = cellValue(data, i);
                if (value == null) {
                    continue;
                }
                if (standardFields[i] == StandardField.MEDIA || i == mediaIdx) {
                    message.getMediaItems().addAll(getMediaCaseItems(context, value));
                } else if (standardFields[i] == StandardField.LATITUDE || standardFields[i] == StandardField.LONGITUDE) {
                    addCoordinate(coordinates, headers[i].name, standardFields[i], value.toString());
                }
            }
            // the place of the message itself is in the plain columns, when there is more than one pair
            String[] place = coordinates.getOrDefault("", coordinates.values().stream().findFirst().orElse(new String[2]));
            message.setLocation(place[0], place[1]);

            conversation.getMessages().add(message);
        }

        ConversationCreator creator = new ConversationCreator(context, view,
                (parent, rowIndex, subitemId) -> createSubItem(parent, mediaType, artifactName, rowIndex,
                        subitemId, headers, standardFields, dataList.get(rowIndex)));

        creator.createConversations(new ArrayList<>(conversations.values()), subitemIdSeq);
    }

    /**
     * Index of the given data view column in data_headers; -1 when absent. Exact match first, then a trimmed
     * case-insensitive fallback, since view declarations are hand-written in the plugins.
     */
    private static int indexOfColumn(Header[] headers, String column) {
        if (column == null) {
            return -1;
        }
        for (int i = 0; i < headers.length; i++) {
            if (column.equals(headers[i].name)) {
                return i;
            }
        }
        for (int i = 0; i < headers.length; i++) {
            if (column.trim().equalsIgnoreCase(StringUtils.trim(headers[i].name))) {
                return i;
            }
        }
        return -1;
    }

    private static String cellString(List<Object> data, int idx) {
        if (idx < 0) {
            return null;
        }
        Object value = cellValue(data, idx);
        return value == null ? null : value.toString();
    }

    /**
     * Resolves the case items referenced by a 'media' typed cell (one exported path or a list of them). Paths not
     * exported by the seeker have no case item counterpart and are skipped.
     */
    @SuppressWarnings("unchecked")
    private static List<IItemReader> getMediaCaseItems(LeappContext context, Object value) {
        List<Object> mediaPaths = (value instanceof List) ? (List<Object>) value : List.of(value);
        List<IItemReader> items = new ArrayList<>();
        for (Object mediaPath : mediaPaths) {
            if (mediaPath == null) {
                continue;
            }
            IItemReader item = context.getFileSeeker().findExported(mediaPath.toString());
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Links the plugin evidence to all files found by the seeker for this plugin.
     */
    private void addLinkedItems() {
        Set<String> globalIds = new HashSet<>();
        for (IItemReader foundFile : context.getFoundFiles()) {
            globalIds.add((String) foundFile.getExtraAttribute(ExtraProperties.GLOBAL_ID));
        }
        String linkedItems = ExtraProperties.GLOBAL_ID + ":(" + String.join(" ", globalIds) + ")";
        context.getPluginItem().getMetadata().add(ExtraProperties.LINKED_ITEMS, linkedItems);
    }

    /**
     * Creates one subitem for a data row under the given parent (the plugin evidence or, when a conversation view
     * exists, the conversation part item), storing each cell as "&lt;tool&gt;:&lt;header&gt;" metadata and mapping typed or
     * recognized columns to IPED standard properties.
     */
    private Item createSubItem(IItem parent, MediaType mediaType, String artifactName, int index,
            int subitemId, Header[] headers, StandardField[] standardFields, List<Object> data) {

        String subItemName = artifactName + "-" + index;
        Item subItem = (Item) parent.createChildItem();
        subItem.setMediaType(mediaType);
        subItem.setName(subItemName);
        subItem.setExtension("");
        subItem.setPath(parent.getPath() + "/" + subItemName);
        subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
        subItem.setSubItem(true);
        subItem.setSubitemId(subitemId);
        subItem.setSumVolume(false);

        // data as metadata
        Map<String, String[]> coordinates = new LinkedHashMap<>();
        List<String> filePathMetaKeys = null;
        for (int i = 0; i < headers.length; i++) {
            Object value = cellValue(data, i);
            if (value == null) {
                continue;
            }

            if (standardFields[i] == StandardField.MEDIA) {
                addMediaValue(subItem, headers[i].name, value);
                continue;
            }

            String valueStr = value.toString();

            // cells holding a path exported by the seeker are rewritten back to the
            // original in-case path and linked to the original item
            boolean linkedToCaseItem = false;
            IItemReader valueItem = context.getFileSeeker().findExported(valueStr);
            if (valueItem != null) {
                valueStr = StringUtils.removeStart(valueItem.getPath(), context.getFileSeeker().getPathRoot());
                subItem.getMetadata().add(ExtraProperties.LINKED_ITEMS, ExtraProperties.GLOBAL_ID + ":" + valueItem.getExtraAttribute(ExtraProperties.GLOBAL_ID));
                linkedToCaseItem = true;
            }

            // fallback for UNTYPED file-path columns (e.g. "File Path"): the value is a device file
            // path the seeker did not export, so it is not in getExportedFiles(). Resolving it needs an
            // index search per value, too costly to run here for every row: just record the metadata key
            // and let AbstractLeappTask.process() resolve and link it when this subitem is reprocessed by a worker.
            if (!linkedToCaseItem && standardFields[i] == StandardField.FILE_PATH) {
                if (filePathMetaKeys == null) {
                    filePathMetaKeys = new ArrayList<>();
                }
                filePathMetaKeys.add(context.getTool().getMetaPrefix() + headers[i].name);
            }

            // cells promoted to a standard property (Communication:*, Message-Body) are
            // not duplicated as "<tool>:" metadata. Only conversation plugins promote: for the
            // others the communication properties have no meaning, and the promoted column would
            // be picked by position (the first date/sender/body column wins), which is arbitrary.
            boolean promoted = false;
            if (standardFields[i] == StandardField.LATITUDE || standardFields[i] == StandardField.LONGITUDE) {
                addCoordinate(coordinates, headers[i].name, standardFields[i], valueStr);
            } else if (conversationPlugin && standardFields[i] != StandardField.NONE) {
                promoted = applyStandardField(subItem, standardFields[i], valueStr);
            }
            if (!promoted) {
                subItem.getMetadata().set(context.getTool().getMetaPrefix() + headers[i].name, valueStr);
            }
        }
        for (String[] pair : coordinates.values()) {
            addLocationIfValid(subItem, pair[0], pair[1]);
        }

        // the recorded file-path columns are resolved and linked later, in AbstractLeappTask.process()
        if (filePathMetaKeys != null) {
            subItem.setTempAttribute(AbstractLeappTask.LEAPP_METADATA_PATHS, filePathMetaKeys);
        }

        return subItem;
    }

    /**
     * Handles a cell of a 'media' typed column. With check_in_media patched to return the exported extraction path
     * (see LeappInterceptors), the cell holds one exported path or a list of them: link each one to the original case
     * item and store its in-case path as metadata.
     */
    @SuppressWarnings("unchecked")
    private void addMediaValue(Item subItem, String headerName, Object value) {

        List<Object> mediaPaths = (value instanceof List) ? (List<Object>) value : List.of(value);
        Set<String> embeddedDone = new HashSet<>();

        for (Object mediaPath : mediaPaths) {
            if (mediaPath == null) {
                continue;
            }
            String pathStr = mediaPath.toString();

            // data registered through check_in_embedded_media: it becomes a child item of the row
            LeappContext.EmbeddedMedia embedded = context.getEmbeddedMedia().get(pathStr);
            if (embedded != null) {
                if (embeddedDone.add(pathStr)) {
                    String name = addEmbeddedMedia(subItem, embedded, pathStr);
                    subItem.getMetadata().add(context.getTool().getMetaPrefix() + headerName, name);
                }
                continue;
            }

            IItemReader mediaItem = context.getFileSeeker().findExported(pathStr);
            if (mediaItem != null) {
                pathStr = StringUtils.removeStart(mediaItem.getPath(), context.getFileSeeker().getPathRoot());
                subItem.getMetadata().add(ExtraProperties.LINKED_ITEMS,
                        ExtraProperties.GLOBAL_ID + ":" + mediaItem.getExtraAttribute(ExtraProperties.GLOBAL_ID));
            }
            subItem.getMetadata().add(context.getTool().getMetaPrefix() + headerName, pathStr);
        }
    }

    /**
     * Records an embedded media to become a child item of the row subitem, and returns the name it will get. The child
     * cannot be created yet: a child item needs the trackId of its parent, only set after the parent is enqueued. So
     * it is done when the row comes back through AbstractLeappTask.process() (see processEmbeddedMedia).
     */
    @SuppressWarnings("unchecked")
    private String addEmbeddedMedia(Item rowItem, LeappContext.EmbeddedMedia embedded, String reference) {

        String hash = StringUtils.substringAfter(reference, IlapfuncsEmbeddedMediaInterceptor.REFERENCE_PREFIX);

        // the name given by the plugin may be a path
        String name = FilenameUtils.getName(StringUtils.trimToEmpty(embedded.name));
        if (name.isEmpty()) {
            name = "embedded-" + StringUtils.left(hash, 8);
        }
        if (StringUtils.isNotBlank(embedded.extension) && LeappUtils.getExtension(name).isEmpty()) {
            name += "." + embedded.extension;
        }

        Map<String, byte[]> pending = (Map<String, byte[]>) rowItem.getTempAttribute(AbstractLeappTask.LEAPP_EMBEDDED_MEDIA);
        if (pending == null) {
            pending = new LinkedHashMap<>();
            rowItem.setTempAttribute(AbstractLeappTask.LEAPP_EMBEDDED_MEDIA, pending);
        }
        pending.putIfAbsent(name, embedded.data);
        rowItem.setHasChildren(true);

        return name;
    }

    private static Object cellValue(List<Object> data, int i) {
        return (i < data.size()) ? data.get(i) : null;
    }

    /**
     * data_headers entries are plain strings or (name, type[, style]) tuples — Jep converts tuples to Lists.
     */
    private static Header parseHeader(Object rawHeader) {
        if (rawHeader instanceof List) {
            List<?> tuple = (List<?>) rawHeader;
            String name = String.valueOf(tuple.get(0));
            String type = tuple.size() > 1 ? StringUtils.lowerCase(String.valueOf(tuple.get(1)), Locale.ROOT) : null;
            return new Header(name, type);
        }
        return new Header(String.valueOf(rawHeader), null);
    }

    private static boolean isDateHeader(String h) {
        if (DATE_HEADERS.contains(h)) {
            return true;
        }
        if (DURATION_HEADERS.contains(h) || NOT_A_DATE.matcher(h).find()) {
            return false;
        }
        return h.contains("timestamp") || StringUtils.endsWithAny(h, " time", " date", "_date");
    }

    private static boolean isFilePathHeader(String h) {
        if (FILE_PATH_HEADERS.contains(h)) {
            return true;
        }
        if (NOT_A_FILE_PATH.matcher(h).find()) {
            return false;
        }
        return h.equals("path") || StringUtils.endsWithAny(h, " path", "_path", "filepath");
    }

    /**
     * For a coordinate column, returns what tells its pair apart from the others of the row: the text before the given
     * word ("latitude"/"longitude"), empty for the plain column. Returns null when the column is not a coordinate.
     */
    private static String coordinatePrefix(String h, String word) {
        if (h.equals(word)) {
            return "";
        }
        if (!h.endsWith(" " + word)) {
            return null;
        }
        String prefix = StringUtils.removeEnd(h, " " + word).trim();
        return NOT_A_PLACE_PREFIXES.contains(prefix) ? null : prefix;
    }

    /**
     * Classification is type-driven when the header carries a type; the name-based heuristics are only a fallback for
     * untyped headers.
     */
    private static StandardField classifyHeader(Header header) {

        if (LEAPP_TYPE_MEDIA.equals(header.type)) {
            return StandardField.MEDIA;
        }
        if (LEAPP_TYPE_DATETIME.equals(header.type) || LEAPP_TYPE_DATE.equals(header.type)) {
            return StandardField.DATE;
        }

        String h = header.name.toLowerCase(Locale.ROOT).trim();
        if (header.type == null && isDateHeader(h)) {
            return StandardField.DATE;
        } else if (FROM_HEADERS.contains(h)) {
            return StandardField.FROM;
        } else if (TO_HEADERS.contains(h)) {
            return StandardField.TO;
        } else if (BODY_HEADERS.contains(h)) {
            return StandardField.BODY;
        } else if (coordinatePrefix(h, LATITUDE) != null) {
            return StandardField.LATITUDE;
        } else if (coordinatePrefix(h, LONGITUDE) != null) {
            return StandardField.LONGITUDE;
        } else if (header.type == null && isFilePathHeader(h)) {
            return StandardField.FILE_PATH;
        }
        return StandardField.NONE;
    }

    /** Returns true when the value was stored in the standard property. */
    private static boolean applyStandardField(Item item, StandardField field, String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        switch (field) {
            case DATE:
                return setIfAbsent(item, ExtraProperties.MESSAGE_DATE, value);
            case FROM:
                // Communication:From/To directly (the standard cross-parser properties):
                // the Message:From/To -> Communication:From/To rename of
                // MetadataUtil.normalizeMetadata only runs inside StandardParser
                return setIfAbsent(item, ExtraProperties.COMMUNICATION_FROM, value);
            case TO:
                return setIfAbsent(item, ExtraProperties.COMMUNICATION_TO, value);
            case BODY:
                return setIfAbsent(item, ExtraProperties.MESSAGE_BODY, value);
            default:
                return false;
        }
    }

    /** Puts the value of a coordinate column in its pair (see {@link #coordinatePrefix}). */
    private static void addCoordinate(Map<String, String[]> coordinates, String headerName, StandardField field, String value) {
        boolean latitude = field == StandardField.LATITUDE;
        String prefix = coordinatePrefix(headerName.toLowerCase(Locale.ROOT).trim(), latitude ? LATITUDE : LONGITUDE);
        coordinates.computeIfAbsent(prefix, k -> new String[2])[latitude ? 0 : 1] = value;
    }

    private static void addLocationIfValid(Item item, String lat, String lon) {
        if (StringUtils.isBlank(lat) || StringUtils.isBlank(lon)) {
            return;
        }
        // ALeapp data may use comma as decimal separator (locale-formatted
        // devices), as MetadataUtil.normalizeGPSMeta also handles
        lat = lat.trim().replace(',', '.');
        lon = lon.trim().replace(',', '.');
        try {
            double la = Double.parseDouble(lat);
            double lo = Double.parseDouble(lon);
            if (!Double.isFinite(la) || !Double.isFinite(lo) //
                    || (la == 0 && lo == 0) || la < -90 || la > 90 || lo < -180 || lo > 180) {
                return;
            }
            // store the original strings like other LOCATIONS writers do:
            // Double.toString would emit scientific notation for small values,
            // which the KML consumers don't accept
            item.getMetadata().add(ExtraProperties.LOCATIONS, lat + ";" + lon);
        } catch (NumberFormatException e) {
            // non-numeric coordinate values: leave them as <tool>:* metadata only
        }
    }

    private static boolean setIfAbsent(Item item, Property key, String value) {
        if (item.getMetadata().get(key) == null) {
            item.getMetadata().set(key, value);
            return true;
        }
        return false;
    }

    private static boolean setIfAbsent(Item item, String key, String value) {
        if (item.getMetadata().get(key) == null) {
            item.getMetadata().set(key, value);
            return true;
        }
        return false;
    }

}
