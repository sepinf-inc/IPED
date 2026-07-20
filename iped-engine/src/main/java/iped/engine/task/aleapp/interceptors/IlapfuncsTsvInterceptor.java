package iped.engine.task.aleapp.interceptors;

import static iped.engine.task.aleapp.AleappTask.ALEAPP_APPLICATION_PREFIX;

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
import iped.engine.task.aleapp.AleappTask;
import iped.engine.task.aleapp.AleappTask.State;
import iped.engine.task.aleapp.AleappUtils;
import iped.engine.task.aleapp.CallInterceptor;
import iped.engine.task.aleapp.FileSeeker;
import iped.properties.ExtraProperties;
import jep.PyMethod;

/**
 * Replace the ilapfuncs.tsv function
 */
public class IlapfuncsTsvInterceptor extends CallInterceptor {

    protected static final Logger logger = LoggerFactory.getLogger(IlapfuncsTsvInterceptor.class);

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
        String sourceFile = (String) getArgumentValue("source_file", 4, args, kwargs);

        if (dataList.isEmpty()) {
            return null;
        }

        State state = AleappTask.getState();

        // set hasChildren, so plugin will not be ignored in AleappTask.processPluginEvidence()
        // and category will not be ignored in AleappTask.processCategoryEvidence()
        state.getPluginItem().setHasChildren(true);
        IItem categoryItem = (IItem) state.getPluginItem().getTempAttribute(AleappTask.ALEAPP_PLUGIN_CATEGORY_KEY);
        categoryItem.setHasChildren(true);

        // linkedItems
        Set<String> globalIds = new HashSet<>();
        if (sourceFile != null) {
            IItemReader sourceFileItem = AleappUtils.findItemByPath(state.getCaseData(), sourceFile);
            if (sourceFileItem != null) {
                globalIds.add((String) sourceFileItem.getExtraAttribute(ExtraProperties.GLOBAL_ID));
            }
        }
        for (IItemReader foundFile : state.getFoundFiles()) {
            globalIds.add((String) foundFile.getExtraAttribute(ExtraProperties.GLOBAL_ID));
        }
        String likedItems = ExtraProperties.GLOBAL_ID + ":(" + String.join(" ", globalIds) + ")";
        state.getPluginItem().getMetadata().add(ExtraProperties.LINKED_ITEMS, likedItems);


        // media type
        String pluginName = state.getPluginItem().getMetadata().get(AleappTask.ALEAPP_PLUGIN_KEYNAME_META);
        MediaType mediaType = resolveMediaType(tsvName, pluginName);

        // headers are constant across rows: classify each column once
        StandardField[] standardFields = new StandardField[dataHeaders.size()];
        for (int i = 0; i < standardFields.length; i++) {
            standardFields[i] = classifyHeader(dataHeaders.get(i));
        }

        // create subItems
        for (int index = 0; index < dataList.size(); index++) {

            String subItemName = tsvName + "-" + index;
            Item subItem = (Item) state.getPluginItem().createChildItem();
            subItem.setMediaType(mediaType);
            subItem.setName(subItemName);
            subItem.setExtension("");
            subItem.setPath(state.getPluginItem().getPath() + "/" + subItemName);
            subItem.setExtraAttribute(ExtraProperties.DECODED_DATA, true);
            subItem.setSubItem(true);
            subItem.setSubitemId(index);

            // data as metadata
            List<Object> data = dataList.get(index);
            String lat = null, lon = null;
            for (int i = 0; i < dataHeaders.size(); i++) {
                Object value = cellValue(data, i);
                if (value != null) {
                    String header = dataHeaders.get(i);
                    String valueStr = value.toString();
                    if (state.getTranslatedPaths().containsKey(valueStr)) {
                        valueStr = state.getTranslatedPaths().get(valueStr);
                    }
                    if (FileSeeker.isIPEDPath(valueStr)) {
                        valueStr = FileSeeker.getItemPath(valueStr);
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

            state.getWorker().processNewItem(subItem, ProcessTime.LATER);
        }

        return null;
    }

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

    private MediaType resolveMediaType(String tsvName, String pluginModuleName) {

        String mimePluginName = pluginModuleName.toLowerCase().replace(".", "");

        if (StringUtils.containsIgnoreCase(mimePluginName, "facebook")) {
            mimePluginName = StringUtils.substringBefore(tsvName, "- ").toLowerCase();
        }

        if (StringUtils.containsIgnoreCase(pluginModuleName, "chrome")) {
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
