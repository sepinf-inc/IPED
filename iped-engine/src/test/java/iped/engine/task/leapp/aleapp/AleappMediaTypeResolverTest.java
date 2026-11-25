package iped.engine.task.leapp.aleapp;

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

    // ── calibration against ALEAPP v2026.4.5 artifact names ─────────────────────

    @Test
    public void testAppNameIsNotTakenAsTheKindOfRecord() {
        // "Snapchat", "ChatGPT" and "Truecaller" hold the words chat/call, but these artifacts are neither
        assertEquals("application/x-aleapp-snapchat-friends", mime("snapchat", "snapchat_friends", "Snapchat - Friends"));
        assertEquals("application/x-aleapp-chatgpt-user-settings", mime("chatgpt", "chatgpt_user_settings", "ChatGPT - User Settings"));
        assertEquals("application/x-aleapp-truecaller_contacts-contact", mime("truecaller", "truecaller_contacts", "Truecaller - Contacts"));
        assertEquals("application/x-aleapp-truecaller_calls-call", mime("truecaller", "truecaller_calls", "Truecaller - Call History"));
        assertEquals("application/x-aleapp-randochat_messages-message", mime("randochat", "randochat_messages", "RandoChat Messages"));
    }

    @Test
    public void testArtifactsAboutTheRecordsAreNotTheRecords() {
        assertEquals("application/x-aleapp-kik-chat-metadata", mime("kikMessenger", "kik_chat_metadata", "Kik Chat Metadata"));
        assertEquals("application/x-aleapp-whatsapp-message-reactions", mime("WhatsApp", "wa_reactions", "WhatsApp - Message Reactions"));
        assertEquals("application/x-aleapp-facebook-contact-sync-state", mime("fb", "fb_sync", "Facebook - Contact Sync State"));
    }

    @Test
    public void testCamelCaseArtifactNames() {
        assertEquals("application/x-aleapp-googlemessages-message", mime("googleMessages", "get_googleMessages", "GoogleMessages"));
    }

    @Test
    public void testMessagesOfEmailAppsAreEmails() {
        assertEquals("application/x-aleapp-protonmail_messages-email", mime("protonmail", "protonmail_messages", "ProtonMail - Messages"));
        assertEquals("application/x-aleapp-k9_messages-email", mime("k9Mail", "k9_messages", "K-9 Mail - Messages"));
        assertEquals("application/x-aleapp-signal_messages-message", mime("signal", "signal_messages", "Signal - Messages"));
    }

    @Test
    public void testRowsOfConversationViewsAreMessages() {
        assertEquals("application/x-aleapp-snapchat_arroyo-message", AleappMediaTypeResolver
                .resolveMediaType("snapchat", "snapchat_arroyo", "Snapchat - Conversations (arroyo.db)", true).toString());
    }
}
