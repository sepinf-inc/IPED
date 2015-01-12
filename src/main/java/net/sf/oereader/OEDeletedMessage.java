package net.sf.oereader;

import java.io.ByteArrayOutputStream;

public class OEDeletedMessage extends OEBase {
	private int marker;
	/**
	 * Length of the deleted data
	 */
	public int length;
	/**
	 * Pointer to the next {@link net.sf.oereader.OEMessage Message} object
	 */
	public int next;

	public ByteArrayOutputStream bytes = new ByteArrayOutputStream();

	/**
	 * Loads the OEMessage object.
	 * 
	 * Traverses the linked list of message body text segments and adds them
	 * together
	 * 
	 * @param data
	 *            data to be read
	 * @param i
	 *            index to start from
	 */
	public OEDeletedMessage(OEData data, int i) {
		marker = toInt4(data, i);
		length = toInt4(data, i + 4);
		next = toInt4(data, i + 16);
		// System.out.println(length);
		bytes.write(data.get(i + 20, length), 0, length);

		System.out.println(bytes.toString());

	}
}
