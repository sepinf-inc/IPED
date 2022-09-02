package iped.parsers.eventtranscript;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;

public class SQLIterator<T> implements Iterator<T>, Closeable {
    ResultSet rs;
    Statement statement;

    public SQLIterator(Connection connection, String query) throws SQLException {
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