package iped.parsers.sqlite;

import fqlite.base.SqliteRow;

public interface SQLiteRecordValidator {
    boolean validateRecord(SqliteRow row);
}
