package iped.engine.task.leapp.interceptors;

import static iped.engine.task.leapp.AleappTask.ALEAPP_APPLICATION_PREFIX;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.metadata.Message;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.engine.core.Worker.ProcessTime;
import iped.engine.data.Item;
import iped.engine.task.leapp.AleappTask;
import iped.engine.task.leapp.CallInterceptor;
import iped.engine.task.leapp.LeappContext;
import iped.properties.ExtraProperties;
import jep.PyMethod;

/**
 * Replaces the lava_insert_sqlite_data function. This is the main IPED-LEAPP integration point: instead of letting the
 * plugin results be written to the LAVA sqlite database, each data row is turned into an IPED subitem of the current
 * plugin evidence.
 *
 * lava_insert_sqlite_data is intercepted (rather than tsv/timeline/html) because it is the only output call that
 * receives the RAW data_headers and data_list: headers keep their (name, type) tuples and values keep their original
 * Python types — the other outputs receive stripped headers and stringified values.
 *
 * NOTE: the interception target is the binding INSIDE scripts.ilapfuncs ("from scripts.lavafuncs import
 * lava_insert_sqlite_data" is captured at import time), which is the name artifact_processor actually calls.
 */
public class LavaInsertSqliteDataInterceptor extends CallInterceptor {

    protected static final Logger logger = LoggerFactory.getLogger(LavaInsertSqliteDataInterceptor.class);

    // Fallback classification for UNTYPED (plain string) headers, calibrated against
    // ALEAPP v2026.1.0 sources. Date columns vary a lot across plugins, so dates use
    // exact names plus a suffix family. Sender, recipient and body use exact matches
    // only: mislabeling those is forensically costly (e.g. "Account" is usually the
    // device owner, who is the receiver of incoming records, not the sender).
    // "Date of Birth"-style personal dates must not become the record's event date,
    // so there is no "date " prefix rule — "date *" event columns are listed explicitly.
    private static final Set<String> DATE_HEADERS = Set.of( //
            "datetime", "date/time", "date", "created", "created at", "updated at", //
            "time created", "last updated", "last login", "last modified", "last access", "last accessed", //
            "date added", "date created", "date modified", "date sent", "date taken");
    private static final Set<String> FROM_HEADERS = Set.of("sender", "from", "author");
    private static final Set<String> TO_HEADERS = Set.of("recipient", "to", "receiver");
    private static final Set<String> BODY_HEADERS = Set.of("message", "body", "text", "content");

    // types used in LEAPP data_headers tuples (see lavafuncs.get_sql_type and
    // ilapfuncs.get_media_header_info)
    private static final String TYPE_DATETIME = "datetime";
    private static final String TYPE_DATE = "date";
    private static final String TYPE_MEDIA = "media";

    private enum StandardField {
        DATE, FROM, TO, BODY, LATITUDE, LONGITUDE, MEDIA, NONE
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

    public LavaInsertSqliteDataInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.lava_insert_sqlite_data");
    }

    @SuppressWarnings("unchecked")
    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        // lava_insert_sqlite_data(table_name, data, object_columns, headers, column_map)
        // table_name/object_columns/column_map come from lava_process_artifact, which is
        // disabled and returns None values: only data and headers are used here
        List<List<Object>> dataList = (List<List<Object>>) getArgumentValue("data", 1, args, kwargs);
        List<Object> rawHeaders = (List<Object>) getArgumentValue("headers", 3, args, kwargs);

        if (dataList == null || dataList.isEmpty()) {
            return null;
        }

        // the interceptor is installed globally in the Python interpreter, so the
        // thread-local context tells us which plugin run this call belongs to
        LeappContext context = LeappContext.get();

        // set hasChildren, so plugin will not be ignored in AleappTask.processPluginEvidence()
        // and category will not be ignored in AleappTask.processCategoryEvidence()
        context.getPluginItem().setHasChildren(true);
        IItem categoryItem = (IItem) context.getPluginItem().getTempAttribute(AleappTask.ALEAPP_PLUGIN_CATEGORY_KEY);
        categoryItem.setHasChildren(true);

        addLinkedItems(context);

        String artifactName = StringUtils.firstNonBlank(
                (String) context.getPlugin().getArtifactInfo().get("name"), context.getPlugin().getName());
        String pluginName = context.getPluginItem().getMetadata().get(AleappTask.ALEAPP_PLUGIN_KEYNAME_META);
        MediaType mediaType = resolveMediaType(artifactName, pluginName);

        // headers are constant across rows: parse and classify each column once
        Header[] headers = new Header[rawHeaders.size()];
        StandardField[] standardFields = new StandardField[rawHeaders.size()];
        for (int i = 0; i < rawHeaders.size(); i++) {
            headers[i] = parseHeader(rawHeaders.get(i));
            standardFields[i] = classifyHeader(headers[i]);
        }

        for (int index = 0; index < dataList.size(); index++) {
            Item subItem = createSubItem(context, mediaType, artifactName, index, headers, standardFields, dataList.get(index));
            context.getWorker().processNewItem(subItem, ProcessTime.LATER);
        }

        return null;
    }

    /**
     * Links the plugin evidence to all files found by the seeker for this plugin.
     */
    private void addLinkedItems(LeappContext context) {
        Set<String> globalIds = new HashSet<>();
        for (IItemReader foundFile : context.getFoundFiles()) {
            globalIds.add((String) foundFile.getExtraAttribute(ExtraProperties.GLOBAL_ID));
        }
        String linkedItems = ExtraProperties.GLOBAL_ID + ":(" + String.join(" ", globalIds) + ")";
        context.getPluginItem().getMetadata().add(ExtraProperties.LINKED_ITEMS, linkedItems);
    }

    /**
     * Creates one subitem for a data row, storing each cell as "aleapp:&lt;header&gt;" metadata and mapping typed or
     * recognized columns to IPED standard properties.
     */
    private Item createSubItem(LeappContext context, MediaType mediaType, String artifactName, int index,
            Header[] headers, StandardField[] standardFields, List<Object> data) {

        String subItemName = artifactName + "-" + index;
        Item subItem = (Item) context.getPluginItem().createChildItem();
        subItem.setMediaType(mediaType);
        subItem.setName(subItemName);
        subItem.setExtension("");
        subItem.setPath(context.getPluginItem().getPath() + "/" + subItemName);
        subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
        subItem.setSubItem(true);
        subItem.setSubitemId(index);

        // data as metadata
        String lat = null, lon = null;
        for (int i = 0; i < headers.length; i++) {
            Object value = cellValue(data, i);
            if (value == null) {
                continue;
            }

            if (standardFields[i] == StandardField.MEDIA) {
                addMediaValue(context, subItem, headers[i].name, value);
                continue;
            }

            String valueStr = value.toString();

            // cells holding a path exported by the seeker are rewritten back to the
            // original in-case path and linked to the original item
            if (context.getFileSeeker().getExportedFiles().containsKey(valueStr)) {
                IItemReader valueItem = context.getFileSeeker().getExportedFiles().get(valueStr);
                valueStr = StringUtils.removeStart(valueItem.getPath(), context.getFileSeeker().getPathRoot());
                subItem.getMetadata().add(ExtraProperties.LINKED_ITEMS, ExtraProperties.GLOBAL_ID + ":" + valueItem.getExtraAttribute(ExtraProperties.GLOBAL_ID));
            }

            subItem.getMetadata().set("aleapp:" + headers[i].name, valueStr);

            if (standardFields[i] == StandardField.LATITUDE) {
                lat = valueStr;
            } else if (standardFields[i] == StandardField.LONGITUDE) {
                lon = valueStr;
            } else if (standardFields[i] != StandardField.NONE) {
                applyStandardField(subItem, standardFields[i], valueStr);
            }
        }
        setLocationIfValid(subItem, lat, lon);

        return subItem;
    }

    /**
     * Handles a cell of a 'media' typed column. With check_in_media patched to return the exported extraction path
     * (see LeappInterceptors), the cell holds one exported path or a list of them: link each one to the original case
     * item and store its in-case path as metadata.
     */
    @SuppressWarnings("unchecked")
    private void addMediaValue(LeappContext context, Item subItem, String headerName, Object value) {

        List<Object> mediaPaths = (value instanceof List) ? (List<Object>) value : List.of(value);

        for (Object mediaPath : mediaPaths) {
            if (mediaPath == null) {
                continue;
            }
            String pathStr = mediaPath.toString();
            IItemReader mediaItem = context.getFileSeeker().getExportedFiles().get(pathStr);
            if (mediaItem != null) {
                pathStr = StringUtils.removeStart(mediaItem.getPath(), context.getFileSeeker().getPathRoot());
                subItem.getMetadata().add(ExtraProperties.LINKED_ITEMS,
                        ExtraProperties.GLOBAL_ID + ":" + mediaItem.getExtraAttribute(ExtraProperties.GLOBAL_ID));
            }
            subItem.getMetadata().add("aleapp:" + headerName, pathStr);
        }
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
        return DATE_HEADERS.contains(h) || h.contains("timestamp") //
                || h.endsWith(" time") || h.endsWith(" date") || h.endsWith("_date");
    }

    /**
     * Classification is type-driven when the header carries a type; the name-based heuristics are only a fallback for
     * untyped headers.
     */
    private static StandardField classifyHeader(Header header) {

        if (TYPE_MEDIA.equals(header.type)) {
            return StandardField.MEDIA;
        }
        if (TYPE_DATETIME.equals(header.type) || TYPE_DATE.equals(header.type)) {
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
        } else if (h.equals("latitude")) {
            return StandardField.LATITUDE;
        } else if (h.equals("longitude")) {
            return StandardField.LONGITUDE;
        }
        return StandardField.NONE;
    }

    private static void applyStandardField(Item item, StandardField field, String value) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        switch (field) {
            case DATE:
                setIfAbsent(item, ExtraProperties.MESSAGE_DATE, value);
                break;
            case FROM:
                setIfAbsent(item, Message.MESSAGE_FROM, value);
                break;
            case TO:
                setIfAbsent(item, Message.MESSAGE_TO, value);
                break;
            case BODY:
                setIfAbsent(item, ExtraProperties.MESSAGE_BODY, value);
                break;
            default:
                break;
        }
    }

    private static void setLocationIfValid(Item item, String lat, String lon) {
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
            item.getMetadata().set(ExtraProperties.LOCATIONS, lat + ";" + lon);
        } catch (NumberFormatException e) {
            // non-numeric coordinate values: leave them as aleapp:* metadata only
        }
    }

    private static void setIfAbsent(Item item, Property key, String value) {
        if (item.getMetadata().get(key) == null) {
            item.getMetadata().set(key, value);
        }
    }

    private static void setIfAbsent(Item item, String key, String value) {
        if (item.getMetadata().get(key) == null) {
            item.getMetadata().set(key, value);
        }
    }

    /**
     * Derives the subitem media type. A module can register many plugins, so the plugin name (often prefixed with
     * "get_", which is stripped) plus hints from the artifact name ("Call", "Chat", ...) are used to build a specific
     * x-aleapp-* type.
     */
    private MediaType resolveMediaType(String artifactName, String pluginName) {

        String mimePluginName = pluginName.toLowerCase().replace(".", "");
        mimePluginName = StringUtils.removeStart(mimePluginName, "get_");

        // Facebook plugins share generic plugin names: the artifact name prefix (before "- ")
        // is more specific, so use it instead
        if (StringUtils.containsIgnoreCase(mimePluginName, "facebook")) {
            mimePluginName = StringUtils.substringBefore(artifactName, "- ").toLowerCase();
        }

        // Chrome plugins are named per artifact already, so the artifact name alone is used
        // (mimePluginName is intentionally ignored in this branch)
        if (StringUtils.containsIgnoreCase(pluginName, "chrome")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + artifactNameToType(artifactName));
        } else if (StringUtils.containsIgnoreCase(artifactName, "Call")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-call");
        } else if (StringUtils.containsIgnoreCase(artifactName, "Chat")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-chat");
        } else if (StringUtils.containsIgnoreCase(artifactName, "Message")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-message");
        } else if (StringUtils.containsAnyIgnoreCase(artifactName, "Activity", "Activities")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-activity");
        } else if (StringUtils.containsIgnoreCase(artifactName, "Contact")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-contact");
        } else if (StringUtils.containsIgnoreCase(artifactName, "Conversation")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-conversation");
        } else if (StringUtils.containsIgnoreCase(artifactName, "Autofill")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-autofill");
        } else {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + artifactNameToType(artifactName));
        }
    }

    private String artifactNameToType(String artifactName) {
        String type = StringUtils.substringBefore(artifactName, " (");
        type = type.replace(" - ", "-").replace(" ", "-").replace("--", "-");
        return type;
    }
}
