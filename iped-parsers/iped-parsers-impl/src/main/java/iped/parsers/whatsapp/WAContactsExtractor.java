package iped.parsers.whatsapp;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;

public abstract class WAContactsExtractor {

    protected final WAContactsDirectory directory;
    protected final File databaseFile;
    private HashSet<String> invalidCol = new HashSet<String>();
    protected boolean recoverDeletedRecords;

    protected WAContactsExtractor(File database, WAContactsDirectory directory, boolean recoverDeletedRecords) {
        this.databaseFile = database;
        this.directory = directory;
        this.recoverDeletedRecords = recoverDeletedRecords;
    }

    public WAContactsDirectory getContactsDirectory() {
        return directory;
    }

    public abstract void extractContactList() throws WAExtractorException;

    protected abstract Connection getConnection() throws SQLException;

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
