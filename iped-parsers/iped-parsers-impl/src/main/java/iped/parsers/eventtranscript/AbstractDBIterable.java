package iped.parsers.eventtranscript;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;

public abstract class AbstractDBIterable<T> implements Iterable<T>, Closeable {

    public ResultSet rs;
    public Statement statement;

    public AbstractDBIterable(Connection connection, String query) throws SQLException {
        statement = connection.createStatement();
        rs = statement.executeQuery(query);
    }

    @Override
    public abstract Iterator<T> iterator();

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