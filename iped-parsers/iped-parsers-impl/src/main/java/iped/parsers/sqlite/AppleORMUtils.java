package iped.parsers.sqlite;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AppleORMUtils {
    static public boolean isAppleORMMetadata(Connection connection) throws SQLException {
		boolean isApple = true;
    	DatabaseMetaData dbm = connection.getMetaData();
    	try(ResultSet rsTables = dbm.getTables(null, null, null, new String[]{"TABLE"})) {
        	while(rsTables.next()) {
        		String tableName = rsTables.getString("TABLE_NAME");
        		try(ResultSet columns = dbm.getColumns(null,null, tableName, "Z%")){
        			if(!columns.next()) {            				
        				//if there is no columns with Z prefix
        				isApple=false;
        				return isApple;
        			}
        		}
        	}
    	}
    	return isApple;
    }
}
