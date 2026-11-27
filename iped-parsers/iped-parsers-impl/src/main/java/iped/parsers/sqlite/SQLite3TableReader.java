package iped.parsers.sqlite;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeSet;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.jdbc.AbstractDBParser;
import iped.parsers.jdbc.JDBCTableReader;
import iped.parsers.util.Messages;
import iped.utils.TimeConverter;

/**
 * Concrete class for SQLLite table parsing. This overrides column type handling
 * from JDBCRowHandler.
 * <p>
 * This class is not designed to be thread safe (because of DateFormat)! Need to
 * call a new instance for each parse, as AbstractDBParser does.
 * <p>
 * For now, this silently skips cells of type CLOB, because xerial's jdbc
 * connector does not currently support them.
 */
class SQLite3TableReader extends JDBCTableReader {

    private static final int UNIXTIME_DATEFORMAT = 1;
	private static final int SYSTEMTIME_DATEFORMAT = 2;
	private static final int PRTIME_DATEFORMAT = 3;
	private static final int FILETIME_DATEFORMAT = 4;
	private static final int JAVADATE_DATEFORMAT = 5;
	private static final int APPLEORM_DATEFORMAT = 6;
	
	private static final long APPLEORM_EPOCH = 978307200;
	SQLite3DBParser dbparser;
	TreeSet<Integer> notADate = new TreeSet<>();

	DateFormat df = new SimpleDateFormat(Messages.getString("SQLite3TableReader.DateFormat"), Locale.ROOT); //$NON-NLS-1$

    private boolean dateGuessed = false;

    public SQLite3TableReader(Connection connection, String tableName, ParseContext context, SQLite3DBParser dbparser) {
        super(connection, tableName, context);
        this.dbparser = dbparser;
        df.setTimeZone(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
    }

    /**
     * No-op for now in {@link SQLite3TableReader}.
     *
     * @param tableName
     * @param fieldName
     * @param rowNum
     * @param resultSet
     * @param columnIndex
     * @param handler
     * @param context
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    @Override
    protected String handleClob(String tableName, String fieldName, int rowNum, ResultSet resultSet, int columnIndex,
            ContentHandler handler, ParseContext context)
            throws SQLException, IOException, SAXException {
        // no-op for now.
        return null;
    }

    /**
     * The jdbc connection to Sqlite does not yet implement blob, have to
     * getBytes().
     *
     * @param resultSet
     *            resultSet
     * @param columnIndex
     *            columnIndex for blob
     * @return
     * @throws java.sql.SQLException
     */
    @Override
    protected TikaInputStream getInputStreamFromBlob(ResultSet resultSet, int columnIndex, Blob blob, Metadata m)
            throws SQLException {
        byte[] bytes = resultSet.getBytes(columnIndex);
        if (bytes == null)
            return null;

        return TikaInputStream.get(bytes, m);
    }

    private static Date minPlausibleDate = new Date(System.currentTimeMillis() - 10L * 365 * 24 * 3600 * 1000);
    private static Date maxPlausibleDate = new Date(System.currentTimeMillis() + 10L * 365 * 24 * 3600 * 1000);

    private int[] dateFormats, numPlausibleDates, zeroes;

    /*
     * Loops through all integer column values to determine if the column seams
     * to store integer date representation.
     * The result is stored in dateFormat[] array.
     */
    private void detectDateFormat() throws SQLException {
        ResultSet results = super.getTableData();
        ResultSetMetaData meta = results.getMetaData();
        if (dateFormats == null) {
            dateFormats = new int[meta.getColumnCount() + 1];
            numPlausibleDates = new int[meta.getColumnCount() + 1];
            zeroes = new int[meta.getColumnCount() + 1];
        }
        int cols = meta.getColumnCount();
        ArrayList<Integer> intCols = new ArrayList<Integer>();
        int maxRow = 100, row = 0;
        while (results.next() && row++ < maxRow) {
            for (int col = 1; col <= cols; col++) {
                if (meta.getColumnType(col) == Types.INTEGER) {
                    if (row == 1)
                        intCols.add(col);
                    long val = results.getLong(col);
                    if (val <= 0) {
                        zeroes[col]++;
                        continue;
                    }
                    Date date;
                    int dateFormat = 0;
                	if(meta.getColumnName(col).startsWith("Z") && meta.getTableName(col).startsWith("Z")) {
                		dateFormat = APPLEORM_DATEFORMAT-1; //goes directly to APPLEORM_DATEFORMAT, ignoring the remainder 
                	}
                    do {
                        date = decodeDate(val, ++dateFormat);

                        if (date != null && (date.after(minPlausibleDate) && date.before(maxPlausibleDate))) {
                            if (dateFormats[col] == 0)
                                dateFormats[col] = dateFormat;
                            if (dateFormats[col] == dateFormat)
                                numPlausibleDates[col]++;
                            break;
                        }

                    } while (date != null);

                }
                if (meta.getColumnType(col) == Types.FLOAT || meta.getColumnType(col) == Types.REAL) {
                	if(meta.getColumnName(col).startsWith("Z") && meta.getTableName(col).startsWith("Z")) {
                        int dateFormat = 0;
                		dateFormat = APPLEORM_DATEFORMAT-1; //goes directly to APPLEORM_DATEFORMAT, ignoring the remainder
                        long val = results.getLong(col);
                        if (val <= 0) {
                            zeroes[col]++;
                        }else {
                            Date date = decodeDate(val, ++dateFormat);

                            if (date != null && (date.after(minPlausibleDate) && date.before(maxPlausibleDate))) {
                                if (dateFormats[col] == 0)
                                    dateFormats[col] = dateFormat;
                                if (dateFormats[col] == dateFormat)
                                    numPlausibleDates[col]++;
                            }
                        }
                	}
                }
            }
        }
        for (int col : intCols) {
            if (numPlausibleDates[col] < (row - zeroes[col]) * 0.8 && row >= 5)
                dateFormats[col] = 0;
        }
        results.close();
    }

    private Date decodeDate(long val, int dateFormat) {
        Date date;
        switch (dateFormat) {
            case UNIXTIME_DATEFORMAT:
                date = TimeConverter.unixTimeToDate(val);
                break;
            case SYSTEMTIME_DATEFORMAT:
                date = TimeConverter.systemTimeToDate(val);
                break;
            case PRTIME_DATEFORMAT:
                date = TimeConverter.PRTimeToDate(val);
                break;
            case FILETIME_DATEFORMAT:
                date = TimeConverter.fileTimeToDate(val);
                break;
            case JAVADATE_DATEFORMAT:
                date = new Date(val);
                break;
            case APPLEORM_DATEFORMAT:
                date = TimeConverter.unixTimeToDate(val + APPLEORM_EPOCH);
                break;
            default:
                date = null;
        }
        return date;
    }

    @Override
    protected String handleInteger(ResultSetMetaData rsmd, ResultSet rs, int col, ContentHandler handler)
            throws SQLException, SAXException {

        String text = null;

        if (dateFormats == null)
            detectDateFormat();

        // As of this writing, with xerial's sqlite jdbc connector, a timestamp is
        // stored as a column of type Integer, but the columnTypeName is TIMESTAMP, and
        // the
        // value is a string representing a Long.
		String datetext="";
        if (rsmd.getColumnTypeName(col).equals("TIMESTAMP")) { //$NON-NLS-1$
        	long longValue = rs.getLong(col);
        	if(rs.wasNull()) {
        		text = null;
        	}else {
                if(dbparser.isAppleORM()) {
                	//Apple ORM date format
                	datetext = parseDateFromLong((longValue+ APPLEORM_EPOCH)*1000l);
                }else {
                	datetext = parseDateFromLong(longValue);
                }            
            	text=AbstractDBParser.DATETIME_MARKUP_START+datetext+"\">"+datetext+"</time>";
        	}
        } else {
            long val = rs.getLong(col);
            text = Long.toString(val);

            if (val > 0 && dateFormats[col] != 0) {
            	datetext = df.format(decodeDate(val, dateFormats[col]));
                text += AbstractDBParser.DATETIME_MARKUP_START+datetext+"\"> (*" + datetext + ")</time>";
                dateGuessed = true;
            }
        }

        return text;
    }

    @Override
    protected String handleFloat(ResultSetMetaData rsmd, ResultSet rs, int col, ContentHandler handler)
            throws SQLException, SAXException {

        String text = null;

        // As of this writing, with xerial's sqlite jdbc connector, a timestamp is
        // stored as a column of type Integer, but the columnTypeName is TIMESTAMP, and
        // the
        // value is a string representing a Long.
		String datetext="";
        if (rsmd.getColumnTypeName(col).equals("TIMESTAMP") //$NON-NLS-1$
                || rsmd.getColumnName(col).toLowerCase().contains("timestamp")) {
        	long longValue = rs.getLong(col);
        	if(rs.wasNull()) {
        		text = null;
        	}else {
                if(dbparser.isAppleORM()) {
                	//Apple ORM date format
                	datetext = parseDateFromLong((longValue+ APPLEORM_EPOCH)*1000l);
                }else {
                	datetext = parseDateFromLong(longValue);
                }            
            	text=AbstractDBParser.DATETIME_MARKUP_START+datetext+"\">"+datetext+"</time>";
        	}
        } else {
        	return super.handleFloat(rsmd, rs, col, handler);
        }

        return text;

    }

    private String parseDateFromLong(Long longValue) throws SAXException {
        java.sql.Date d = new java.sql.Date(longValue);
        return df.format(d);

    }

    @Override
    public boolean hasDateGuessed() {
        return dateGuessed;
    }
}
