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
import iped.engine.task.leapp.LeappUtils;
import iped.engine.task.leapp.CallInterceptor;
import iped.engine.task.leapp.LeappContext;
import iped.properties.ExtraProperties;
import jep.PyMethod;

/**
 * Replaces the ilapfuncs.tsv function. This is the main IPED-ALEAPP integration point: instead of letting the plugin
 * write a TSV report file, each data row is turned into an IPED subitem of the current plugin evidence, with the TSV
 * columns mapped to metadata (and, when recognized, to IPED standard properties like message date/from/to/body and
 * geolocation).
 */
public class IlapfuncsTsvInterceptor extends CallInterceptor {

    protected static final Logger logger = LoggerFactory.getLogger(IlapfuncsTsvInterceptor.class);

    // Date columns vary a lot across ALeapp plugins ("Timestamp", "Start Time",
    // "Created Timestamp", "Last Updated", ...), so dates use exact names plus a
    // suffix family, calibrated against ALEAPP v2026.1.0 sources. Sender,
    // recipient and body use exact matches only: mislabeling those is
    // forensically costly (e.g. "Account" is usually the device owner, who is
    // the receiver of incoming records, not the sender). "Date of Birth"-style
    // personal dates must not become the record's event date, so there is no
    // "date " prefix rule — "date *" event columns are listed explicitly.
    private static final Set<String> DATE_HEADERS = Set.of( //
            "datetime", "date/time", "date", "created", "created at", "updated at", //
            "time created", "last updated", "last login", "last modified", "last access", "last accessed", //
            "date added", "date created", "date modified", "date sent", "date taken");
    private static final Set<String> FROM_HEADERS = Set.of("sender", "from", "author");
    private static final Set<String> TO_HEADERS = Set.of("recipient", "to", "receiver");
    private static final Set<String> BODY_HEADERS = Set.of("message", "body", "text", "content");

    private enum StandardField {
        DATE, FROM, TO, BODY, LATITUDE, LONGITUDE, NONE
    }

    public IlapfuncsTsvInterceptor() {
        super("scripts.ilapfuncs", "scripts.ilapfuncs.tsv");
    }

    @SuppressWarnings("unchecked")
    @Override
    @PyMethod(varargs = true, kwargs = true)
    public Object call(Object[] args, Map<String, Object> kwargs) throws Exception {

        // get params from arguments: tsv(report_folder, data_headers, data_list, tsvname, source_file=None):
        List<String> dataHeaders = (List<String>) getArgumentValue("data_headers", 1, args, kwargs);
        List<List<Object>> dataList = (List<List<Object>>) getArgumentValue("data_list", 2, args, kwargs);
        String tsvName = (String) getArgumentValue("tsvname", 3, args, kwargs);
        String sourceFiles = (String) getArgumentValue("source_file", 4, args, kwargs);

        if (dataList.isEmpty()) {
            return null;
        }

        // the interceptor is installed globally in the Python interpreter, so the
        // thread-local context tells us which plugin run this tsv() call belongs to
        LeappContext context = LeappContext.get();

        // set hasChildren, so plugin will not be ignored in AleappTask.processPluginEvidence()
        // and category will not be ignored in AleappTask.processCategoryEvidence()
        context.getPluginItem().setHasChildren(true);
        IItem categoryItem = (IItem) context.getPluginItem().getTempAttribute(AleappTask.ALEAPP_PLUGIN_CATEGORY_KEY);
        categoryItem.setHasChildren(true);

        addLinkedItems(context, sourceFiles);

        String pluginName = context.getPluginItem().getMetadata().get(AleappTask.ALEAPP_PLUGIN_KEYNAME_META);
        MediaType mediaType = resolveMediaType(tsvName, pluginName);

        // headers are constant across rows: classify each column once
        StandardField[] standardFields = new StandardField[dataHeaders.size()];
        for (int i = 0; i < standardFields.length; i++) {
            standardFields[i] = classifyHeader(dataHeaders.get(i));
        }

        for (int index = 0; index < dataList.size(); index++) {
            Item subItem = createSubItem(context, mediaType, tsvName, index, dataHeaders, standardFields, dataList.get(index));
            context.getWorker().processNewItem(subItem, ProcessTime.LATER);
        }

        return null;
    }

    /**
     * Links the plugin evidence to the items the data came from: the files reported by the plugin via source_file plus
     * all files found by the seeker for this plugin.
     */
    private void addLinkedItems(LeappContext context, String sourceFiles) {
        Set<String> globalIds = new HashSet<>();
        if (sourceFiles != null) {
            for (String sourceFile : sourceFiles.split(", ")) {
                IItemReader sourceFileItem = LeappUtils.findItemByPath(context.getFileSeeker().getSearcher(), sourceFile);
                if (sourceFileItem != null) {
                    globalIds.add((String) sourceFileItem.getExtraAttribute(ExtraProperties.GLOBAL_ID));
                }
            }
        }
        for (IItemReader foundFile : context.getFoundFiles()) {
            globalIds.add((String) foundFile.getExtraAttribute(ExtraProperties.GLOBAL_ID));
        }
        String linkedItems = ExtraProperties.GLOBAL_ID + ":(" + String.join(" ", globalIds) + ")";
        context.getPluginItem().getMetadata().add(ExtraProperties.LINKED_ITEMS, linkedItems);
    }

    /**
     * Creates one subitem for a TSV data row, storing each cell as "aleapp:&lt;header&gt;" metadata and mapping
     * recognized columns to IPED standard properties.
     */
    private Item createSubItem(LeappContext context, MediaType mediaType, String tsvName, int index,
            List<String> dataHeaders, StandardField[] standardFields, List<Object> data) {

        String subItemName = tsvName + "-" + index;
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
        for (int i = 0; i < dataHeaders.size(); i++) {
            Object value = cellValue(data, i);
            if (value != null) {
                String header = dataHeaders.get(i);
                String valueStr = value.toString();
                // cells holding a path exported by the seeker are rewritten back to the
                // original in-case path and linked to the original item
                if (context.getFileSeeker().getExportedFiles().containsKey(valueStr)) {
                    IItemReader valueItem = context.getFileSeeker().getExportedFiles().get(valueStr);
                    valueStr = StringUtils.removeStart(valueItem.getPath(), context.getFileSeeker().getPathRoot());
                    subItem.getMetadata().add(ExtraProperties.LINKED_ITEMS, ExtraProperties.GLOBAL_ID + ":" + valueItem.getExtraAttribute(ExtraProperties.GLOBAL_ID));
                }
                subItem.getMetadata().set("aleapp:" + header, valueStr);
                if (standardFields[i] == StandardField.LATITUDE) {
                    lat = valueStr;
                } else if (standardFields[i] == StandardField.LONGITUDE) {
                    lon = valueStr;
                } else if (standardFields[i] != StandardField.NONE) {
                    applyStandardField(subItem, standardFields[i], valueStr);
                }
            }
        }
        setLocationIfValid(subItem, lat, lon);

        return subItem;
    }

    private static Object cellValue(List<Object> data, int i) {
        return (i < data.size()) ? data.get(i) : null;
    }

    private static boolean isDateHeader(String h) {
        return DATE_HEADERS.contains(h) || h.contains("timestamp") //
                || h.endsWith(" time") || h.endsWith(" date") || h.endsWith("_date");
    }

    private static StandardField classifyHeader(String header) {
        String h = header.toLowerCase(Locale.ROOT).trim();
        if (isDateHeader(h)) {
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
     * "get_", which is stripped) plus hints from the tsv report name ("Call", "Chat", ...) are used to build a specific
     * x-aleapp-* type.
     */
    private MediaType resolveMediaType(String tsvName, String pluginName) {

        String mimePluginName = pluginName.toLowerCase().replace(".", "");
        mimePluginName = StringUtils.removeStart(mimePluginName, "get_");

        // Facebook plugins share generic plugin names: the tsv name prefix (before "- ")
        // is more specific, so use it instead
        if (StringUtils.containsIgnoreCase(mimePluginName, "facebook")) {
            mimePluginName = StringUtils.substringBefore(tsvName, "- ").toLowerCase();
        }

        // Chrome plugins are named per artifact already, so the tsv name alone is used
        // (mimePluginName is intentionally ignored in this branch)
        if (StringUtils.containsIgnoreCase(pluginName, "chrome")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + tsvNameToType(tsvName));
        } else if (StringUtils.containsIgnoreCase(tsvName, "Call")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-call");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Chat")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-chat");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Message")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-message");
        } else if (StringUtils.containsAnyIgnoreCase(tsvName, "Activity", "Activities")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-activity");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Contact")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-contact");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Conversation")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-conversation");
        } else if (StringUtils.containsIgnoreCase(tsvName, "Autofill")) {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + mimePluginName + "-autofill");
        } else {
            return MediaType.application(ALEAPP_APPLICATION_PREFIX + tsvNameToType(tsvName));
        }
    }

    private String tsvNameToType(String tsvName) {
        String type = StringUtils.substringBefore(tsvName, " (");
        type = type.replace(" - ", "-").replace(" ", "-").replace("--", "-");
        return type;
    }
}
