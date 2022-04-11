package dpf.mg.udi.gpinf.sqlite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fqlite.base.SqliteRow;

public class SQLiteUndeleteTable {
    private String tableName;
    private List<SqliteRow> tableRows;
    private List<String> tableColumns;
    private Map<String, Integer> colIdx = new HashMap<>();

    public SQLiteUndeleteTable(List<String> columnNames) {
        int id = 0;
        for (String col : columnNames) {
            colIdx.put(col, id++);
        }
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

    public int getColumnIndex(String columnName) {
        if (colIdx.containsKey(columnName)) {
            return colIdx.get(columnName);
        }
        return -1;
    }

    public long getIntValue(SqliteRow row, String col) {
        return row.getRowData().get(colIdx.get(col)).getIntValue();
    }

    public String getTextValue(SqliteRow row, String col) {
        return row.getRowData().get(colIdx.get(col)).getTextValue();
    }

    public double getFloatValue(SqliteRow row, String col) {
        return row.getRowData().get(colIdx.get(col)).getFloatValue();
    }

    public byte[] getBlobValue(SqliteRow row, String col) {
        return row.getRowData().get(colIdx.get(col)).getBlobValue();
    }

    public Map<Long, List<SqliteRow>> getTableRowsGroupedByLongCol(String longColumnToGroup) {
        var tableDataMap = new HashMap<Long, List<SqliteRow>>();
        getTableRows().stream().forEach(row -> {
            long key = getIntValue(row, longColumnToGroup);
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
            String key = getTextValue(row, longColumnToGroup);
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
            long pk = getIntValue(row, longPrimaryKeyColumnName);
            if (pk > 0) {
                tableDataMap.put(pk, row);
            }
        });
        return tableDataMap;
    }
}
