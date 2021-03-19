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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.IOExceptionWithCause;
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

import dpf.sp.gpinf.indexer.parsers.util.Messages;
import dpf.sp.gpinf.indexer.util.SimpleHTMLEncoder;

/**
 * Abstract class that handles iterating through tables within a database.
 */
abstract class AbstractDBParser extends AbstractParser {

    private final static byte[] EMPTY_BYTE_ARR = new byte[0];

    private Connection connection;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return null;
    }

    private static final String newCol(String value, String tag) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(tag).append(">");
        if (value != null) {
            sb.append(SimpleHTMLEncoder.htmlEncode(value));
        }
        sb.append("</").append(tag).append(">");
        return sb.toString();
    }

    private static final String newCol(String value) {
        return newCol(value, "td");
    }

    private Metadata parseTables(ContentHandler handler, ParseContext context, String tableName)
            throws SAXException, IOException, SQLException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        Metadata tableM = new Metadata();

        int nrows = 0, ncols = 0;
        try (TemporaryResources tmp = new TemporaryResources()) {
            Path path = tmp.createTempFile();
            try (OutputStream os = Files.newOutputStream(path);
                    Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                    PrintWriter out = new PrintWriter(writer);) {
                JDBCTableReader tableReader = getTableReader(connection, tableName, context);
                ncols = tableReader.getHeaders().size();
                out.print("<head>"); //$NON-NLS-1$
                out.print("<style>"); //$NON-NLS-1$
                out.print("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
                out.print("</style>"); //$NON-NLS-1$
                out.print("</head>"); //$NON-NLS-1$

                out.print("<body>");
                out.print("<b>");
                out.print(Messages.getString("AbstractDBParser.Table") + tableReader.getTableName());
                out.print("</b>");
                
                out.print("<table name=\"" + tableReader.getTableName() + "\" >");
                out.print("<thead>");
                out.print("<tr>"); //$NON-NLS-1$
                for (String header : tableReader.getHeaders()) {
                    out.print(newCol(header, "th"));
                }
                out.print("</tr>"); //$NON-NLS-1$

                out.print("</thead>");

                out.print("<tbody>");
                ResultSet r = tableReader.getTableData();
                while (r != null && r.next()) {
                    nrows++;
                    out.print("<tr>");
                    for (int i = 1; i <= ncols; i++) {
                        String text = tableReader.handleCell(r, r.getMetaData(), i, handler, context, false, nrows);
                        out.print(newCol(text));
                    }
                    out.print("</tr>");
                }
                out.print("</tbody>");
                out.print("</table>");
                out.print("</body>");
                out.close();
                tableReader.closeReader();

            }
            tableM.set(TikaCoreProperties.TITLE, tableName);
            tableM.set(Database.TABLE_NAME, tableName);
            tableM.set(Database.COLUMN_COUNT, Integer.toString(ncols));
            tableM.set(Database.ROW_COUNT, Integer.toString(nrows));
            InputStream is = new BufferedInputStream(Files.newInputStream(path));
            extractor.parseEmbedded(is, handler, tableM, false);

            return tableM;

        }

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

            xHandler.characters(Messages.getString("AbstractDBParser.ProbableDate")); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$

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

            for (String tableName : tableNames) {
                Metadata t = parseTables(xHandler, context, tableName);
                xHandler.startElement("tr");

                xHandler.startElement("td");
                xHandler.characters(tableName);
                xHandler.endElement("td");

                xHandler.startElement("td");
                xHandler.characters(t.get(Database.COLUMN_COUNT));
                xHandler.endElement("td");

                xHandler.startElement("td");
                xHandler.characters(t.get(Database.ROW_COUNT));
                xHandler.endElement("td");

                xHandler.endElement("tr");
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
            throw new IOExceptionWithCause(e);
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
