package net.sf.oereader;

/**
 * IndexedInfo objects hold the overarching layout of the .dbx files.
 * 
 * IndexedInfo objects are used as the structures for the
 * {@link net.sf.oereader.OEMessageInfo MessageInfo} and FolderInfo objects.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OEIndexedInfo extends OEBase {
	protected class IndexValue {
		public int index;
		public int value;
		public Boolean direct;

		public IndexValue(int i, int v, boolean d) {
			index = i;
			value = v;
			direct = d;
		}
	}

	private int marker;
	/**
	 * Length of the following {@link net.sf.oereader.OEIndexedInfo IndexedInfo}
	 * body
	 */
	public int bodylength;
	/**
	 * Length of this {@link net.sf.oereader.OEIndexedInfo IndexedInfo} object
	 */
	public int objectlength;
	/**
	 * Entries in the following index field
	 */
	public int entries;
	/**
	 * Counts the changes made to this object
	 */
	public int changes;
	protected int datapos;
	protected IndexValue[] indices;

	/**
	 * Constructor for an OEIndexedInfo object
	 * 
	 * @param data
	 *            data to be read
	 * @param i
	 *            index to start from
	 */
	public OEIndexedInfo(OEData data, int i) {
		marker = toInt4(data, i);
		bodylength = toInt4(data, i + 4);
		objectlength = toInt2(data, i + 8);
		entries = data.get(i + 10);
		changes = data.get(i + 11);
		indices = new IndexValue[entries];
		for (int n = 0; n < entries; n++) {
			int k = data.get(i + 12 + n * 4);
			int v = toInt3(data, i + 13 + n * 4);
			indices[n] = new IndexValue(k & 127, v, ((k >> 7) & 1) == 1);
		}
		datapos = i + 12 + entries * 4;
	}
}
