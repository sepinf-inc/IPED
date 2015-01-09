package net.sf.oereader;

/**
 * File header at the beginning of a .dbx Outlook Express file.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OEFileHeader extends OEBase {
	/**
	 * Type of file (MessageDB,FolderDB,OfflineDB)
	 */
	public String type = "";
	/**
	 * Length of {@link net.sf.oereader.OEFileInfo FileInfo} object
	 */
	public int fileInfoLength;
	/**
	 * Pointer to the last variable segment
	 */
	public int lastvseg;
	/**
	 * Length of a variable segment
	 */
	public int vseglength;
	/**
	 * Used space of the last variable segment
	 */
	public int lastvsegspace;
	/**
	 * Pointer to the last {@link net.sf.oereader.OETree Tree} segment
	 */
	public int lasttseg;
	/**
	 * Length of a {@link net.sf.oereader.OETree Tree} segment
	 */
	public int tseglength;
	/**
	 * Used space of the last {@link net.sf.oereader.OETree Tree} segment
	 */
	public int lasttsegspace;
	/**
	 * Pointer to the last {@link net.sf.oereader.OEMessage Message} segment
	 */
	public int lastmseg;
	/**
	 * Length of a {@link net.sf.oereader.OEMessage Message} segment
	 */
	public int mseglength;
	/**
	 * Used space of the last {@link net.sf.oereader.OEMessage Message} segment
	 */
	public int lastmsegspace;
	/**
	 * Root pointer to the deleted {@link net.sf.oereader.OEMessage Message}
	 * list
	 */
	public int rootp_deletedm;
	/**
	 * Root pointer to the deleted {@link net.sf.oereader.OETree Tree} list
	 */
	public int rootp_deletedt;
	/**
	 * Used space in the middle sector of the file
	 */
	public int middle_use_space;
	/**
	 * Reusable space in the middle sector of the file
	 */
	public int middle_reuse_space;
	/**
	 * Index of the last entry in the {@link net.sf.oereader.OETree Tree}
	 */
	public int lasttentry;
	/**
	 * Pointer to the first FolderList node
	 */
	public int firstflnode;
	/**
	 * Pointer to the last FolderList node
	 */
	public int lastflnode;
	/**
	 * Used space of the file (length of the first and the middle sector)
	 */
	public int fm_use_space;
	/**
	 * Pointer to the MessageConditions object
	 */
	public int mconditions;
	/**
	 * Pointer to the FolderConditions object
	 */
	public int fconditions;
	/**
	 * Entries in the rootnode of the main {@link net.sf.oereader.OETree Tree}
	 */
	public int rootnodeEntries;
	/**
	 * Entries in the rootnode of the variant {@link net.sf.oereader.OETree
	 * Tree} (Watched or ignored {@link net.sf.oereader.OEMessageInfo
	 * MessageInfo} objects)
	 */
	public int variantEntries;
	/**
	 * Pointer to the rootnode of the main {@link net.sf.oereader.OETree Tree}
	 */
	public int rootnode;
	/**
	 * Pointer to the rootnode of the variant {@link net.sf.oereader.OETree
	 * Tree}
	 */
	public int rootnodeVariant;

	/**
	 * Constructor for an OEFileHeader.
	 * 
	 * The file header is the first data that will be read from a given .dbx
	 * file. It contains all information necessary to parse the rest of the
	 * file.
	 * 
	 * @param data
	 *            data to be read
	 */
	public OEFileHeader(OEData data) {
		if (toInt(data, 0) != 0xcfad12fe) {
			return;
		}
		switch (toInt(data, 4)) {
		case 0xc5fd746f:
			type = "MessageDB";
			break;
		case 0xc6fd746f:
			type = "FolderDB";
			break;
		case 0x309dfe26:
			type = "OfflineDB";
			break;
		default:
			return;
		}
		// if (toInt(data,8) != 0x66e3d111 || toInt(data,12) != 0x9a4e00c0 ||
		// toInt(data,16) != 0x4fa309d4 || toInt(data,20) != 0x05000000 ||
		// toInt(data,24) != 0x05000000) return;
		fileInfoLength = toInt4(data, 28);
		lastvseg = toInt4(data, 36);
		vseglength = toInt4(data, 40);
		lastvsegspace = toInt4(data, 44);
		lasttseg = toInt4(data, 48);
		tseglength = toInt4(data, 52);
		lasttsegspace = toInt4(data, 56);
		lastmseg = toInt4(data, 60);
		mseglength = toInt4(data, 64);
		lastmsegspace = toInt4(data, 68);
		rootp_deletedm = toInt4(data, 72);
		rootp_deletedt = toInt4(data, 76);
		middle_use_space = toInt4(data, 84);
		middle_reuse_space = toInt4(data, 88);
		lasttentry = toInt4(data, 92);
		// if (toInt4(data,100) != 1) return;
		if (type == "FolderDB" && toInt4(data, 104) != 1)
			return;
		firstflnode = toInt4(data, 108);
		lastflnode = toInt4(data, 112);
		if (type == "FolderDB" && toInt4(data, 116) != 3 && toInt4(data, 120) != 2)
			return;
		fm_use_space = toInt4(data, 124);
		// if (type == "MessageDB" && toInt4(data,128) != 2) return;
		if (type == "FolderDB" && toInt4(data, 128) != 3)
			return;
		mconditions = toInt4(data, 136);
		fconditions = toInt4(data, 140);
		rootnodeEntries = toInt4(data, 196);
		variantEntries = toInt4(data, 200);
		rootnode = toInt4(data, 228);
		rootnodeVariant = toInt4(data, 232);
		// Ends at 0x24bc
	}

	/**
	 * Check the magic number sequences in the beginning of the file.
	 * 
	 * @param data
	 *            data to be read
	 * @return whether the magic numbers were valid or not
	 */
	public boolean checkMagic(OEData data) {
		if (toInt(data, 0) != 0xcfad12fe) {
			return false;
		}
		switch (toInt(data, 4)) {
		case 0xc5fd746f:
		case 0xc6fd746f:
		case 0x309dfe26:
			break;
		default:
			return false;
		}
		// if (toInt(data,8) != 0x66e3d111 || toInt(data,12) != 0x9a4e00c0 ||
		// toInt(data,16) != 0x4fa309d4 || toInt(data,20) != 0x05000000 ||
		// toInt(data,24) != 0x05000000) return false;
		return true;
	}
}
