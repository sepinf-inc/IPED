package iped.parsers.jdbc;

import java.util.ArrayList;

import iped.utils.tika.IpedMetadata;

public abstract class AbstractMetadataColumnExtractor implements MetadataColumnExtractor {
	ArrayList<String> extractedValues = new ArrayList<String>();

	protected boolean isCompatibleColName(String colName) {
		String[] columnNames = getColumnNames();
		for (int i = 0; i < columnNames.length; i++) {
			boolean found=false;
			String columnName = columnNames[i].toLowerCase();
			if(columnName.startsWith("*")&&columnNames[i].endsWith("*")) {
				found=colName.contains(columnName.substring(1));
			}
			if(found) {
				return true;
			}
			if(columnName.startsWith("*")) {
				found=colName.endsWith(columnName.substring(1));
			}
			if(columnName.endsWith("*")) {
				found=colName.endsWith(columnName.substring(0,columnName.length()-2));
			}
			if(found) {
				return true;
			}
			if(columnName.equals(colName)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void applyExtractedMetadatas(IpedMetadata tableM) {
		tableM.set(getMetadataName(), extractedValues);
	}
	
	@Override
	public void cancelLastExtraction() {
		extractedValues.remove(extractedValues.size()-1);
	}
	
	@Override
	public String getLastExtraction() {
		return extractedValues.get(extractedValues.size()-1);
	}

}
