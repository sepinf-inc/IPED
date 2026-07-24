package iped.engine.task.leapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AleappMediaTypeResolverTest {

    private String mime(String module, String pluginKey, String artifactName) {
        return AleappMediaTypeResolver.resolveMediaType(module, pluginKey, artifactName).toString();
    }

    // ── plugin-specific subtypes (drive the category via CategoriesConfig.json) ──

    @Test
    public void testFcmQueuedPluginsShareNotificationType() {
        assertEquals("application/x-aleapp-notification",
                mime("FCMQueuedMessagesTwitter", "get_fcm_twitter", "FCM Queued Messages - Twitter"));
        assertEquals("application/x-aleapp-notification",
                mime("FCMQueuedMessageKik", "get_fcm_kik", "FCM Queued Messages - Kik"));
    }

    @Test
    public void testAccountsPlugins() {
        assertEquals("application/x-aleapp-account", mime("accounts_de", "get_accounts_de", "Accounts_de"));
        assertEquals("application/x-aleapp-account", mime("accounts_ce", "get_accounts_ce", "Accounts_ce"));
        assertEquals("application/x-aleapp-account-authtoken",
                mime("accounts_ce", "get_accounts_ce_authtokens", "Authentication tokens"));
    }

    @Test
    public void testSingleModulePlugins() {
        assertEquals("application/x-aleapp-siminfo", mime("siminfo", "get_siminfo", "Sim Info"));
        assertEquals("application/x-aleapp-gdrive-file-entry", mime("Cello", "get_cello", "Cello"));
        assertEquals("application/x-aleapp-app-role", mime("roles", "get_roles", "Roles"));
        assertEquals("application/x-aleapp-update-info", mime("frosting", "get_frosting", "Frosting"));
    }

    @Test
    public void testGmailOnlyAppEmailsBecomesEmail() {
        assertEquals("application/x-aleapp-email", mime("gmailEmails", "get_gmail_app_emails", "Gmail - App Emails"));
        // other gmail artifacts fall back to the generic subtype
        assertEquals("application/x-aleapp-gmail-label-details",
                mime("gmailEmails", "get_gmail_labels", "Gmail - Label Details"));
    }

    @Test
    public void testFacebookMessengerContactsAndUser() {
        assertEquals("application/x-aleapp-facebook-contact",
                mime("FacebookMessenger", "get_fb_msys_contacts", "Facebook Messenger - Contacts"));
        assertEquals("application/x-aleapp-facebook-account",
                mime("FacebookMessenger", "get_fb_user_id", "Facebook Messenger - User"));
    }

    // ── generic derivation (unchanged behaviour) ────────────────────────────────

    @Test
    public void testChromePluginsUseArtifactName() {
        assertEquals("application/x-aleapp-cookies", mime("chromeCookies", "get_chromeCookies", "Cookies"));
        assertEquals("application/x-aleapp-offline-pages",
                mime("chromeOfflinePages", "get_chromeOfflinePages", "Offline Pages"));
    }

    @Test
    public void testArtifactNameHintSuffixes() {
        assertEquals("application/x-aleapp-whatsapp-call", mime("WhatsApp", "get_whatsapp", "WhatsApp Calls"));
        assertEquals("application/x-aleapp-someapp-message", mime("someapp", "get_someapp", "App Messages"));
    }

    @Test
    public void testGenericFallbackUsesArtifactName() {
        assertEquals("application/x-aleapp-app-usage", mime("wellbeing", "get_wellbeing", "App Usage"));
    }
}
