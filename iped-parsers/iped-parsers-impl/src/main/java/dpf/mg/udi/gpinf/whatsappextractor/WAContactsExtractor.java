package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;

public abstract class WAContactsExtractor {

    protected final WAContactsDirectory directory;
    protected final File databaseFile;
    private HashSet<String> invalidCol = new HashSet<String>();

    protected WAContactsExtractor(File database, WAContactsDirectory directory) {
        this.databaseFile = database;
        this.directory = directory;
    }

    public WAContactsDirectory getContactsDirectory() {
        return directory;
    }

    public abstract void extractContactList() throws WAExtractorException;
    
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    protected String getString(ResultSet rs, String colname) {
        if (!invalidCol.contains(colname)) {
            try {
                return rs.getString(colname);

            } catch (SQLException e) {
                invalidCol.add(colname);
            }
        }
        return null;
    }
}
