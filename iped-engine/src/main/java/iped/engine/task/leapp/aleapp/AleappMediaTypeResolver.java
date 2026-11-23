package iped.engine.task.leapp.aleapp;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MediaType;

/**
 * Gives each ALEAPP plugin row subitem its media type.
 *
 * <p>
 * The media type is how these items get a category: they are not categorized by code, only by the mime &rarr; category
 * map in CategoriesConfig.json.
 *
 * <p>
 * The type is built in one of two ways:
 * <ul>
 * <li>{@link #pluginSpecificSubtype}: a few plugins get a fixed type so all their rows share one category (e.g. every
 * FCMQueued* plugin &rarr; {@code x-aleapp-notification});
 * <li>{@link #genericSubtype}: any other plugin gets a type built from its artifact name.
 * </ul>
 *
 * <p>
 * A category that depends on a cell value (e.g. "settingsSecure Name = bluetooth_address") cannot be set here: the type
 * is decided once per plugin run, not per row.
 */
public final class AleappMediaTypeResolver {

    private AleappMediaTypeResolver() {
    }

    /**
     * Returns the media type for a row: the plugin-specific type when there is one, otherwise the generic type.
     *
     * @param moduleName the plugin module (python file stem, e.g. "chromeCookies")
     * @param pluginName the artifact key (e.g. "get_fb_user_id")
     * @param artifactName the artifact display name (e.g. "Gmail - App Emails")
     */
    public static MediaType resolveMediaType(String moduleName, String pluginName, String artifactName) {
        return resolveMediaType(moduleName, pluginName, artifactName, false);
    }

    /**
     * @param conversationRows whether the rows are the messages of a conversation view (see ConversationViewSpec)
     */
    public static MediaType resolveMediaType(String moduleName, String pluginName, String artifactName,
            boolean conversationRows) {
        String subtype = pluginSpecificSubtype(moduleName, pluginName, artifactName);
        if (subtype == null) {
            subtype = genericSubtype(moduleName, pluginName, artifactName, conversationRows);
        }
        return AleappTool.INSTANCE.mediaType(subtype);
    }

    /**
     * Stable subtypes for plugins whose forensic category is driven by identity (module or artifact), so that
     * CategoriesConfig.json can map the whole group with a single mime. Returns null when no specific rule applies.
     */
    private static String pluginSpecificSubtype(String moduleName, String pluginName, String artifactName) {

        if (moduleName.startsWith("FCMQueued")) {
            return "notification";
        }
        if (moduleName.equals("accounts_de")) {
            return "account";
        }
        if (moduleName.equals("accounts_ce")) {
            // accounts_ce.py declares two artifacts: "Accounts_ce" and "Authentication tokens"
            return "Authentication tokens".equals(artifactName) ? "account-authtoken" : "account";
        }
        if (moduleName.equals("siminfo")) {
            return "siminfo";
        }
        if (moduleName.equals("Cello")) {
            return "gdrive-file-entry";
        }
        if (moduleName.equals("roles")) {
            return "app-role";
        }
        if (moduleName.equals("frosting")) {
            return "update-info";
        }
        if (moduleName.equals("gmailEmails") && "Gmail - App Emails".equals(artifactName)) {
            return "email";
        }
        if (moduleName.equals("FacebookMessenger")) {
            // artifact keys: get_fb_*_contacts, get_fb_user_id, get_fb_*_chats (chats are chat previews)
            if (StringUtils.containsIgnoreCase(pluginName, "contacts")) {
                return "facebook-contact";
            }
            if (StringUtils.containsIgnoreCase(pluginName, "user_id")) {
                return "facebook-account";
            }
        }
        return null;
    }

    // Words telling that an artifact named after calls, chats, messages or contacts does not hold the records
    // themselves, but something about them (e.g. "Kik Chat Metadata", "WhatsApp - Message Reactions", "Truecaller -
    // Call Cache"), so it must not get their category.
    private static final Pattern NOT_THE_RECORDS = Pattern.compile(
            "\\b(metadata|info|information|details?|settings?|preferences|status|state|counts?|telemetry|accounts?"
                    + "|users?|members|participants|rooms|groups|drafts|hints?|reactions|receipts|edits?|records"
                    + "|transcriptions?|screen|cache|media|files?|store|keys?|bundles|objects)\\b");

    private static final Pattern CONTACT = Pattern.compile("\\bcontacts?\\b");
    private static final Pattern CALL = Pattern.compile("\\bcalls?\\b");
    private static final Pattern MESSAGE = Pattern.compile("\\b(messages?|e-?mails?)\\b");
    private static final Pattern CHAT = Pattern.compile("\\bchats?\\b");
    private static final Pattern CONVERSATION = Pattern.compile("\\bconversations?\\b");
    private static final Pattern ACTIVITY = Pattern.compile("\\bactivit(y|ies)\\b");
    private static final Pattern AUTOFILL = Pattern.compile("\\bautofill\\b");

    // apps whose messages are emails
    private static final Pattern EMAIL_APP = Pattern.compile("mail|thunderbird");

    /**
     * Generic per-artifact subtype. A module can register many plugins, so the plugin name (often prefixed with "get_",
     * which is stripped) plus a hint about what the artifact holds ("-call", "-chat", "-message"...) are used to build
     * a specific subtype. RefineCategoryTask.js gives the category from that hint.
     *
     * The hint is taken from whole words of the artifact name, ignoring the app name: matching substrings would give
     * every "Snapchat - ..." or "ChatGPT - ..." artifact the chat category, and the "Truecaller - ..." ones the call
     * category.
     *
     * @param conversationRows whether the rows are the messages of a conversation view, whatever the artifact is named
     */
    private static String genericSubtype(String moduleName, String pluginName, String artifactName, boolean conversationRows) {

        String mimePluginName = pluginName.toLowerCase().replace(".", "");
        mimePluginName = StringUtils.removeStart(mimePluginName, "get_");

        // Facebook plugins share generic plugin names: the artifact name prefix (before "- ")
        // is more specific, so use it instead
        if (StringUtils.containsIgnoreCase(mimePluginName, "facebook")) {
            mimePluginName = StringUtils.substringBefore(artifactName, "- ").toLowerCase();
        }

        // the app name is what comes before " - ", when the artifact is named "<app> - <what it holds>"
        String app = StringUtils.contains(artifactName, " - ") ? StringUtils.substringBefore(artifactName, " - ") : moduleName;
        boolean emailApp = EMAIL_APP.matcher(StringUtils.lowerCase(app + " " + moduleName)).find();

        if (conversationRows) {
            return mimePluginName + (emailApp ? "-email" : "-message");
        }

        // Chrome plugins are named per artifact already, so the artifact name alone is used
        // (mimePluginName is intentionally ignored in this branch)
        if (StringUtils.containsIgnoreCase(pluginName, "chrome")) {
            return artifactNameToType(artifactName);
        }

        // what the artifact holds: its name without the app name and the trailing "(...)" note; names
        // written in camel case (e.g. "GoogleMessages") are split in words
        String kind = StringUtils.substringBefore(StringUtils.defaultIfBlank(StringUtils.substringAfter(artifactName, " - "), artifactName), " (");
        if (!StringUtils.containsWhitespace(kind.trim())) {
            kind = StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(kind), ' ');
        }
        kind = kind.toLowerCase();

        if (!NOT_THE_RECORDS.matcher(kind).find()) {
            if (CONTACT.matcher(kind).find()) {
                return mimePluginName + "-contact";
            } else if (CALL.matcher(kind).find()) {
                return mimePluginName + "-call";
            } else if (MESSAGE.matcher(kind).find()) {
                return mimePluginName + (emailApp ? "-email" : "-message");
            } else if (CHAT.matcher(kind).find()) {
                return mimePluginName + "-chat";
            } else if (CONVERSATION.matcher(kind).find()) {
                return mimePluginName + "-conversation";
            }
        }
        if (ACTIVITY.matcher(kind).find()) {
            return mimePluginName + "-activity";
        } else if (AUTOFILL.matcher(kind).find()) {
            return mimePluginName + "-autofill";
        }
        return artifactNameToType(artifactName);
    }

    private static String artifactNameToType(String artifactName) {
        String type = StringUtils.substringBefore(artifactName, " (");
        type = type.replace(" - ", "-").replace(" ", "-").replace("--", "-");
        return type;
    }
}
