package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class WAContactsExtractorAndroid extends WAContactsExtractor {

    private static final String SELECT_CONTACT_NAMES = "SELECT * FROM wa_contacts"; //$NON-NLS-1$

    public WAContactsExtractorAndroid(File database, WAContactsDirectory directory) {
        super(database, directory);
    }

    @Override
    public void extractContactList() throws WAExtractorException {
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
    }

}
