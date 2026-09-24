package iped.engine.task.leapp.conversation;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * The "conversation" data view a plugin declares in the "data_views" field of its __artifacts_v2__ block, e.g.:
 *
 * <pre>
 * "data_views": {
 *     "conversation": {
 *         "conversationDiscriminatorColumn": "Channel ID",
 *         "textColumn": "Content",
 *         "directionColumn": "Direction",
 *         "directionSentValue": "Outgoing",
 *         "timeColumn": "Timestamp",
 *         "senderColumn": "Username"
 *     }
 * }
 * </pre>
 *
 * In ALEAPP this declaration is only copied into the LAVA JSON by lava_process_artifact (the rendering happens in the
 * LAVA viewer app), so there is no need to enable that function: the raw dict is read here straight from the plugin's
 * artifact_info, and the column names match the raw data_headers names received by PluginResultsProcessor.
 *
 * The backward compatibility rules of lava_process_artifact are mirrored: a "chat" view is upgraded to "conversation"
 * and thread*Column keys are remapped to conversation*Column.
 *
 * https://github.com/abrignoni/ALEAPP/blob/v2026.1.0/scripts/lavafuncs.py#L253
 */
public class ConversationViewSpec {

    public static final String DATA_VIEWS_KEY = "data_views";

    private final String discriminatorColumn;
    private final String labelColumn;
    private final String textColumn;
    private final String directionColumn;
    private final String directionSentValue;
    private final String timeColumn;
    private final String senderColumn;

    private ConversationViewSpec(Map<?, ?> params) {
        this.discriminatorColumn = str(params, "conversationDiscriminatorColumn", "threadDiscriminatorColumn");
        this.labelColumn = str(params, "conversationLabelColumn", "threadLabelColumn");
        this.textColumn = str(params, "textColumn");
        this.directionColumn = str(params, "directionColumn");
        this.directionSentValue = str(params, "directionSentValue");
        this.timeColumn = str(params, "timeColumn");
        this.senderColumn = str(params, "senderColumn");
    }

    /**
     * Returns the conversation view declared in the given artifact_info, or null if there is none usable (a view
     * without a discriminator column cannot group rows into conversations).
     */
    public static ConversationViewSpec from(Map<String, Object> artifactInfo) {

        Object dataViews = artifactInfo.get(DATA_VIEWS_KEY);
        if (!(dataViews instanceof Map)) {
            return null;
        }

        Object view = ((Map<?, ?>) dataViews).get("conversation");
        if (!(view instanceof Map)) {
            // backward compatibility, mirroring lava_process_artifact
            view = ((Map<?, ?>) dataViews).get("chat");
        }
        if (!(view instanceof Map)) {
            return null;
        }

        ConversationViewSpec spec = new ConversationViewSpec((Map<?, ?>) view);
        return spec.discriminatorColumn != null ? spec : null;
    }

    private static String str(Map<?, ?> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    public String getDiscriminatorColumn() {
        return discriminatorColumn;
    }

    public String getLabelColumn() {
        return labelColumn;
    }

    public String getTextColumn() {
        return textColumn;
    }

    public String getDirectionColumn() {
        return directionColumn;
    }

    public String getDirectionSentValue() {
        return directionSentValue;
    }

    public String getTimeColumn() {
        return timeColumn;
    }

    public String getSenderColumn() {
        return senderColumn;
    }
}
