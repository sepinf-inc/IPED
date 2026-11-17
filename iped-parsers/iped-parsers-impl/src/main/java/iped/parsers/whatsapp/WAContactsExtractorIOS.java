package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Util.nullToEmpty;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fqlite.base.SqliteRow;
import iped.parsers.sqlite.SQLiteRecordValidator;
import iped.parsers.sqlite.SQLiteUndelete;
import iped.parsers.sqlite.SQLiteUndeleteTable;

public abstract class WAContactsExtractorIOS extends WAContactsExtractor {
    
    private static Logger logger = LoggerFactory.getLogger(WAContactsExtractorIOS.class);

    private static final String SELECT_CONTACT_NAMES = "SELECT * FROM ZWAADDRESSBOOKCONTACT WHERE ZWHATSAPPID IS NOT NULL"; //$NON-NLS-1$

    private static final String SELECT_CONTACT_NAMES_1 = "SELECT * FROM ZWAPHONE " //$NON-NLS-1$
            + "LEFT JOIN ZWACONTACT ON ZWAPHONE.ZCONTACT = ZWACONTACT.Z_PK " //$NON-NLS-1$
            + "LEFT JOIN ZWASTATUS ON ZWAPHONE.Z_PK = ZWASTATUS.ZPHONE"; //$NON-NLS-1$

    public WAContactsExtractorIOS(File database, WAContactsDirectory directory, boolean recoverDeletedRecords) {
        super(database, directory, recoverDeletedRecords);
    }

    protected abstract Connection getConnection() throws SQLException;

    @Override
    public void extractContactList() throws WAExtractorException {
        
        SQLiteUndeleteTable undeletedContactsTable = null;
        
        if (recoverDeletedRecords) {
            try {
                SQLiteUndelete undelete = new SQLiteUndelete(databaseFile.toPath());
                undelete.addTableToRecover("ZWAADDRESSBOOKCONTACT"); //$NON-NLS-1$
                undelete.addRecordValidator("ZWAADDRESSBOOKCONTACT", new WAIOSContactValidator()); //$NON-NLS-1$
                undeletedContactsTable = undelete.undeleteData().get("ZWAADDRESSBOOKCONTACT"); //$NON-NLS-1$
            } catch (Exception e) {
                logger.warn("Error recovering deleted records from IOS WhatsApp Contacts Database", e);
            }
        }
        
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            ResultSet rs;
            try {
                rs = stmt.executeQuery(SELECT_CONTACT_NAMES);

            } catch (SQLException ex) {
                rs = stmt.executeQuery(SELECT_CONTACT_NAMES_1);
            }

            while (rs.next()) {
                String id = getString(rs, "ZWHATSAPPID");
                if (!id.endsWith(WAContact.waSuffix)) {
                    id += WAContact.waSuffix;
                }
                WAContact c = directory.getContact(id);
                c.setWaName(getString(rs, "ZHIGHLIGHTEDNAME")); //$NON-NLS-1$
                c.setDisplayName(getString(rs, "ZFULLNAME")); //$NON-NLS-1$
                c.setNickName(getString(rs, "ZNICKNAME")); //$NON-NLS-1$
                String given_name = getString(rs, "ZGIVENNAME"); //$NON-NLS-1$
                if (given_name == null)
                    given_name = getString(rs, "ZFIRSTNAME"); //$NON-NLS-1$
                c.setGivenName(given_name);
                String status = getString(rs, "ZABOUTTEXT"); //$NON-NLS-1$
                if (status == null)
                    status = getString(rs, "ZTEXT"); //$NON-NLS-1$
                c.setStatus(status);
            }

        } catch (SQLException ex) {
            throw new WAExtractorException(ex);
        }
        
        if (undeletedContactsTable != null) {
            for (var row : undeletedContactsTable.getTableRows()) {
                var id = row.getTextValue("ZWHATSAPPID");
                if (!id.endsWith(WAContact.waSuffix)) {
                    id += WAContact.waSuffix;
                }
                if (! directory.hasContact(Util.getNameFromId(id))) { // only recover contact if it does not exist already
                    WAContact c = directory.getContact(id);
                    c.setWaName(nullToEmpty(row.getTextValue("ZHIGHLIGHTEDNAME"))); //$NON-NLS-1$
                    c.setDisplayName(nullToEmpty(row.getTextValue("ZFULLNAME"))); //$NON-NLS-1$
                    c.setNickName(nullToEmpty(row.getTextValue("ZNICKNAME"))); //$NON-NLS-1$
                    String given_name = row.getTextValue("ZGIVENNAME"); //$NON-NLS-1$
                    if (given_name == null) 
                        given_name = row.getTextValue("ZFIRSTNAME"); //$NON-NLS-1$
                    c.setGivenName(nullToEmpty(given_name));
                    String status = row.getTextValue("ZABOUTTEXT"); //$NON-NLS-1$
                    if (status == null)
                        status = row.getTextValue("ZTEXT"); //$NON-NLS-1$
                    c.setStatus(nullToEmpty(status)); //$NON-NLS-1$
                    c.setDeleted(true);
                }
            }
        }
    }
    
    
    private static class WAIOSContactValidator implements SQLiteRecordValidator {

        @Override
        public boolean validateRecord(SqliteRow row) {
            try {
                String jid = row.getTextValue("ZWHATSAPPID"); //$NON-NLS-1$
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
