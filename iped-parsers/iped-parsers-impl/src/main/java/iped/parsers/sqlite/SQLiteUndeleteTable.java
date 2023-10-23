package iped.parsers.sqlite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fqlite.base.SqliteRow;

public class SQLiteUndeleteTable {
    private String tableName;
    private List<SqliteRow> tableRows;
    private List<String> tableColumns;

    public SQLiteUndeleteTable(List<String> columnNames) {
        tableRows = new ArrayList<>();
        tableColumns = columnNames;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<SqliteRow> getTableRows() {
        return tableRows;
    }

    public List<String> getColumnNames() {
        return tableColumns;
    }

    public Map<Long, List<SqliteRow>> getTableRowsGroupedByLongCol(String longColumnToGroup) {
        var tableDataMap = new HashMap<Long, List<SqliteRow>>();
        getTableRows().stream().forEach(row -> {
            long key = row.getIntValue(longColumnToGroup);
            if (key > 0) {
                var rows = tableDataMap.get(key);
                if (rows == null) {
                    rows = new ArrayList<>();
                    tableDataMap.put(key, rows);
                }
                rows.add(row);
            }
        });
        return tableDataMap;
    }

    public Map<String, List<SqliteRow>> getTableRowsGroupedByTextCol(String longColumnToGroup) {
        var tableDataMap = new HashMap<String, List<SqliteRow>>();
        getTableRows().stream().forEach(row -> {
            String key = row.getTextValue(longColumnToGroup);
            if (key != null) {
                var rows = tableDataMap.get(key);
                if (rows == null) {
                    rows = new ArrayList<>();
                    tableDataMap.put(key, rows);
                }
                rows.add(row);
            }
        });
        return tableDataMap;
    }

    public Map<Long, SqliteRow> getRowsMappedByLongPrimaryKey(String longPrimaryKeyColumnName) {
        var tableDataMap = new HashMap<Long, SqliteRow>();
        getTableRows().stream().forEach(row -> {
            long pk = row.getIntValue(longPrimaryKeyColumnName);
            if (pk > 0) {
                tableDataMap.put(pk, row);
            }
        });
        return tableDataMap;
    }
}
