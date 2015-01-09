package net.sf.oereader;

/**
 * Stores tree information from the .dbx file.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OETree extends OEBase {
	private int marker;
	/**
	 * Pointer to the child node
	 */
	public int child;
	/**
	 * Pointer to the parent node
	 */
	public int parent;
	/**
	 * Number of stored entries in the child tree of this node
	 */
	public int childvalues;
	/**
	 * Values in the body of this node
	 */
	public int[] value;
	/**
	 * Pointers to child nodes of this node
	 */
	public int[] childp;
	/**
	 * Number of stored entries in the {@link #childp children} of this tree
	 */
	public int[] childvaluesp;
	/**
	 * Node id of this node
	 */
	public int nodeId;
	/**
	 * Number of entries in the body of this node
	 */
	public int bodyentries;
	/**
	 * {@link net.sf.oereader.OETree OETree} of the child of this node
	 */
	public OETree dChild;
	/**
	 * {@link net.sf.oereader.OETree OETree}'s of the children of this node
	 */
	public OETree[] bChildren;

	/**
	 * Reads a tree of data recursively from the root node of the file.
	 * 
	 * Reads and reconstructs the tree of data for perusal later.
	 * 
	 * @param data
	 *            data to be read
	 * @param i
	 *            index to start from
	 */
	public OETree(OEData data, int i) {
		marker = toInt4(data, i);
		child = toInt4(data, i + 8);
		parent = toInt4(data, i + 12);
		nodeId = data.get(i + 16) & 0xff;
		bodyentries = data.get(i + 17) & 0xff;
		childvalues = toInt4(data, i + 20);

		if (child != 0) {
			dChild = new OETree(data, child);
		}

		value = new int[bodyentries];
		childp = new int[bodyentries];
		childvaluesp = new int[bodyentries];
		bChildren = new OETree[bodyentries];

		for (int k = 0; k < bodyentries; k++) {
			value[k] = toInt4(data, 24 + 12 * k + i);
			childp[k] = toInt4(data, 28 + 12 * k + i);
			if (childp[k] != 0)
				bChildren[k] = new OETree(data, childp[k]);
			childvaluesp[k] = toInt4(data, 32 + 12 * k + i);
		}

	}
}
