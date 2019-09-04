package dpf.sp.gpinf.indexer.parsers.jdbc;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;

import javax.swing.text.html.HTMLEditorKit.Parser;

import org.apache.commons.codec.binary.Hex;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * General base class to iterate through rows of a JDBC table
 */
class JDBCTableReader {

    private final static Attributes EMPTY_ATTRIBUTES = new AttributesImpl();
    private final Connection connection;
    private final String tableName;
    int maxClobLength = 1000000;
    private ResultSet results = null;
    int rows = 0;
    private TikaConfig tikaConfig = null;
    private Detector detector = null;
    private MimeTypes mimeTypes = null;
    private EmbeddedDocumentExtractor ex;
    private static Logger LOGGER = LoggerFactory.getLogger(JDBCTableReader.class);

    public JDBCTableReader(Connection connection, String tableName, ParseContext context) {
        this.connection = connection;
        this.tableName = tableName;
        this.tikaConfig = context.get(TikaConfig.class);
        this.ex = AbstractDBParser.getEmbeddedDocumentExtractor(context);
    }

    public boolean nextRow(ContentHandler handler, ParseContext context) throws IOException, SAXException {
        // lazy initialization
        if (results == null) {
            results = getTableData();
        }
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Parsing interrupted"); //$NON-NLS-1$
        try {
            if (results == null || !results.next()) {
                return false;
            }
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        try {
            ResultSetMetaData meta = results.getMetaData();
            handler.startElement(XHTMLContentHandler.XHTML, "tr", "tr", EMPTY_ATTRIBUTES); //$NON-NLS-1$ //$NON-NLS-2$
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                handler.startElement(XHTMLContentHandler.XHTML, "td", "td", EMPTY_ATTRIBUTES); //$NON-NLS-1$ //$NON-NLS-2$
                handleCell(meta, i, handler, context);
                handler.endElement(XHTMLContentHandler.XHTML, "td", "td"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            handler.endElement(XHTMLContentHandler.XHTML, "tr", "tr"); //$NON-NLS-1$ //$NON-NLS-2$

        } catch (Exception e2) {
            LOGGER.warn("Error reading sqlite row {} from table {}", rows, tableName); //$NON-NLS-1$
        }
        rows++;
        return true;
    }

    private void handleCell(ResultSetMetaData rsmd, int i, ContentHandler handler, ParseContext context)
            throws SQLException, IOException, SAXException {
        switch (rsmd.getColumnType(i)) {
            case Types.BLOB:
                handleBlob(tableName, rsmd.getColumnName(i), rows, results, i, handler, context);
                break;
            case Types.CLOB:
                handleClob(tableName, rsmd.getColumnName(i), rows, results, i, handler, context);
                break;
            case Types.BOOLEAN:
                handleBoolean(results.getBoolean(i), handler);
                break;
            case Types.DATE:
                handleDate(results, i, handler);
                break;
            case Types.TIMESTAMP:
                handleTimeStamp(results, i, handler);
                break;
            case Types.INTEGER:
                handleInteger(rsmd, results, i, handler);
                break;
            case Types.FLOAT:
                // this is necessary to handle rounding issues in presentation
                // Should we just use getString(i)?
                addAllCharacters(Float.toString(results.getFloat(i)), handler);
                break;
            case Types.DOUBLE:
                addAllCharacters(Double.toString(results.getDouble(i)), handler);
                break;
            default:
                addAllCharacters(results.getString(i), handler);
                break;
        }
    }

    public List<String> getHeaders() throws IOException {
        List<String> headers = new LinkedList<String>();
        // lazy initialization
        if (results == null) {
            results = getTableData();
        }
        if (results != null)
            try {
                ResultSetMetaData meta = results.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    headers.add(meta.getColumnName(i));
                }
            } catch (SQLException e) {
                throw new IOExceptionWithCause(e);
            }
        return headers;
    }

    protected void handleInteger(ResultSetMetaData rsmd, ResultSet rs, int columnIndex, ContentHandler handler)
            throws SQLException, SAXException {
        addAllCharacters(Integer.toString(rs.getInt(columnIndex)), handler);
    }

    private void handleBoolean(boolean aBoolean, ContentHandler handler) throws SAXException {
        addAllCharacters(Boolean.toString(aBoolean), handler);
    }

    protected void handleClob(String tableName, String columnName, int rowNum, ResultSet resultSet, int columnIndex,
            ContentHandler handler, ParseContext context) throws SQLException, IOException, SAXException {
        Clob clob = resultSet.getClob(columnIndex);
        boolean truncated = clob.length() > Integer.MAX_VALUE || clob.length() > maxClobLength;

        int readSize = (clob.length() < maxClobLength ? (int) clob.length() : maxClobLength);
        Metadata m = new Metadata();
        m.set(Database.TABLE_NAME, tableName);
        m.set(Database.COLUMN_NAME, columnName);
        m.set(Database.PREFIX + "ROW_NUM", Integer.toString(rowNum)); //$NON-NLS-1$
        m.set(Database.PREFIX + "IS_CLOB", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        m.set(Database.PREFIX + "CLOB_LENGTH", Long.toString(clob.length())); //$NON-NLS-1$
        m.set(Database.PREFIX + "IS_CLOB_TRUNCATED", Boolean.toString(truncated)); //$NON-NLS-1$
        m.set(Metadata.CONTENT_TYPE, "text/plain; charset=UTF-8"); //$NON-NLS-1$
        m.set(Metadata.CONTENT_LENGTH, Integer.toString(readSize));
        // just in case something screwy is going on with the column name
        String name = FilenameUtils.normalize(FilenameUtils.getName(columnName + "_" + rowNum + ".txt")); //$NON-NLS-1$ //$NON-NLS-2$
        m.set(TikaMetadataKeys.RESOURCE_NAME_KEY, name);
        addAllCharacters(name, handler);

        // is there a more efficient way to go from a Reader to an InputStream?
        String s = clob.getSubString(0, readSize);
        // EmbeddedDocumentExtractor ex =
        // AbstractDBParser.getEmbeddedDocumentExtractor(context);
        ex.parseEmbedded(new ByteArrayInputStream(s.getBytes("UTF-8")), handler, m, false); //$NON-NLS-1$
    }

    protected void handleBlob(String tableName, String columnName, int rowNum, ResultSet resultSet, int columnIndex,
            ContentHandler handler, ParseContext context) throws SQLException, IOException, SAXException {
        Blob blob = null;
        Metadata m = new Metadata();
        /*
         * m.set(Database.TABLE_NAME, tableName); m.set(Database.COLUMN_NAME,
         * columnName); m.set(Database.PREFIX + "ROW_NUM", Integer.toString(rowNum));
         * m.set(Database.PREFIX + "IS_BLOB", "true");
         */
        TikaInputStream is = getInputStreamFromBlob(resultSet, columnIndex, blob, m);
        if (is != null)
            try {
                /*
                 * Attributes attrs = new AttributesImpl(); ((AttributesImpl)
                 * attrs).addAttribute("", "type", "type", "CDATA", "blob"); ((AttributesImpl)
                 * attrs).addAttribute("", "column_name", "column_name", "CDATA", columnName);
                 * ((AttributesImpl) attrs).addAttribute("", "row_number", "row_number",
                 * "CDATA", Integer.toString(rowNum)); handler.startElement("", "span", "span",
                 * attrs); MediaType mediaType = getDetector().detect(is, new Metadata());
                 * String extension = ""; try { MimeType mimeType =
                 * getMimeTypes().forName(mediaType.toString()); m.set(Metadata.CONTENT_TYPE,
                 * mimeType.toString()); extension = mimeType.getExtension(); } catch
                 * (MimeTypeException e) { //swallow } //just in case something screwy is going
                 * on with the column name String name =
                 * FilenameUtils.normalize(FilenameUtils.getName(columnName + "_" + rowNum +
                 * extension));
                 */

                int MIN_SIZE = 32;
                if (is.getLength() > MIN_SIZE) {
                    String name = tableName + "_" + columnName + "_" + rowNum + ".data"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    if (context.get(Parser.class) != null || !(ex instanceof ParsingEmbeddedDocumentExtractor)) {
                        m.set(TikaMetadataKeys.RESOURCE_NAME_KEY, name);
                        ex.parseEmbedded(is, handler, m, false);
                    } else
                        addAllCharacters(name, handler);
                } else {
                    byte[] bytes = new byte[(int) is.getLength()];
                    is.read(bytes);
                    addAllCharacters("0x" + Hex.encodeHexString(bytes), handler); //$NON-NLS-1$
                }

            } finally {
                if (blob != null) {
                    try {
                        blob.free();
                    } catch (SQLException e) {
                        // swallow
                    }
                }
                IOUtils.closeQuietly(is);
            }
        // handler.endElement("", "span", "span");
    }

    protected TikaInputStream getInputStreamFromBlob(ResultSet resultSet, int columnIndex, Blob blob, Metadata metadata)
            throws SQLException {
        return TikaInputStream.get(blob, metadata);
    }

    protected void handleDate(ResultSet resultSet, int columnIndex, ContentHandler handler)
            throws SAXException, SQLException {
        addAllCharacters(resultSet.getString(columnIndex), handler);
    }

    protected void handleTimeStamp(ResultSet resultSet, int columnIndex, ContentHandler handler)
            throws SAXException, SQLException {
        addAllCharacters(resultSet.getString(columnIndex), handler);
    }

    protected void addAllCharacters(String s, ContentHandler handler) throws SAXException {
        if (s == null)
            return;
        char[] chars = s.toCharArray();
        handler.characters(chars, 0, chars.length);
    }

    protected ResultSet getTableData() {

        ResultSet results;
        String sql = "SELECT * from " + tableName; //$NON-NLS-1$
        try {
            Statement st = connection.createStatement();
            results = st.executeQuery(sql);

        } catch (SQLException e) {
            results = null;
            // throw new IOExceptionWithCause(e);
        }
        rows = 0;
        return results;
    }

    public void closeReader() throws SQLException {
        if (results != null)
            results.close();
    }

    public String getTableName() {
        return tableName;
    }

    protected TikaConfig getTikaConfig() {
        if (tikaConfig == null) {
            tikaConfig = TikaConfig.getDefaultConfig();
        }
        return tikaConfig;
    }

    protected Detector getDetector() {
        if (detector != null)
            return detector;

        detector = getTikaConfig().getDetector();
        return detector;
    }

    protected MimeTypes getMimeTypes() {
        if (mimeTypes != null)
            return mimeTypes;

        mimeTypes = getTikaConfig().getMimeRepository();
        return mimeTypes;
    }

}
