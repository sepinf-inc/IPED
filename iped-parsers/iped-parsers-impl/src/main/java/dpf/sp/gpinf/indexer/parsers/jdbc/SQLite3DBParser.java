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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.sqlite.SQLiteConfig;

import dpf.sp.gpinf.indexer.parsers.util.DelegatingConnection;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;

/**
 * This is the implementation of the db parser for SQLite.
 * <p>
 * This parser is internal only; it should not be registered in the services
 * file or configured in the TikaConfig xml file.
 */
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
            throw new IOExceptionWithCause(e);
        }
        TemporaryResources tmp = new TemporaryResources();
        try {
            File dbFile = TikaInputStream.get(stream, tmp).getFile();
            File walTemp = exportWalLog(dbFile, context);
            if(walTemp != null) {
                tmp.addResource(new Closeable() {
                    @Override
                    public void close() {
                        walTemp.delete();
                        new File(dbFile.getAbsolutePath() + "-shm").delete();
                    }
                });
            }
            String connectionString = getConnectionString(dbFile);
            
            SQLiteConfig config = new SQLiteConfig();

            // good habit, but effectively meaningless here
            config.setReadOnly(true);
            connection = config.createConnection(connectionString);
            
            connection = new DelegatingConnection(connection) {
                @Override
                public void close() throws SQLException {
                    super.close();
                    try {
                        tmp.close();
                    } catch (IOException e) {
                        throw new SQLException(e);
                    }
                }
            };

        } catch (SQLException e) {
            throw new IOException(e.getMessage());
        }
        return connection;
    }
    
    private File exportWalLog(File dbFile, ParseContext context) throws IOException {
        IItemSearcher searcher = context.get(IItemSearcher.class);
        if(searcher != null) {
            IItemBase dbItem = context.get(IItemBase.class);
            if(dbItem != null) {
                String dbPath = dbItem.getPath();
                String walQuery = BasicProps.PATH + ":\"" + searcher.escapeQuery(dbPath + "-wal") + "\"";
                List<IItemBase> items = searcher.search(walQuery);
                if(items.size() > 0) {
                    IItemBase wal = items.get(0);
                    File walTemp = new File(dbFile.getAbsolutePath() + "-wal");
                    if(!walTemp.exists()) {
                        try(InputStream in = wal.getBufferedStream()){
                            Files.copy(in, walTemp.toPath());
                        }
                    }
                    return walTemp;
                }
            }
        }
        return null;
    }
    
    
    
    @Override
    protected String getConnectionString(InputStream stream, Metadata metadata, ParseContext context) throws IOException {
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
        List<String> tableNames = new LinkedList<String>();

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
}
