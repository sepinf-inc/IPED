package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class WAContactsExtractorIOS extends WAContactsExtractor {

    private static final String SELECT_CONTACT_NAMES = "SELECT * FROM ZWAADDRESSBOOKCONTACT WHERE ZWHATSAPPID IS NOT NULL"; //$NON-NLS-1$

    private static final String SELECT_CONTACT_NAMES_1 = "SELECT * FROM ZWAPHONE " //$NON-NLS-1$
            + "LEFT JOIN ZWACONTACT ON ZWAPHONE.ZCONTACT = ZWACONTACT.Z_PK " //$NON-NLS-1$
            + "LEFT JOIN ZWASTATUS ON ZWAPHONE.Z_PK = ZWASTATUS.ZPHONE"; //$NON-NLS-1$

    public WAContactsExtractorIOS(File database, WAContactsDirectory directory) {
        super(database, directory);
    }

    @Override
    public void extractContactList() throws WAExtractorException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            ResultSet rs;
            try {
                rs = stmt.executeQuery(SELECT_CONTACT_NAMES);

            } catch (SQLException ex) {
                rs = stmt.executeQuery(SELECT_CONTACT_NAMES_1);
            }

            while (rs.next()) {
                WAContact c = directory.getContact(getString(rs, "ZWHATSAPPID") + "@s.whatsapp.net"); //$NON-NLS-1$ //$NON-NLS-2$
                c.setDisplayName(getString(rs, "ZHIGHLIGHTEDNAME")); //$NON-NLS-1$
                c.setWaName(getString(rs, "ZFULLNAME")); //$NON-NLS-1$
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
    }

}
