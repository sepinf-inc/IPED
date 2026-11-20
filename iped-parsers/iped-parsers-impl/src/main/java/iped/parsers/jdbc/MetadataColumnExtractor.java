package iped.parsers.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import iped.utils.tika.IpedMetadata;

public interface MetadataColumnExtractor {
	String[] getColumnNames();

	public boolean extractMetadata(ResultSet rs, int columnIndex) throws SQLException;
	public void applyExtractedMetadatas(IpedMetadata metadata);
	public String getMetadataName();
	public void cancelLastExtraction();
	public String getLastExtraction();
}
