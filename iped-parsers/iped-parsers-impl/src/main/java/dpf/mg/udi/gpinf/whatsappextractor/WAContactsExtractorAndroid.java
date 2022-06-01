package dpf.mg.udi.gpinf.whatsappextractor;

import static dpf.mg.udi.gpinf.whatsappextractor.Util.nullToEmpty;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.mg.udi.gpinf.sqlite.SQLiteRecordValidator;
import dpf.mg.udi.gpinf.sqlite.SQLiteUndelete;
import dpf.mg.udi.gpinf.sqlite.SQLiteUndeleteTable;
import fqlite.base.SqliteRow;

public class WAContactsExtractorAndroid extends WAContactsExtractor {
    
    private static Logger logger = LoggerFactory.getLogger(WAContactsExtractorAndroid.class);

    private static final String SELECT_CONTACT_NAMES = "SELECT * FROM wa_contacts"; //$NON-NLS-1$

    public WAContactsExtractorAndroid(File database, WAContactsDirectory directory, boolean recoverDeletedRecords) {
        super(database, directory, recoverDeletedRecords);
    }

    @Override
    public void extractContactList() throws WAExtractorException {
        
        SQLiteUndeleteTable undeletedContactsTable = null;
        
        if (recoverDeletedRecords) {
            try {
                SQLiteUndelete undelete = new SQLiteUndelete(databaseFile.toPath());
                undelete.addTableToRecover("wa_contacts"); //$NON-NLS-1$
                undelete.addRecordValidator("wa_contacts", new WAAndroidContactValidator()); //$NON-NLS-1$
                undeletedContactsTable = undelete.undeleteData().get("wa_contacts"); //$NON-NLS-1$
            } catch (Exception e) {
                logger.warn("Error recovering deleted records from Android WhatsApp Contacts Database", e);
            }
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(SELECT_CONTACT_NAMES)) {

                while (rs.next()) {
                    WAContact c = directory.getContact(getString(rs, "jid")); //$NON-NLS-1$
                    c.setDisplayName(getString(rs, "display_name")); //$NON-NLS-1$
                    c.setWaName(getString(rs, "wa_name")); //$NON-NLS-1$
                    c.setNickName(getString(rs, "nickname")); //$NON-NLS-1$
                    c.setSortName(getString(rs, "sort_name")); //$NON-NLS-1$
                    c.setGivenName(getString(rs, "given_name")); //$NON-NLS-1$
                    c.setStatus(getString(rs, "status")); //$NON-NLS-1$
                }
            }
        } catch (SQLException ex) {
            throw new WAExtractorException(ex);
        }
        
        if (undeletedContactsTable != null) {
            for (var row : undeletedContactsTable.getTableRows()) {
                var jid = row.getTextValue("jid");
                if (! directory.hasContact(jid)) { // only recover contact if it does not exist already
                    WAContact c = directory.getContact(jid);
                    c.setDisplayName(nullToEmpty(row.getTextValue("display_name"))); //$NON-NLS-1$
                    c.setWaName(nullToEmpty(row.getTextValue("wa_name"))); //$NON-NLS-1$
                    c.setNickName(nullToEmpty(row.getTextValue("nickname"))); //$NON-NLS-1$
                    c.setSortName(nullToEmpty(row.getTextValue("sort_name"))); //$NON-NLS-1$
                    c.setGivenName(nullToEmpty(row.getTextValue("given_name"))); //$NON-NLS-1$
                    c.setStatus(nullToEmpty(row.getTextValue("status"))); //$NON-NLS-1$
                    c.setDeleted(true);
                }
            }
        }
    }
    
    private static class WAAndroidContactValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                String jid = row.getTextValue("jid"); //$NON-NLS-1$
                if (jid == null || jid.isBlank()) {
                    return false;
                }
                return true;
                
            } catch (Exception e) {
            }
            return false;
        }

    }

}
