package dpf.mt.gpinf.skype.parser;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

import dpf.mt.gpinf.skype.parser.v8.SkypeSqliteV12;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;

public class SkypeStorageFactory extends SQLite3DBParser {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public SkypeStorage createFromFile(File file, String mainDbPath) {
        if (file.getName().contains("main.db") || mainDbPath.contains("main.db")) {
            return new SkypeSqlite(file, mainDbPath);
        }

        if (file.getName().startsWith("s4l-") || mainDbPath.contains("s4l-")) {
            return new SkypeSqliteV12(file, mainDbPath);
        }

        return null;
    }

    public SkypeStorage createFromMediaType(TikaInputStream tis, Metadata metadata, ParseContext context,
            String mainDbPath) throws IOException {
        if (metadata.get(Metadata.CONTENT_TYPE).equals(SkypeParser.SKYPE_MIME.toString())) {
            return new SkypeSqlite(tis.getFile(), mainDbPath) {
                @Override
                public Connection getConnection() throws SkypeParserException {
                    if (conn == null) {
                        try {
                            conn = SkypeStorageFactory.this.getConnection(tis, metadata, context);
                        } catch (IOException e) {
                            throw new SkypeParserException(e);
                        }
                    }
                    return conn;
                }

                @Override
                public Connection getStorageDbConnection() throws SkypeParserException {
                    if (this.storageDbPath != null && this.connStorageDb == null) {
                        try (TikaInputStream tis = TikaInputStream.get(storageDbPath.toPath())) {
                            connStorageDb = SkypeStorageFactory.this.getConnection(tis, metadata, context);
                        } catch (IOException e) {
                            throw new SkypeParserException(e);
                        }
                    }
                    return connStorageDb;
                }

                @Override
                public Connection getMediaCacheDbConnection() throws SkypeParserException {
                    if (this.cacheMediaDbPath != null && this.connMediaCacheDb == null) {
                        try (TikaInputStream tis = TikaInputStream.get(cacheMediaDbPath.toPath())) {
                            connMediaCacheDb = SkypeStorageFactory.this.getConnection(tis, metadata, context);
                        } catch (IOException e) {
                            throw new SkypeParserException(e);
                        }
                    }
                    return connMediaCacheDb;
                }
            };
        }
        if (metadata.get(Metadata.CONTENT_TYPE).equals(SkypeParser.SKYPE_MIME_V12.toString())) {
            return new SkypeSqliteV12(tis.getFile(), mainDbPath) {
                @Override
                public Connection getConnection() throws SkypeParserException {
                    if (conn == null) {
                        try {
                            conn = SkypeStorageFactory.this.getConnection(tis, metadata, context);
                        } catch (IOException e) {
                            throw new SkypeParserException(e);
                        }
                    }
                    return conn;
                }
            };
        }
        return null;
    }
}