package iped.parsers.jdbc;
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
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.standard.StandardParser;
import iped.properties.ExtraProperties;
import iped.utils.tika.IpedMetadata;

/**
 * Abstract class that handles iterating through tables within a database.
 */
public abstract class AbstractDBParser extends AbstractParser {

    private static final long serialVersionUID = 1L;

	private final static byte[] EMPTY_BYTE_ARR = new byte[0];

    public static final MediaType TABLE_REPORT = MediaType.application("x-database-table");

    public static final int HTML_MAX_ROWS = Integer.MAX_VALUE;
    
    public static final String DATETIME_MARKUP_START = "<time datetime=\"";
    public static final String DATABASEDATECOLUMN_PREFIX = "DatabaseDate"+TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    protected Connection connection;
    int tableRowsPerItem = 100;

    private boolean parseEmptyIncrementalRanges;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return null;
    }
    
    @Field
    public void setParseEmptyIncrementalRanges(boolean value) {
        this.parseEmptyIncrementalRanges = value;
    }

    @Field
    public void setTableRowsPerItem(int value) {
    	this.tableRowsPerItem = value;
    }

    private int parseTables(ContentHandler handler, ParseContext context, JDBCTableReader reader)
            throws SAXException, IOException, SQLException {

        TableReportGenerator trg = new TableReportGenerator(reader);
        int table_fragment = 0;
        TemporaryResources tmp = new TemporaryResources();
        boolean hasNext = true;
        do {
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));
            IpedMetadata tableM = new IpedMetadata();
            do {            	
                try(InputStream is = trg.createHtmlReport(tableRowsPerItem, handler, context, tmp, tableM)) {
                    ++table_fragment;
                    hasNext = trg.hasNext();
                    String title = reader.getTableName();
                    if (hasNext || table_fragment > 1) {
                        title += "_" + table_fragment;
                    }
                    tableM.set(TikaCoreProperties.TITLE, title);
                    tableM.set(StandardParser.INDEXER_CONTENT_TYPE, TABLE_REPORT.toString());
                    tableM.set(Database.TABLE_NAME, reader.getTableName());
                    tableM.set(Database.COLUMN_COUNT, Integer.toString(trg.getCols()));
                    tableM.set(Database.ROW_COUNT, Integer.toString(trg.getRows()));
                    tableM.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

                    extractor.parseEmbedded(is, handler, tableM, false);
                }
            }while(trg.hasNext());

        } while (hasNext);

        return trg.getTotRows();
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        connection = getConnection(stream, metadata, context);
        XHTMLContentHandler xHandler = null;
        List<String> tableNames = null;
        try {
            tableNames = getTableNames(connection, metadata, context);
            for (String tableName : tableNames) {
                // add table names to parent metadata
                metadata.add(Database.TABLE_NAME, tableName);
            }
            xHandler = new XHTMLContentHandler(handler, metadata);
            xHandler.startDocument();

            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$

            xHandler.startElement("table");
            xHandler.startElement("theader");

            xHandler.startElement("tr");

            xHandler.startElement("th");
            xHandler.characters("Name");
            xHandler.endElement("th");

            xHandler.startElement("th");
            xHandler.characters("Cols");
            xHandler.endElement("th");

            xHandler.startElement("th");
            xHandler.characters("Rows");
            xHandler.endElement("th");

            xHandler.endElement("tr");

            xHandler.endElement("theader");
            
            StringBuffer sbfim = new StringBuffer(); 

            boolean first = true;
            for (String tableName : tableNames) {
                JDBCTableReader reader = getTableReader(connection, tableName, context);

                int row_count = parseTables(xHandler, context, reader);
                xHandler.startElement("tr");

                xHandler.startElement("td");
                xHandler.characters(tableName);
                xHandler.endElement("td");

                xHandler.startElement("td");
                xHandler.characters(Integer.toString(reader.getHeaders().size()));
                xHandler.endElement("td");

                xHandler.startElement("td");
                xHandler.characters(Integer.toString(row_count));
                xHandler.endElement("td");

                xHandler.endElement("tr");
                
                if (parseEmptyIncrementalRanges) {
                    try {
                        StringBuffer sb = new StringBuffer();
                        DatabaseMetaData dbmd = connection.getMetaData();
                        ResultSet rs = dbmd.getColumns(null, null, tableName, "");
                        while (rs.next()) {
                            String isAutoIncrement = rs.getString("IS_AUTOINCREMENT");
                            if (isAutoIncrement != null && isAutoIncrement.equals("YES")) {
                                String columnName = rs.getString("COLUMN_NAME");
                                ResultSet rsGaps = reader.getSequentialGaps(columnName);
                                if (rs.next()) {
                                    if (first) {
                                        first = false;
                                        sb.append("<h1>Tabelas com vãos em campos autoincrementais</h1>");
                                    }
                                    sb.append("<table>");
                                    sb.append("<tr><th colspan=\"2\">Tabela:" + tableName + "</th></tr>");
                                    sb.append("<tr><th colspan=\"2\">Coluna:" + columnName + "</th></tr>");
                                    sb.append("<tr><th>Inicio</th><th>Fim</th></tr>");
                                    do {
                                        sb.append("<tr><td>" + rs.getString("start") + "</td><td>" + rs.getString("end")
                                                + "</td></tr>");

                                    } while (rs.next());
                                    sb.append("</table>");
                                }
                            }
                        }
                        sbfim.append(sb.toString());
                    } catch (Exception e) {
                        // ignores
                    }
                }


                reader.closeReader();
                
                xHandler.characters(sbfim.toString());
            }
        } catch (SQLException e) {
            throw new TikaException("SQLite parsing exception", e); //$NON-NLS-1$

        } finally {
            if (xHandler != null)
                xHandler.endDocument();
            try {
                close();
            } catch (Exception e) {
                // swallow
            }
        }
    }

    protected static EmbeddedDocumentExtractor getEmbeddedDocumentExtractor(ParseContext context) {
        return context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
    }

    /**
     * Override this for any special handling of closing the connection.
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    protected void close() throws SQLException, IOException {
        connection.close();
    }

    /**
     * Override this for special configuration of the connection, such as limiting
     * the number of rows to be held in memory.
     *
     * @param stream
     *            stream to use
     * @param metadata
     *            metadata that could be used in parameterizing the connection
     * @param context
     *            parsecontext that could be used in parameterizing the connection
     * @return connection
     * @throws java.io.IOException
     * @throws org.apache.tika.exception.TikaException
     */
    protected Connection getConnection(InputStream stream, Metadata metadata, ParseContext context)
            throws IOException, TikaException {
        String connectionString = getConnectionString(stream, metadata, context);

        Connection connection = null;
        try {
            Class.forName(getJDBCClassName());
        } catch (ClassNotFoundException e) {
            throw new TikaException(e.getMessage());
        }
        try {
            connection = DriverManager.getConnection(connectionString);
        } catch (SQLException e) {
            throw new IOException(e);
        }
        return connection;
    }

    /**
     * Implement for db specific connection information, e.g.
     * "jdbc:sqlite:/docs/mydb.db"
     * <p>
     * Include any optimization settings, user name, password, etc.
     * <p>
     * 
     * @param stream
     *            stream for processing
     * @param metadata
     *            metadata might be useful in determining connection info
     * @param parseContext
     *            context to use to help create connectionString
     * @return connection string to be used by {@link #getConnection}.
     * @throws java.io.IOException
     */
    abstract protected String getConnectionString(InputStream stream, Metadata metadata, ParseContext parseContext)
            throws IOException;

    /**
     * JDBC class name, e.g. org.sqlite.JDBC
     * 
     * @return jdbc class name
     */
    abstract protected String getJDBCClassName();

    /**
     *
     * Returns the names of the tables to process
     *
     * @param connection
     *            Connection to use to make the sql call(s) to get the names of the
     *            tables
     * @param metadata
     *            Metadata to use (potentially) in decision about which tables to
     *            extract
     * @param context
     *            ParseContext to use (potentially) in decision about which tables
     *            to extract
     * @return
     * @throws java.sql.SQLException
     */
    abstract protected List<String> getTableNames(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException;

    /**
     * Given a connection and a table name, return the JDBCTableReader for this db.
     *
     * @param connection
     * @param tableName
     * @return
     */
    abstract protected JDBCTableReader getTableReader(Connection connection, String tableName,
            ParseContext parseContext);

}
