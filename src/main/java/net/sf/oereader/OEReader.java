package net.sf.oereader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Main class used to read data from a .dbx Outlook Express file.
 * 
 * This class takes care of loading the data from the .dbx file, parsing the
 * {@link net.sf.oereader.OEFileHeader header}, and loading an array of
 * {@link net.sf.oereader.OEMessage messages} to be used by the application.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OEReader {
	/**
	 * {@link net.sf.oereader.OEFileHeader FileHeader} of the file
	 */
	public OEFileHeader header;
	/**
	 * {@link net.sf.oereader.OEFileInfo FileInfo} of the file
	 */
	public OEFileInfo info;

	// public byte data[];
	public OEData data;
	public Boolean open = false;

	public OEReader() {
	}

	/**
	 * Opens a file, given the name, and reads the header information.
	 * 
	 * @param file
	 *            name of the .dbx file
	 * @return whether the file was read successfully
	 * @throws IOException
	 */
	public boolean open(String file) throws IOException {
		if (file != null) {
			File f = new File(file);
			return open(f);
		} else
			return false;

	}

	/**
	 * Opens a file, given the {@link java.io.File File}, and reads the header
	 * information.
	 * 
	 * @param file
	 *            {@link java.io.File File} of the .dbx file
	 * @return whether the file was read successfully
	 * @throws IOException
	 */
	public boolean open(File file) throws IOException {
		if (file != null) {
			data = new OEData(file);
		} else
			return false;

		if (data == null)
			return false;

		header = new OEFileHeader(data);
		if (!header.checkMagic(data))
			return false;
		info = new OEFileInfo(header.type, data, 0x24bc);
		open = true;
		return true;
	}

	/**
	 * Closes a previously opened file.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		header = null;
		info = null;
		open = false;
		data.close();
	}

	private ArrayList<OEMessageInfo> tree_Messages(OEData data, OETree t) {
		if (t == null)
			return new ArrayList<OEMessageInfo>();

		ArrayList<OEMessageInfo> ret = new ArrayList<OEMessageInfo>(t.bodyentries);
		for (int i = 0; i < t.bodyentries; i++)
			ret.add(new OEMessageInfo(data, t.value[i]));

		ret.addAll(tree_Messages(data, t.dChild));

		for (int i = 0; i < t.bodyentries; i++)
			ret.addAll(tree_Messages(data, t.bChildren[i]));

		return ret;
	}

	/**
	 * Gets an array of all {@link net.sf.oereader.OEMessageInfo OEMessageInfo}
	 * objects from the opened file.
	 * 
	 * @return an array of {@link net.sf.oereader.OEMessageInfo OEMessageInfo}
	 *         objects
	 */
	public ArrayList<OEMessageInfo> getMessages() {
		if (open == false)
			return null;

		if (header.rootnodeEntries < 1)
			return null;

		OETree tree = new OETree(data, header.rootnode);
		ArrayList<OEMessageInfo> messages = tree_Messages(data, tree);

		return messages;
	}

}
