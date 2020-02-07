package dpf.sp.gpinf.detector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.detect.Detector;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

import dpf.inc.sepinf.browsers.parsers.ChromeSqliteParser;
import dpf.inc.sepinf.browsers.parsers.FirefoxSqliteParser;
import dpf.inc.sepinf.browsers.parsers.SafariSqliteParser;
import dpf.mg.udi.gpinf.whatsappextractor.WhatsAppParser;
import dpf.mt.gpinf.skype.parser.SkypeParser;

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

    static {
        try {
            header = headerStr.getBytes("UTF-8"); //$NON-NLS-1$

        } catch (UnsupportedEncodingException e) {
            header = headerStr.getBytes();
        }
    }

    @Override
    public MediaType detect(InputStream input, Metadata metadata) throws IOException {

        if (input == null)
            return MediaType.OCTET_STREAM;

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(input, tmp);

            byte[] prefix = new byte[32];
            int len = tis.peek(prefix);
            if (len < header.length)
                return MediaType.OCTET_STREAM;

            for (int i = 0; i < header.length; i++)
                if (prefix[i] != header[i])
                    return MediaType.OCTET_STREAM;

            return detectSQLiteFormat(tis.getFile());

        } finally {
            tmp.close();
        }

    }

    private MediaType detectSQLiteFormat(File file) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath()); //$NON-NLS-1$
                Statement st = conn.createStatement();) {
            Set<String> tableNames = new HashSet<String>();
            String sql = "SELECT name FROM sqlite_master WHERE type='table'"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);
            while (rs.next())
                tableNames.add(rs.getString(1));

            return detectTableNames(tableNames);

        } catch (SQLException ex) {
            return SQLITE_MIME;
        }
    }

    private MediaType detectTableNames(Set<String> tableNames) {

        if (tableNames.contains("messagesv12") && //$NON-NLS-1$
                tableNames.contains("profilecachev8") && //$NON-NLS-1$
                ( tableNames.contains("conversationsv14") || tableNames.contains("conversationsv13") )&& //$NON-NLS-1$
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

        if (tableNames.contains("chat_list") && //$NON-NLS-1$
                tableNames.contains("messages") && //$NON-NLS-1$
                tableNames.contains("group_participants") && //$NON-NLS-1$
                tableNames.contains("media_refs") && //$NON-NLS-1$
                tableNames.contains("receipts") && //$NON-NLS-1$
                tableNames.contains("sqlite_sequence")) //$NON-NLS-1$
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

        return SQLITE_MIME;

    }

}
