package iped.engine.task.leapp;

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
        String subtype = pluginSpecificSubtype(moduleName, pluginName, artifactName);
        if (subtype == null) {
            subtype = genericSubtype(pluginName, artifactName);
        }
        return MediaType.application(AleappTask.ALEAPP_APPLICATION_PREFIX + subtype);
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

    /**
     * Generic per-artifact subtype. A module can register many plugins, so the plugin name (often prefixed with "get_",
     * which is stripped) plus hints from the artifact name ("Call", "Chat", ...) are used to build a specific subtype.
     */
    private static String genericSubtype(String pluginName, String artifactName) {

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
            return artifactNameToType(artifactName);
        } else if (StringUtils.containsIgnoreCase(artifactName, "Call")) {
            return mimePluginName + "-call";
        } else if (StringUtils.containsIgnoreCase(artifactName, "Chat")) {
            return mimePluginName + "-chat";
        } else if (StringUtils.containsIgnoreCase(artifactName, "Message")) {
            return mimePluginName + "-message";
        } else if (StringUtils.containsAnyIgnoreCase(artifactName, "Activity", "Activities")) {
            return mimePluginName + "-activity";
        } else if (StringUtils.containsIgnoreCase(artifactName, "Contact")) {
            return mimePluginName + "-contact";
        } else if (StringUtils.containsIgnoreCase(artifactName, "Conversation")) {
            return mimePluginName + "-conversation";
        } else if (StringUtils.containsIgnoreCase(artifactName, "Autofill")) {
            return mimePluginName + "-autofill";
        } else {
            return artifactNameToType(artifactName);
        }
    }

    private static String artifactNameToType(String artifactName) {
        String type = StringUtils.substringBefore(artifactName, " (");
        type = type.replace(" - ", "-").replace(" ", "-").replace("--", "-");
        return type;
    }
}
