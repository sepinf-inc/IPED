package dpf.inc.sepinf.browsers.parsers;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.parser.AbstractParser;

public abstract class AbstractSqliteBrowserParser extends AbstractParser{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String SQLITE_CLASS_NAME = "org.sqlite.JDBC"; //$NON-NLS-1$
    
    protected boolean extractEntries = true;
    
    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }
    
    protected Connection getConnection(File dbFile) throws IOException, TikaException {
        String connectionString = getConnectionString(dbFile);
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

    protected String getConnectionString(File dbFile) throws IOException {
        return "jdbc:sqlite:" + dbFile.getAbsolutePath(); //$NON-NLS-1$
    }

    protected String getJDBCClassName() {
        return SQLITE_CLASS_NAME;
    }

}
