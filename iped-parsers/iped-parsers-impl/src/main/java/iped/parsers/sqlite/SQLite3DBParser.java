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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.sqlite.SQLiteConfig;

import iped.data.IItemReader;
import iped.parsers.jdbc.AbstractDBParser;
import iped.parsers.jdbc.JDBCTableReader;
import iped.parsers.util.DelegatingConnection;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;
import iped.utils.IOUtil;

/**
 * This is the implementation of the db parser for SQLite.
 * <p>
 * This parser is internal only; it should not be registered in the services
 * file or configured in the TikaConfig xml file.
 */
@SuppressWarnings("serial")
public class SQLite3DBParser extends AbstractDBParser {

    protected static final String SQLITE_CLASS_NAME = "org.sqlite.JDBC"; //$NON-NLS-1$

    /**
     *
     * @param context
     *            context
     * @return null (always)
     */
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return null;
    }

    @Override
    protected Connection getConnection(InputStream stream, Metadata metadata, ParseContext context) throws IOException {
        Connection connection = null;
        try {
            Class.forName(getJDBCClassName());
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        TemporaryResources tmp = new TemporaryResources();
        try {
            final File tempDB;
            File tikaFile = TikaInputStream.get(stream, tmp).getFile();
            if (!IOUtil.isTemporaryFile(tikaFile)) {
                File tempFile = Files.createTempFile("sqlite_tmp", ".db").toFile();
                tmp.addResource(() -> {
                    tempFile.delete();
                });
                IOUtil.copyFile(tikaFile, tempFile);
                tempDB = tempFile;
            } else {
                tempDB = tikaFile;
            }

            exportWalLog(tempDB, context, tmp);
            exportRollbackJournal(tempDB, context, tmp);

            SQLiteConfig config = new SQLiteConfig();
            // don't set this: see #1186
            // config.setReadOnly(true);

            String connectionString = getConnectionString(tempDB);
            connection = config.createConnection(connectionString);

            connection = new DelegatingConnection(connection) {
                @Override
                public void close() throws SQLException {
                    super.close();
                    try {
                        tmp.close();
                        String absPath = tempDB.getAbsolutePath();
                        Files.deleteIfExists(Paths.get(absPath + "-wal"));
                        Files.deleteIfExists(Paths.get(absPath + "-shm"));
                        Files.deleteIfExists(Paths.get(absPath + "-journal"));
                    } catch (Exception e) {
                        // don't propagate temp files deleting errors
                        e.printStackTrace();
                    }
                }
            };

        } catch (SQLException e) {
            throw new IOException(e.getMessage());
        }
        return connection;
    }

    private static File exportWalLog(File dbFile, ParseContext context, TemporaryResources tmp) {
        return exportRelatedFile(dbFile, "-wal", context, tmp);
    }
    
    private static File exportRollbackJournal(File dbFile, ParseContext context, TemporaryResources tmp) {
        return exportRelatedFile(dbFile, "-journal", context, tmp);
    }
    
    private static File exportRelatedFile(File theFile, String suffix, ParseContext context, TemporaryResources tmp) {
        IItemSearcher searcher = context.get(IItemSearcher.class);
        if (searcher != null) {
            IItemReader parsingItem = context.get(IItemReader.class);
            if (parsingItem != null) {
                String parsingFilePath = parsingItem.getPath();
                String relatedFileName = parsingItem.getName() + suffix;
                String relatedFileQuery = BasicProps.PATH + ":\"" + searcher.escapeQuery(parsingFilePath + suffix) + "\"";
                List<IItemReader> items = searcher.search(relatedFileQuery);
                if (items.size() > 0) {
                    IItemReader relatedItem = null;
                    // Pick the journal/wal, prioritizing the same deleted status.
                    for (IItemReader item : items) {
                        if (item.isDir() || !relatedFileName.equalsIgnoreCase(item.getName())) {
                            // Ignore folders or items with name that doesn't match SQLite name (see #1791)
                            continue;
                        }
                        if (relatedItem == null || item.isDeleted() == parsingItem.isDeleted()) {
                            relatedItem = item;
                        }
                    }
                    if (relatedItem != null) {
                        File relatedFileTemp = new File(theFile.getAbsolutePath() + suffix);
                        try (InputStream in = relatedItem.getBufferedInputStream()) {
                            Files.copy(in, relatedFileTemp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return relatedFileTemp;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected String getConnectionString(InputStream stream, Metadata metadata, ParseContext context)
            throws IOException {
        throw new RuntimeException("Not Implemented"); //$NON-NLS-1$
    }

    protected String getConnectionString(File dbFile) throws IOException {
        return "jdbc:sqlite:" + dbFile.getAbsolutePath(); //$NON-NLS-1$
    }

    @Override
    protected String getJDBCClassName() {
        return SQLITE_CLASS_NAME;
    }

    @Override
    protected List<String> getTableNames(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<String> tableNames = new ArrayList<String>();

        Statement st = null;
        try {
            st = connection.createStatement();
            String sql = "SELECT name FROM sqlite_master WHERE type='table'"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                tableNames.add(rs.getString(1));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return tableNames;
    }

    @Override
    public JDBCTableReader getTableReader(Connection connection, String tableName, ParseContext context) {
        return new SQLite3TableReader(connection, tableName, context);
    }

    public static boolean checkIfColumnExists(Connection connection, String table, String column) throws SQLException  {
        String query = "SELECT name FROM pragma_table_info('" + table + "')";
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(query)) {
            while (rs.next()) {
                if (rs.getString(1).equals(column)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean containsTable(String table, Connection connection) throws SQLException {
        SQLite3DBParser parser = new SQLite3DBParser();
        return parser.getTableNames(connection, null, null).contains(table);
    }

    public static String getStringIfExists(ResultSet rs, String col) throws SQLException {
        int colIdx;
        String result = null;
        try {
            colIdx = rs.findColumn(col);
            result = rs.getString(colIdx);

        } catch (SQLException e) {
            if (!e.toString().contains("no such column"))
                throw e;
        }
        return result;
    }
    
    public static int getIntIfExists(ResultSet rs, String col) throws SQLException {
        int colIdx;
        int result = 0;
        try {
            colIdx = rs.findColumn(col);
            result = rs.getInt(colIdx);

        } catch (SQLException e) {
            if (!e.toString().contains("no such column"))
                throw e;
        }
        return result;
    }
}
