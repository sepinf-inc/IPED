package iped.engine.task.leapp;

import java.util.Collections;
import java.util.Map;

import org.apache.tika.mime.MediaType;

/**
 * Describes one of the LEAPP tools (ALEAPP, iLEAPP...) to the code shared by all of them.
 *
 * <p>
 * The LEAPP tools share the same plugin architecture (plugin loader, artifact_processor, Context, seekers, LAVA
 * output), so most of the integration is tool-agnostic and lives in this package. What differs from one tool to the
 * other is gathered here: the names used in the case (media types and metadata prefix) and the knowledge about the
 * platform the tool decodes (how device paths map to the extraction tree, plugin-specific exceptions).
 */
public abstract class LeappTool {

    // subtypes of the structural items every tool creates
    public static final String CASE_SUBTYPE = "case";
    public static final String CATEGORY_SUBTYPE = "category";
    public static final String PLUGIN_RESULTS_SUBTYPE = "plugin-results";
    public static final String DEVICE_INFO_SUBTYPE = "deviceinfo";
    public static final String CHAT_PREVIEW_SUBTYPE = "chat-preview";

    private final String id;
    private final String displayName;

    /**
     * @param id lower case identifier, used to build media types ("application/x-&lt;id&gt;-...") and metadata keys
     *        ("&lt;id&gt;:...")
     * @param displayName the tool name, as shown to the user
     */
    protected LeappTool(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Prefix of the metadata keys holding plugin data, e.g. "aleapp:". */
    public String getMetaPrefix() {
        return id + ":";
    }

    /** Prefix of the metadata keys describing the plugin itself, e.g. "aleapp:plugin:". */
    public String getPluginMetaPrefix() {
        return getMetaPrefix() + "plugin:";
    }

    /** Prefix of the subtype of every media type of this tool, e.g. "x-aleapp-". */
    public String getMimeSubtypePrefix() {
        return "x-" + id + "-";
    }

    public MediaType mediaType(String subtype) {
        return MediaType.application(getMimeSubtypePrefix() + subtype);
    }

    /** Whether the media type belongs to an item created by this tool. */
    public boolean owns(MediaType mediaType) {
        return mediaType != null && "application".equals(mediaType.getType())
                && mediaType.getSubtype().startsWith(getMimeSubtypePrefix());
    }

    /** Name of the item holding all the results of this tool for one extraction. */
    public String getResultsItemName() {
        return displayName + "_Results";
    }

    /** The oldest python version the tool runs on, as {major, minor}. */
    public abstract int[] getMinPythonVersion();

    /**
     * Returns the media type of the row subitems of a plugin. It is how these items get a category (see
     * CategoriesConfig.json).
     *
     * @param moduleName the plugin module (python file stem)
     * @param pluginName the artifact key
     * @param artifactName the artifact display name
     * @param conversationRows whether the rows are the messages of a conversation view
     */
    public abstract MediaType resolveRowMediaType(String moduleName, String pluginName, String artifactName,
            boolean conversationRows);

    /**
     * Device paths that are just another name for a location of the extraction tree (symbolic links, bind mounts),
     * used to locate the case item a path stored by an app refers to. Each entry maps an app-visible prefix to the
     * location it resolves to. More specific prefixes must come first.
     */
    public Map<String, String> getPathAliases() {
        return Collections.emptyMap();
    }

    /**
     * Whether a column that looks like a file path by its name does NOT hold a file system path in the given plugin,
     * so no file lookup must be attempted for it.
     */
    public boolean isNonFilePathColumn(String moduleName, String headerName) {
        return false;
    }
}
