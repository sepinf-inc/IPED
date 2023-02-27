package iped.parsers.eventtranscript;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;

/**
 * Closeable iterator for a database
 * <p>
 * next() method needs to be implemented by the subclass, it should return a T
 * object representing the current row in the ResultSet (rs variable)
 */
public class DBIterator<T> implements Iterator<T>, Closeable {
    protected ResultSet rs;
    private Statement statement;

    public DBIterator(Connection connection, String query) throws SQLException {
        statement = connection.createStatement();
        rs = statement.executeQuery(query);
    }

    @Override
    public boolean hasNext() {
        try {
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T next() {
        return null;
    }

    @Override
    public void close() throws IOException {
        try {
            rs.close();
            statement.close();
        } catch (SQLException e) {
            // swallow
        }
    }

}