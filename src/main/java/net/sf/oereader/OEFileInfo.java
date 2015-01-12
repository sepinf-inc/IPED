package net.sf.oereader;

/**
 * Provides information about the current file.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OEFileInfo extends OEBase {
	/**
	 * Registry key of the account
	 */
	public String regKey;
	/**
	 * Folder name used in Outlook Express
	 */
	public String folderName;
	/**
	 * Source Type (3 = LocalStore, 0 = News)
	 */
	public int sourceType;
	/**
	 * Creation time of this file (FolderDB)
	 */
	public int[] filetime;

	/**
	 * Constructor for an OEFileInfo object.
	 * 
	 * @param type
	 *            String denoting the type of the file from the
	 *            {@link net.sf.oereader.OEFileHeader#type FileHeader}
	 * @param data
	 *            data to be read
	 * @param i
	 *            index to start from
	 */
	public OEFileInfo(String type, OEData data, int i) {
		if (type == "MessageDB") {
			if (toInt4(data, i) != 1)
				return;
			sourceType = data.get(i + 4);
			regKey = toString(data, i + 5);
			folderName = toString(data, i + 0x105);
		} else if (type == "FolderDB") {
			filetime = toInt8(data, i);
			if (toInt4(data, i + 8) != 1)
				return;
		}
	}
}
