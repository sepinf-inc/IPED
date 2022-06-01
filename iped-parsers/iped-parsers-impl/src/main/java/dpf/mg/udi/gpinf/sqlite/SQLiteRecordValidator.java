package dpf.mg.udi.gpinf.sqlite;

import fqlite.base.SqliteRow;

public interface SQLiteRecordValidator {
    boolean validateRecord(SqliteRow row);
}
