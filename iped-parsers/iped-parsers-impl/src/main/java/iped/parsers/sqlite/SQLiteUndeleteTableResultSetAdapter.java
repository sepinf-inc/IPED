package iped.parsers.sqlite;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import fqlite.base.SqliteRow;

public class SQLiteUndeleteTableResultSetAdapter implements ResultSet {

    private SQLiteUndeleteTable table;
    private int idx;
    private Map<String, String> columnNamesMap;
    private Set<String> colNamesSet;

    public SQLiteUndeleteTableResultSetAdapter(SQLiteUndeleteTable table, Predicate<SqliteRow> filter) {
        this(table, Collections.emptyMap(), filter);
    }

    public SQLiteUndeleteTableResultSetAdapter(SQLiteUndeleteTable table, Map<String, String> columnNamesMap, Predicate<SqliteRow> filter) {
        this(table.getTableRows(), table.getColumnNames(), columnNamesMap, filter);
    }

    public SQLiteUndeleteTableResultSetAdapter(List<SqliteRow> rows, List<String> columnNames) {
        this(rows, columnNames, Collections.emptyMap());
    }

    public SQLiteUndeleteTableResultSetAdapter(List<SqliteRow> rows, List<String> columnNames, Map<String, String> columnNamesMap) {
        this(rows, columnNames, columnNamesMap, (x) -> true);
    }

    public SQLiteUndeleteTableResultSetAdapter(List<SqliteRow> rows, List<String> columnNames, Predicate<SqliteRow> filter) {
        this(rows, columnNames, Collections.emptyMap(), filter);
    }

    public SQLiteUndeleteTableResultSetAdapter(List<SqliteRow> rows, List<String> columnNames, Map<String, String> columnNamesMap, Predicate<SqliteRow> filter) {
        this.columnNamesMap = columnNamesMap;
        this.table = new SQLiteUndeleteTable(columnNames);
        this.colNamesSet = new HashSet<>(columnNames);
        for (SqliteRow row : rows) {
            if (filter.test(row)) {
                this.table.getTableRows().add(row);
            }
        }
        idx = -1;
    }
    
    private String getMappedColumnName(String columnName) throws SQLException {
        String result = columnNamesMap.getOrDefault(columnName, columnName);
        if (colNamesSet.contains(result)) {
            return result;
        }
        throw new SQLException("Column " + columnName + " not found. no such column");
    }
    
    public SqliteRow getCurrentRow() {
        return table.getTableRows().get(idx);
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        if (row > table.getTableRows().size() || row < 0)
            return false;
        idx = row;
        return true;
    }

    @Override
    public void afterLast() throws SQLException {
        idx = table.getTableRows().size();

    }

    @Override
    public void beforeFirst() throws SQLException {
        idx = -1;
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void clearWarnings() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void close() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void deleteRow() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        return getCurrentRow().getColumnIndex(getMappedColumnName(columnLabel));

    }

    @Override
    public boolean first() throws SQLException {
        idx = 0;
        return true;
    }

    @Override
    public Array getArray(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Array getArray(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        try {
            return new BigDecimal(getCurrentRow().getIntValue(columnIndex));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        try {
            return new BigDecimal(getCurrentRow().getIntValue(getMappedColumnName(columnLabel)));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Blob getBlob(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        try {
            return getCurrentRow().getIntValue(columnIndex) != 0;
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        try {
            return getCurrentRow().getIntValue(getMappedColumnName(columnLabel)) != 0;
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        try {
            return (byte) getCurrentRow().getIntValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        try {
            return (byte) getCurrentRow().getIntValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        try {
            return getCurrentRow().getBlobValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        try {
            return getCurrentRow().getBlobValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Clob getClob(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public int getConcurrency() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public String getCursorName() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        try {
            return new Date(getCurrentRow().getIntValue(columnIndex));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Date getDate(String columnLabel) throws SQLException {
        try {
            return new Date(getCurrentRow().getIntValue(getMappedColumnName(columnLabel)));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        try {
            return getCurrentRow().getFloatValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        try {
            return getCurrentRow().getFloatValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public int getFetchDirection() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public int getFetchSize() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        try {
            return (float) getCurrentRow().getFloatValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        try {
            return (float) getCurrentRow().getFloatValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public int getHoldability() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        try {
            return (int) getCurrentRow().getIntValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        try {
            return (int) getCurrentRow().getIntValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        try {
            return getCurrentRow().getIntValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        try {
            return getCurrentRow().getIntValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public String getNString(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        try {
            return getCurrentRow().getBlobValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        try {
            return getCurrentRow().getBlobValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Ref getRef(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public int getRow() throws SQLException {
        return idx;
    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        try {
            return (short) getCurrentRow().getIntValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        try {
            return (short) getCurrentRow().getIntValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Statement getStatement() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        try {
            return getCurrentRow().getTextValue(columnIndex);
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        try {
            return getCurrentRow().getTextValue(getMappedColumnName(columnLabel));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        try {
            return new Time(getCurrentRow().getIntValue(columnIndex));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Time getTime(String columnLabel) throws SQLException {
        try {
            return new Time(getCurrentRow().getIntValue(getMappedColumnName(columnLabel)));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        try {
            throw new SQLException("Not supported");
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        try {
            return new Timestamp(getCurrentRow().getIntValue(columnIndex));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        try {
            return new Timestamp(getCurrentRow().getIntValue(getMappedColumnName(columnLabel)));
        } catch (IndexOutOfBoundsException e) {
            throw new SQLException("no such column", e);
        }
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public int getType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public URL getURL(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void insertRow() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        return idx == table.getTableRows().size();
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        return idx == -1;
    }

    @Override
    public boolean isFirst() throws SQLException {
        return idx == 0;
    }

    @Override
    public boolean isLast() throws SQLException {
        return idx == table.getTableRows().size() - 1;
    }

    @Override
    public boolean last() throws SQLException {
        idx = table.getTableRows().size() - 1;
        return true;
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean next() throws SQLException {
        if (idx < table.getTableRows().size() - 1) {
            idx++;
            return true;
        }
        return false;
    }

    @Override
    public boolean previous() throws SQLException {
        if (idx > 0) {
            idx--;
            return true;
        }
        return false;
    }

    @Override
    public void refreshRow() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        if (idx + rows < table.getTableRows().size() && idx + rows >= 0) {
            idx += rows;
            return true;
        }
        return false;
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean rowInserted() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public boolean isWrapperFor(Class<?> arg0) throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> arg0) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        throw new SQLException("Not supported");
    }


    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        throw new SQLException("Not supported");
    }


    @Override
    public boolean isClosed() throws SQLException {
        return false;
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        throw new SQLException("Not supported");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNull(int columnIndex) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateNull(String columnLabel) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateRow() throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        throw new SQLException("Not supported");

    }

    @Override
    public boolean wasNull() throws SQLException {
        throw new SQLException("Not supported");
    }


}

