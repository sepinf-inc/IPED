package net.sf.oereader;

import java.io.UnsupportedEncodingException;

/**
 * Base class which all other classes inherit; contains a number of functions to
 * parse data from binary.
 * 
 * @author Alex Franchuk
 * @version 1.0
 */
public class OEBase {
	/**
	 * Converts 4 bytes directly to an int (big endian).
	 * 
	 * @param data
	 *            data array to read
	 * @param i
	 *            index to start from
	 * @return integer composed of the 4 read bytes
	 */
	protected int toInt(OEData data, int i) {
		return ((data.get(i) & 0xff) << 24) | ((data.get(i + 1) & 0xff) << 16) | ((data.get(i + 2) & 0xff) << 8) | (data.get(i + 3) & 0xff);
	}

	/**
	 * Converts 2 bytes to an int (technically a short in an int).
	 * 
	 * @param data
	 *            data array to read
	 * @param i
	 *            index to start from
	 * @return integer composed of the 2 bytes, in proper order
	 */
	protected int toInt2(OEData data, int i) {
		return ((data.get(i + 1) & 0xff) << 8) | (data.get(i) & 0xff);
	}

	/**
	 * Converts 3 bytes to an int.
	 * 
	 * @param data
	 *            data array to read
	 * @param i
	 *            index to start from
	 * @return integer composed of the 3 bytes, in proper order
	 */
	protected int toInt3(OEData data, int i) {
		return ((data.get(i + 2) & 0xff) << 16) | ((data.get(i + 1) & 0xff) << 8) | (data.get(i) & 0xff);
	}

	/**
	 * Converts 4 bytes to an int (little endian).
	 * 
	 * @param data
	 *            data array to read
	 * @param i
	 *            index to start from
	 * @return integer composed of the 4 bytes, in proper order
	 */
	protected int toInt4(OEData data, int i) {
		return (data.get(i) & 0xff) | ((data.get(i + 1) & 0xff) << 8) | ((data.get(i + 2) & 0xff) << 16) | ((data.get(i + 3) & 0xff) << 24);
	}

	protected long toInt4_2(OEData data, int i) {
		return (data.get(i) & 0xff) | ((data.get(i + 1) & 0xff) << 8) | ((data.get(i + 2) & 0xff) << 16) | (((long) data.get(i + 3) & 0xff) << 24);
	}

	/**
	 * Converts 8 bytes to an array of 2 ints.
	 * 
	 * @param data
	 *            data array to read
	 * @param i
	 *            index to start from
	 * @return integer array of length 2, with 2 read integers
	 */
	protected int[] toInt8(OEData data, int i) {
		int[] ret = new int[2];
		ret[0] = toInt4(data, i);
		ret[1] = toInt4(data, i + 4);
		return ret;
	}

	protected long[] toInt8_2(OEData data, int i) {
		long[] ret = new long[2];
		ret[0] = toInt4_2(data, i);
		ret[1] = toInt4_2(data, i + 4);
		return ret;
	}

	/**
	 * Reads a string from data, starting at index i.
	 * 
	 * @param data
	 *            data array to read
	 * @param i
	 *            index to start from
	 * @return {@link java.lang.String String} containing the read data string
	 */
	protected String toString(OEData data, int i) {
		String ret = "";
		for (int x = i; data.get(x) != 0; x++) {
			ret += (char) data.get(x);
		}
		return ret;
	}

	protected String toString2(OEData data, int i) {
		int j = i;
		for (int x = i; x < data.length; x++)
			if (data.get(x) == 0) {
				j = x;
				break;
			}

		try {
			return new String(data.get(i, j - i), "windows-1252");

		} catch (UnsupportedEncodingException e) {
			return "";
		}
	}
}
