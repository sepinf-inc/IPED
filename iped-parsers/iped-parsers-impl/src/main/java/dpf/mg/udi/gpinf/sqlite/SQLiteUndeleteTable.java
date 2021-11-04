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
}
