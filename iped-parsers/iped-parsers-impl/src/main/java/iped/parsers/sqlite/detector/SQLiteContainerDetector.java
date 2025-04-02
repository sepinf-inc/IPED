package iped.parsers.sqlite.detector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import iped.parsers.browsers.chrome.ChromeSqliteParser;
import iped.parsers.browsers.firefox.FirefoxSqliteParser;
import iped.parsers.browsers.safari.SafariSqliteParser;
import iped.parsers.eventtranscript.EventTranscriptParser;
import iped.parsers.gdrive.GDriveMainParser;
import iped.parsers.skype.SkypeParser;
import iped.parsers.telegram.TelegramParser;
import iped.parsers.threema.ThreemaParser;
import iped.parsers.whatsapp.WhatsAppParser;
import iped.parsers.winx.WinXTimelineParser;
import iped.utils.IOUtil;

/**
 * Detects subtypes of SQLite based on table names.
 * 
 * @author Nassif
 *
 */
public class SQLiteContainerDetector implements Detector {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final MediaType SQLITE_MIME = MediaType.application("x-sqlite3"); //$NON-NLS-1$

    private static final String headerStr = "SQLite format 3\0"; //$NON-NLS-1$

    private static byte[] header;

    private static Properties sqliteConnectionProperties;

    static {
        try {
            header = headerStr.getBytes("UTF-8"); //$NON-NLS-1$
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            config.setOpenMode(SQLiteOpenMode.MAIN_DB);
            sqliteConnectionProperties = config.toProperties();
        } catch (UnsupportedEncodingException e) {
            header = headerStr.getBytes();
        }
    }

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {

        if (input == null)
            return MediaType.OCTET_STREAM;

        File dbFile = null;
        try {
            TikaInputStream tis = TikaInputStream.cast(input);
            if (tis == null) {
                throw new RuntimeException("Just a TikaInputStream can be given to " + this.getClass().getSimpleName());
            }

            byte[] prefix = new byte[32];
            int len = tis.peek(prefix);
            if (len < header.length)
                return MediaType.OCTET_STREAM;

            for (int i = 0; i < header.length; i++)
                if (prefix[i] != header[i])
                    return MediaType.OCTET_STREAM;

            dbFile = tis.getFile();
            return detectSQLiteFormat(dbFile);

        } finally {
            if (dbFile != null && IOUtil.isTemporaryFile(dbFile)) {
                new File(dbFile.getAbsolutePath() + "-wal").delete();
                new File(dbFile.getAbsolutePath() + "-shm").delete();
            }
        }

    }

    private MediaType detectSQLiteFormat(File file) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath(), //$NON-NLS-1$
                sqliteConnectionProperties); Statement st = conn.createStatement();) {
            Set<String> tableNames = new HashSet<String>();
            String sql = "SELECT name FROM sqlite_master WHERE type='table'"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);
            while (rs.next())
                tableNames.add(rs.getString(1));

            return detectTableNames(conn, tableNames);

        } catch (SQLException ex) {
            return SQLITE_MIME;
        }
    }

    private MediaType detectTableNames(Connection conn, Set<String> tableNames) throws SQLException {

        if (tableNames.contains("messagesv12") && //$NON-NLS-1$
                tableNames.contains("profilecachev8") && //$NON-NLS-1$
                (tableNames.contains("conversationsv14") || tableNames.contains("conversationsv13")) && //$NON-NLS-1$
                tableNames.contains("internaldata")) //$NON-NLS-1$
            return SkypeParser.SKYPE_MIME_V12;

        if (tableNames.contains("Messages") && //$NON-NLS-1$
                tableNames.contains("Participants") && //$NON-NLS-1$
                tableNames.contains("Contacts") && //$NON-NLS-1$
                tableNames.contains("Transfers") && //$NON-NLS-1$
                tableNames.contains("Conversations") && //$NON-NLS-1$
                tableNames.contains("Chats") && //$NON-NLS-1$
                tableNames.contains("Calls")) //$NON-NLS-1$
            return SkypeParser.SKYPE_MIME;

        if ((tableNames.contains("chat_list") || tableNames.contains("chat")) &&
                (tableNames.contains("messages") || tableNames.contains("message")) &&
                (tableNames.contains("group_participants") || tableNames.contains("group_participant_user")) &&
                tableNames.contains("media_refs") &&
                tableNames.contains("sqlite_sequence"))
            return WhatsAppParser.MSG_STORE;

        if (tableNames.contains("wa_contacts") && //$NON-NLS-1$
                tableNames.contains("wa_contact_capabilities") && //$NON-NLS-1$
                tableNames.contains("sqlite_sequence")) //$NON-NLS-1$
            return WhatsAppParser.WA_DB;

        if (tableNames.contains("ZWACHATSESSION") && //$NON-NLS-1$
                tableNames.contains("ZWAMESSAGE") && //$NON-NLS-1$
                tableNames.contains("ZWAMEDIAITEM") && //$NON-NLS-1$
                tableNames.contains("ZWAGROUPMEMBER")) //$NON-NLS-1$
            return WhatsAppParser.CHAT_STORAGE;

        if (tableNames.contains("ZWAADDRESSBOOKCONTACT") || //$NON-NLS-1$
                (tableNames.contains("ZWACONTACT") && tableNames.contains("ZWAPHONE"))) //$NON-NLS-1$ //$NON-NLS-2$
            return WhatsAppParser.CONTACTS_V2;

        if (tableNames.contains("moz_places") && //$NON-NLS-1$
                tableNames.contains("moz_bookmarks")) //$NON-NLS-1$
            return FirefoxSqliteParser.MOZ_PLACES;

        if (tableNames.contains("history_items") && //$NON-NLS-1$
                tableNames.contains("history_visits")) //$NON-NLS-1$
            return SafariSqliteParser.SAFARI_SQLITE;

        if (tableNames.contains("downloads") && //$NON-NLS-1$
                tableNames.contains("urls") && //$NON-NLS-1$
                tableNames.contains("visits") && //$NON-NLS-1$
                tableNames.contains("downloads_url_chains")) //$NON-NLS-1$
            return ChromeSqliteParser.CHROME_SQLITE;

        if (tableNames.contains("Activity") && tableNames.contains("Activity_PackageId") && tableNames.contains("ActivityOperation"))
            return WinXTimelineParser.WIN10_TIMELINE;

        if (tableNames.contains("events_persisted") && tableNames.contains("tag_descriptions")
                && tableNames.contains("provider_groups"))
            return EventTranscriptParser.EVENT_TRANSCRIPT;
        
        if (tableNames.contains("cloud_graph_entry") &&
                tableNames.contains("cloud_relations"))
            return GDriveMainParser.GDRIVE_CLOUD_GRAPH;
        
        if (tableNames.contains("cloud_entry") &&
                tableNames.contains("mapping") &&
                tableNames.contains("cloud_relations") &&
                tableNames.contains("local_entry") &&
                tableNames.contains("local_relations") &&
                tableNames.contains("volume_info"))
            return GDriveMainParser.GDRIVE_SNAPSHOT;
        
        if (tableNames.contains("global_preferences") ||
                tableNames.contains("data"))
            return GDriveMainParser.GDRIVE_ACCOUNT_INFO;

        if (tableNames.contains("dialogs") && tableNames.contains("chats") && tableNames.contains("users")
                && (tableNames.contains("messages") || tableNames.contains("messages_v2"))
                && (tableNames.contains("media") || tableNames.contains("media_v2") || tableNames.contains("media_v3")
                        || tableNames.contains("media_v4")))
            return TelegramParser.TELEGRAM_DB;
        
        // detection for Telegram iOS DB
        if (tableNames.contains("t0") && tableNames.contains("t2") && tableNames.contains("t6")
                && tableNames.contains("t7") && tableNames.contains("t9")) {
            return TelegramParser.TELEGRAM_DB_IOS;
        }

        // iOS backups databases below

        if (tableNames.contains("Files") && tableNames.contains("Properties")) {
            Set<String> cols = detectColumnNames(conn, "Files");
            if (cols.contains("fileID") && cols.contains("relativePath") && cols.contains("domain")) {
                return MediaType.application("x-ios-backup-manifest-db");
            }
        }

        if (tableNames.contains("message") && tableNames.contains("chat") && tableNames.contains("chat_message_join")
                && tableNames.contains("chat_handle_join")) {
            return MediaType.application("x-ios-sms-db");
        }

        if (tableNames.contains("ABPerson") && tableNames.contains("ABMultiValue")
                && tableNames.contains("ABPersonFullTextSearch_content")) {
            return MediaType.application("x-ios-addressbook-db");
        }

        if (tableNames.contains("call") && tableNames.contains("_SqliteDatabaseProperties")) {
            Set<String> cols = detectColumnNames(conn, "call");
            if (cols.contains("address") && cols.contains("date") && cols.contains("duration")) {
                return MediaType.application("x-ios-calllog-db");
            }
        }

        if (tableNames.contains("ZCALLDBPROPERTIES") && tableNames.contains("ZCALLRECORD")) {
            return MediaType.application("x-ios8-calllog-db");
        }

        if (tableNames.contains("voicemail")) {
            Set<String> cols = detectColumnNames(conn, "voicemail");
            if (cols.contains("remote_uid") && cols.contains("date") && cols.contains("duration")) {
                return MediaType.application("x-ios-voicemail-db");
            }
        }

        if (tableNames.contains("ZNOTE") && tableNames.contains("ZNOTEBODY")) {
            return MediaType.application("x-ios-oldnotes-db");
        }

        if (tableNames.contains("ZICCLOUDSTATE") && tableNames.contains("ZICCLOUDSYNCINGOBJECT")) {
            return MediaType.application("x-ios-notes-db");
        }

        if (tableNames.contains("ZALBUMLIST") && tableNames.contains("ZGENERICALBUM")) {
            return MediaType.application("x-ios-photos-db");
        }

        if (tableNames.contains("Calendar") && tableNames.contains("CalendarChanges")
                && tableNames.contains("CalendarItem")) {
            return MediaType.application("x-ios-calendar-db");
        }

        if (tableNames.contains("Fences") && tableNames.contains("Vertices")) {
            Set<String> cols = detectColumnNames(conn, "Fences");
            if (cols.contains("Latitude") && cols.contains("Longitude")) {
                return MediaType.application("x-ios-locations-db");
            }
        }
        
        if (tableNames.contains("ZCONVERSATION") && tableNames.contains("ZMESSAGE") && tableNames.contains("ZCONTACT") && tableNames.contains("ZFILEDATA") && tableNames.contains("ZIMAGEDATA")) {
            return ThreemaParser.CHAT_STORAGE;
        }

        return SQLITE_MIME;

    }

    private Set<String> detectColumnNames(Connection conn, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("select name FROM PRAGMA_TABLE_INFO(?)")) {
            ps.setString(1, tableName);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        return columns;
    }

}
