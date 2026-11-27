package iped.parsers.ios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class IOSBackupManifestReader {

    private static final String FILES_TABLE = "Files";
    private static final Set<String> REQUIRED_COLUMNS = new HashSet<>(
            Arrays.asList("fileid", "domain", "relativepath", "flags", "file"));

    private static final String SELECT_ENTRIES = "SELECT fileID, domain, relativePath, flags, file "
            + "FROM Files ORDER BY domain, relativePath";

    @FunctionalInterface
    interface EntryConsumer<E extends Exception> {
        void accept(IOSBackupManifestEntry entry) throws E;
    }

    <E extends Exception> long readEntries(Connection connection, EntryConsumer<E> consumer)
            throws SQLException, E {
        long count = 0;
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ENTRIES);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                IOSBackupManifestEntry entry = new IOSBackupManifestEntry(
                        resultSet.getString("fileID"),
                        resultSet.getString("domain"),
                        resultSet.getString("relativePath"),
                        resultSet.getInt("flags"),
                        resultSet.getBytes("file"));
                consumer.accept(entry);
                count++;
            }
        }
        return count;
    }

    void validateSchema(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA table_info('" + FILES_TABLE + "')")) {
            while (resultSet.next()) {
                String column = resultSet.getString("name");
                if (column != null) {
                    columns.add(column.toLowerCase(Locale.ROOT));
                }
            }
        }

        Set<String> missingColumns = new HashSet<>(REQUIRED_COLUMNS);
        missingColumns.removeAll(columns);
        if (!missingColumns.isEmpty()) {
            throw new SQLException("Invalid iOS backup Manifest.db: Files table is missing columns "
                    + missingColumns);
        }
    }
}
