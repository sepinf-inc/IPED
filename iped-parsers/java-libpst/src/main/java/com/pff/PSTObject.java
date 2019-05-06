/**
 * Copyright 2010 Richard Johnson & Orin Eman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ---
 *
 * This file is part of java-libpst.
 *
 * java-libpst is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-libpst is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with java-libpst.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.pff;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * PST Object is the root class of all PST Items. It also provides a number of
 * static utility functions. The most important of which is the
 * detectAndLoadPSTObject call which allows extraction of a PST Item from the
 * file.
 * 
 * @author Richard Johnson
 */
public class PSTObject {

	public static final int NID_TYPE_HID = 0x00; // Heap node
	public static final int NID_TYPE_INTERNAL = 0x01; // Internal node (section
														// 2.4.1)
	public static final int NID_TYPE_NORMAL_FOLDER = 0x02; // Normal Folder
															// object (PC)
	public static final int NID_TYPE_SEARCH_FOLDER = 0x03; // Search Folder
															// object (PC)
	public static final int NID_TYPE_NORMAL_MESSAGE = 0x04; // Normal Message
															// object (PC)
	public static final int NID_TYPE_ATTACHMENT = 0x05; // Attachment object
														// (PC)
	public static final int NID_TYPE_SEARCH_UPDATE_QUEUE = 0x06; // Queue of
																	// changed
																	// objects
																	// for
																	// search
																	// Folder
																	// objects
	public static final int NID_TYPE_SEARCH_CRITERIA_OBJECT = 0x07; // Defines
																	// the
																	// search
																	// criteria
																	// for a
																	// search
																	// Folder
																	// object
	public static final int NID_TYPE_ASSOC_MESSAGE = 0x08; // Folder associated
															// information (FAI)
															// Message object
															// (PC)
	public static final int NID_TYPE_CONTENTS_TABLE_INDEX = 0x0A; // Internal,
																	// persisted
																	// view-related
	public static final int NID_TYPE_RECEIVE_FOLDER_TABLE = 0X0B; // Receive
																	// Folder
																	// object
																	// (Inbox)
	public static final int NID_TYPE_OUTGOING_QUEUE_TABLE = 0x0C; // Outbound
																	// queue
																	// (Outbox)
	public static final int NID_TYPE_HIERARCHY_TABLE = 0x0D; // Hierarchy table
																// (TC)
	public static final int NID_TYPE_CONTENTS_TABLE = 0x0E; // Contents table
															// (TC)
	public static final int NID_TYPE_ASSOC_CONTENTS_TABLE = 0x0F; // FAI
																	// contents
																	// table
																	// (TC)
	public static final int NID_TYPE_SEARCH_CONTENTS_TABLE = 0x10; // Contents
																	// table
																	// (TC) of a
																	// search
																	// Folder
																	// object
	public static final int NID_TYPE_ATTACHMENT_TABLE = 0x11; // Attachment
																// table (TC)
	public static final int NID_TYPE_RECIPIENT_TABLE = 0x12; // Recipient table
																// (TC)
	public static final int NID_TYPE_SEARCH_TABLE_INDEX = 0x13; // Internal,
																// persisted
																// view-related
	public static final int NID_TYPE_LTP = 0x1F; // LTP

	public String getItemsString() {
		return items.toString();
	}

	protected PSTFile pstFile;
	protected byte[] data;
	protected DescriptorIndexNode descriptorIndexNode;
	protected HashMap<Integer, PSTTableBCItem> items;
	protected HashMap<Integer, PSTDescriptorItem> localDescriptorItems = null;

	protected LinkedHashMap<String, HashMap<DescriptorIndexNode, PSTObject>> children;

	protected PSTObject(PSTFile theFile, DescriptorIndexNode descriptorIndexNode) throws PSTException, IOException {
		this.pstFile = theFile;
		this.descriptorIndexNode = descriptorIndexNode;

		// descriptorIndexNode.readData(theFile);
		// PSTTableBC table = new PSTTableBC(descriptorIndexNode.dataBlock.data,
		// descriptorIndexNode.dataBlock.blockOffsets);
		PSTTableBC table = new PSTTableBC(new PSTNodeInputStream(pstFile, pstFile.getOffsetIndexNode(descriptorIndexNode.dataOffsetIndexIdentifier)));
		// System.out.println(table);
		this.items = table.getItems();

		if (descriptorIndexNode.localDescriptorsOffsetIndexIdentifier != 0) {
			// PSTDescriptor descriptor = new PSTDescriptor(theFile,
			// descriptorIndexNode.localDescriptorsOffsetIndexIdentifier);
			// localDescriptorItems = descriptor.getChildren();
			this.localDescriptorItems = theFile.getPSTDescriptorItems(descriptorIndexNode.localDescriptorsOffsetIndexIdentifier);
		}
	}

	/**
	 * for pre-population
	 * 
	 * @param theFile
	 * @param folderIndexNode
	 * @param table
	 */
	protected PSTObject(PSTFile theFile, DescriptorIndexNode folderIndexNode, PSTTableBC table, HashMap<Integer, PSTDescriptorItem> localDescriptorItems) {
		this.pstFile = theFile;
		this.descriptorIndexNode = folderIndexNode;
		this.items = table.getItems();
		this.table = table;
		this.localDescriptorItems = localDescriptorItems;
	}

	protected PSTTableBC table;

	/**
	 * get the descriptor node for this item this identifies the location of the
	 * node in the BTree and associated info
	 * 
	 * @return item's descriptor node
	 */
	public DescriptorIndexNode getDescriptorNode() {
		return this.descriptorIndexNode;
	}

	/**
	 * get the descriptor identifier for this item can be used for loading
	 * objects through detectAndLoadPSTObject(PSTFile theFile, long
	 * descriptorIndex)
	 * 
	 * @return item's descriptor node identifier
	 */
	public long getDescriptorNodeId() {
		return this.descriptorIndexNode.descriptorIdentifier;
	}

	public int getNodeType() {
		return PSTObject.getNodeType(this.descriptorIndexNode.descriptorIdentifier);
	}

	public static int getNodeType(int descriptorIdentifier) {
		return descriptorIdentifier & 0x1F;
	}

	protected int getIntItem(int identifier) {
		return getIntItem(identifier, 0);
	}

	protected int getIntItem(int identifier, int defaultValue) {
		if (this.items.containsKey(identifier)) {
			PSTTableBCItem item = this.items.get(identifier);
			return item.entryValueReference;
		}
		return defaultValue;
	}

	protected boolean getBooleanItem(int identifier) {
		return getBooleanItem(identifier, false);
	}

	protected boolean getBooleanItem(int identifier, boolean defaultValue) {
		if (this.items.containsKey(identifier)) {
			PSTTableBCItem item = this.items.get(identifier);
			return item.entryValueReference != 0;
		}
		return defaultValue;
	}

	protected double getDoubleItem(int identifier) {
		return getDoubleItem(identifier, 0);
	}

	protected double getDoubleItem(int identifier, double defaultValue) {
		if (this.items.containsKey(identifier)) {
			PSTTableBCItem item = this.items.get(identifier);
			long longVersion = PSTObject.convertLittleEndianBytesToLong(item.data);
			return Double.longBitsToDouble(longVersion);
		}
		return defaultValue;
	}

	protected long getLongItem(int identifier) {
		return getLongItem(identifier, 0);
	}

	protected long getLongItem(int identifier, long defaultValue) {
		if (this.items.containsKey(identifier)) {
			PSTTableBCItem item = this.items.get(identifier);
			if (item.entryValueType == 0x0003) {
				// we are really just an int
				return item.entryValueReference;
			} else if (item.entryValueType == 0x0014) {
				// we are a long
				if (item.data != null && item.data.length == 8) {
					return PSTObject.convertLittleEndianBytesToLong(item.data, 0, 8);
				} else {
					System.err.printf("Invalid data length for long id 0x%04X\n", identifier);
					// Return the default value for now...
				}
			}
		}
		return defaultValue;
	}

	protected String getStringItem(int identifier) {
		return getStringItem(identifier, 0);
	}

	protected String getStringItem(int identifier, int stringType) {
		return getStringItem(identifier, stringType, null);
	}

	protected String getStringItem(int identifier, int stringType, String codepage) {
		PSTTableBCItem item = this.items.get(identifier);
		if (item != null) {

			if (codepage == null) {
				codepage = this.getStringCodepage();
			}

			// get the string type from the item if not explicitly set
			if (stringType == 0) {
				stringType = item.entryValueType;
			}

			// see if there is a descriptor entry
			if (!item.isExternalValueReference) {
				// System.out.println("here: "+new
				// String(item.data)+this.descriptorIndexNode.descriptorIdentifier);
				return PSTObject.createJavaString(item.data, stringType, codepage);
			}
			if (this.localDescriptorItems != null && this.localDescriptorItems.containsKey(item.entryValueReference)) {
				// we have a hit!
				PSTDescriptorItem descItem = this.localDescriptorItems.get(item.entryValueReference);

				try {
					byte[] data = descItem.getData();
					if (data == null) {
						return "";
					}

					return PSTObject.createJavaString(data, stringType, codepage);
				} catch (Exception e) {
					System.err.printf("Exception %s decoding string %s: %s\n", e.toString(), PSTFile.getPropertyDescription(identifier, stringType), data != null ? data.toString() : "null");
					return "";
				}
				// System.out.printf("PSTObject.getStringItem - item isn't a string: 0x%08X\n",
				// identifier);
				// return "";
			}

			return PSTObject.createJavaString(data, stringType, codepage);
		}
		return "";
	}
	
	public static String tryDecodeUnknowCharset(byte[] data) {

		try {

			int count0 = 0, max = 100000;
			if (data.length < max)
				max = data.length;

			for (int i = 0; i < max; i++)
				if (data[i] == 0)
					count0++;
			if (count0 > 0 && count0 * 2 >= max * 0.9)
				return new String(data, "UTF-16LE");

			boolean hasUtf8 = false;
			for (int i = 0; i < max - 1; i++)
				if (data[i] == (byte) 0xC3 && data[i + 1] >= (byte) 0x80 && data[i + 1] <= (byte) 0xBC) {
					hasUtf8 = true;
					break;
				}
			if (hasUtf8)
				return new String(data, "UTF-8");

			return new String(data, "windows-1252");

		} catch (UnsupportedEncodingException e) {
			return new String(data);
		}

	}

	static String createJavaString(byte[] data, int stringType, String codepage) {
		try {
		    if(data == null)
		        return "";
		    
			if (stringType == 0x1F) {
				return new String(data, "UTF-16LE");
			}
			
			if (codepage == null) {
				// patch Nassif: decodifica emails com codificação desconhecida
				return tryDecodeUnknowCharset(data);
				
			} else {
				codepage = codepage.toUpperCase();
				return new String(data, codepage);
			}
			
			/*
			if (codepage == null || codepage.toUpperCase().equals("UTF-8") || codepage.toUpperCase().equals("UTF-7")) {
				// PST UTF-8 strings are not... really UTF-8
				// it seems that they just don't use multibyte chars at all.
				// indeed, with some crylic chars in there, the difficult chars are just converted to %3F(?)
				// I suspect that outlook actually uses RTF to store these problematic strings.
				StringBuffer sbOut = new StringBuffer();
				for (int x = 0; x < data.length; x++) {
					sbOut.append((char)(data[x] & 0xFF)); // just blindly accept the byte as a UTF char, seems right half the time
				}
				return new String(sbOut);
			} else {
				codepage = codepage.toUpperCase();
				return new String(data, codepage);
			}
			 */

		} catch (Exception err) {
			System.err.println("Unable to decode string");
			err.printStackTrace();
			return "";
		}
	}

	private String getStringCodepage() {
		// try and get the codepage
		PSTTableBCItem cpItem = this.items.get(0x3FFD); // PidTagMessageCodepage
		if (cpItem == null) {
			cpItem = this.items.get(0x3FDE); // PidTagInternetCodepage
		}
		if (cpItem != null) {
			return PSTFile.getInternetCodePageCharset(cpItem.entryValueReference);
		}
		return null;
	}

	public Date getDateItem(int identifier) {
		if (this.items.containsKey(identifier)) {
			PSTTableBCItem item = this.items.get(identifier);
			if (item.data.length == 0) {
				return new Date(0);
			}
			int high = (int) PSTObject.convertLittleEndianBytesToLong(item.data, 4, 8);
			int low = (int) PSTObject.convertLittleEndianBytesToLong(item.data, 0, 4);

			return PSTObject.filetimeToDate(high, low);
		}
		return null;
	}

	protected byte[] getBinaryItem(int identifier) {
		if (this.items.containsKey(identifier)) {
			PSTTableBCItem item = this.items.get(identifier);
			if (item.entryValueType == 0x0102) {
				if (!item.isExternalValueReference) {
					return item.data;
				}
				if (this.localDescriptorItems != null && this.localDescriptorItems.containsKey(item.entryValueReference)) {
					// we have a hit!
					PSTDescriptorItem descItem = this.localDescriptorItems.get(item.entryValueReference);
					try {
						return descItem.getData();
					} catch (Exception e) {
						System.err.printf("Exception reading binary item: reference 0x%08X\n", item.entryValueReference);

						return null;
					}
				}

				// System.out.println("External reference!!!\n");
			}
		}
		return null;
	}

	protected PSTTimeZone getTimeZoneItem(int identifier) {
		byte[] tzData = getBinaryItem(identifier);
		if (tzData != null && tzData.length != 0) {
			return new PSTTimeZone(tzData);
		}
		return null;
	}

	public String getMessageClass() {
		return this.getStringItem(0x001a);
	}

	@Override
	public String toString() {
		return this.localDescriptorItems + "\n" + (this.items);
	}

	/**
	 * These are the common properties, some don't really appear to be common
	 * across folders and emails, but hey
	 */

	/**
	 * get the display name
	 */
	public String getDisplayName() {
		return this.getStringItem(0x3001);
	}

	/**
	 * Address type Known values are SMTP, EX (Exchange) and UNKNOWN
	 */
	public String getAddrType() {
		return this.getStringItem(0x3002);
	}

	/**
	 * E-mail address
	 */
	public String getEmailAddress() {
		return this.getStringItem(0x3003);
	}

	/**
	 * Comment
	 */
	public String getComment() {
		return this.getStringItem(0x3004);
	}

	/**
	 * Creation time
	 */
	public Date getCreationTime() {
		return this.getDateItem(0x3007);
	}

	/**
	 * Modification time
	 */
	public Date getLastModificationTime() {
		return this.getDateItem(0x3008);
	}

	/**
	 * Static stuff below ------------------
	 */

	// substitution table for the compressible encryption type.
	static int[] compEnc = { 0x47, 0xf1, 0xb4, 0xe6, 0x0b, 0x6a, 0x72, 0x48, 0x85, 0x4e, 0x9e, 0xeb, 0xe2, 0xf8, 0x94, 0x53, 0xe0, 0xbb, 0xa0, 0x02, 0xe8, 0x5a, 0x09, 0xab, 0xdb, 0xe3, 0xba, 0xc6,
			0x7c, 0xc3, 0x10, 0xdd, 0x39, 0x05, 0x96, 0x30, 0xf5, 0x37, 0x60, 0x82, 0x8c, 0xc9, 0x13, 0x4a, 0x6b, 0x1d, 0xf3, 0xfb, 0x8f, 0x26, 0x97, 0xca, 0x91, 0x17, 0x01, 0xc4, 0x32, 0x2d, 0x6e,
			0x31, 0x95, 0xff, 0xd9, 0x23, 0xd1, 0x00, 0x5e, 0x79, 0xdc, 0x44, 0x3b, 0x1a, 0x28, 0xc5, 0x61, 0x57, 0x20, 0x90, 0x3d, 0x83, 0xb9, 0x43, 0xbe, 0x67, 0xd2, 0x46, 0x42, 0x76, 0xc0, 0x6d,
			0x5b, 0x7e, 0xb2, 0x0f, 0x16, 0x29, 0x3c, 0xa9, 0x03, 0x54, 0x0d, 0xda, 0x5d, 0xdf, 0xf6, 0xb7, 0xc7, 0x62, 0xcd, 0x8d, 0x06, 0xd3, 0x69, 0x5c, 0x86, 0xd6, 0x14, 0xf7, 0xa5, 0x66, 0x75,
			0xac, 0xb1, 0xe9, 0x45, 0x21, 0x70, 0x0c, 0x87, 0x9f, 0x74, 0xa4, 0x22, 0x4c, 0x6f, 0xbf, 0x1f, 0x56, 0xaa, 0x2e, 0xb3, 0x78, 0x33, 0x50, 0xb0, 0xa3, 0x92, 0xbc, 0xcf, 0x19, 0x1c, 0xa7,
			0x63, 0xcb, 0x1e, 0x4d, 0x3e, 0x4b, 0x1b, 0x9b, 0x4f, 0xe7, 0xf0, 0xee, 0xad, 0x3a, 0xb5, 0x59, 0x04, 0xea, 0x40, 0x55, 0x25, 0x51, 0xe5, 0x7a, 0x89, 0x38, 0x68, 0x52, 0x7b, 0xfc, 0x27,
			0xae, 0xd7, 0xbd, 0xfa, 0x07, 0xf4, 0xcc, 0x8e, 0x5f, 0xef, 0x35, 0x9c, 0x84, 0x2b, 0x15, 0xd5, 0x77, 0x34, 0x49, 0xb6, 0x12, 0x0a, 0x7f, 0x71, 0x88, 0xfd, 0x9d, 0x18, 0x41, 0x7d, 0x93,
			0xd8, 0x58, 0x2c, 0xce, 0xfe, 0x24, 0xaf, 0xde, 0xb8, 0x36, 0xc8, 0xa1, 0x80, 0xa6, 0x99, 0x98, 0xa8, 0x2f, 0x0e, 0x81, 0x65, 0x73, 0xe4, 0xc2, 0xa2, 0x8a, 0xd4, 0xe1, 0x11, 0xd0, 0x08,
			0x8b, 0x2a, 0xf2, 0xed, 0x9a, 0x64, 0x3f, 0xc1, 0x6c, 0xf9, 0xec };

	/**
	 * Output a dump of data in hex format in the order it was read in
	 * 
	 * @param data
	 * @param pretty
	 */
	public static void printHexFormatted(byte[] data, boolean pretty) {
		printHexFormatted(data, pretty, new int[0]);
	}

	protected static void printHexFormatted(byte[] data, boolean pretty, int[] indexes) {
		// groups of two
		if (pretty) {
			System.out.println("---");
		}
		long tmpLongValue;
		String line = "";
		int nextIndex = 0;
		int indexIndex = 0;
		if (indexes.length > 0) {
			nextIndex = indexes[0];
			indexIndex++;
		}
		for (int x = 0; x < data.length; x++) {
			tmpLongValue = (long) data[x] & 0xff;

			if (indexes.length > 0 && x == nextIndex && nextIndex < data.length) {
				System.out.print("+");
				line += "+";
				while (indexIndex < indexes.length - 1 && indexes[indexIndex] <= nextIndex) {
					indexIndex++;
				}
				nextIndex = indexes[indexIndex];
				// indexIndex++;
			}

			if (Character.isLetterOrDigit((char) tmpLongValue)) {
				line += (char) tmpLongValue;
			} else {
				line += ".";
			}

			if (Long.toHexString(tmpLongValue).length() < 2) {
				System.out.print("0");
			}
			System.out.print(Long.toHexString(tmpLongValue));
			if (x % 2 == 1 && pretty) {
				System.out.print(" ");
			}
			if (x % 16 == 15 && pretty) {
				System.out.print(" " + line);
				System.out.println("");
				line = "";
			}
		}
		if (pretty) {
			System.out.println(" " + line);
			System.out.println("---");
			System.out.println(data.length);
		} else {
		}
	}

	/**
	 * decode a lump of data that has been encrypted with the compressible
	 * encryption
	 * 
	 * @param data
	 * @return decoded data
	 */
	protected static byte[] decode(byte[] data) {
		int temp;
		for (int x = 0; x < data.length; x++) {
			temp = data[x] & 0xff;
			data[x] = (byte) compEnc[temp];
		}

		return data;
	}

	protected static byte[] encode(byte[] data) {
		// create the encoding array...
		int[] enc = new int[compEnc.length];
		for (int x = 0; x < enc.length; x++) {
			enc[compEnc[x]] = x;
		}

		// now it's just the same as decode...
		int temp;
		for (int x = 0; x < data.length; x++) {
			temp = data[x] & 0xff;
			data[x] = (byte) enc[temp];
		}

		return data;
	}

	/**
	 * Utility function for converting little endian bytes into a usable java
	 * long
	 * 
	 * @param data
	 * @return long version of the data
	 */
	public static long convertLittleEndianBytesToLong(byte[] data) {
		return convertLittleEndianBytesToLong(data, 0, data.length);
	}

	/**
	 * Utility function for converting little endian bytes into a usable java
	 * long
	 * 
	 * @param data
	 * @param start
	 * @param end
	 * @return long version of the data
	 */
	public static long convertLittleEndianBytesToLong(byte[] data, int start, int end) {

		long offset = data[end - 1] & 0xff;
		long tmpLongValue;
		for (int x = end - 2; x >= start; x--) {
			offset = offset << 8;
			tmpLongValue = (long) data[x] & 0xff;
			offset |= tmpLongValue;
		}

		return offset;
	}

	/**
	 * Utility function for converting big endian bytes into a usable java long
	 * 
	 * @param data
	 * @param start
	 * @param end
	 * @return long version of the data
	 */
	public static long convertBigEndianBytesToLong(byte[] data, int start, int end) {

		long offset = 0;
		for (int x = start; x < end; ++x) {
			offset = offset << 8;
			offset |= (data[x] & 0xFFL);
		}

		return offset;
	}

	/*
	 * protected static boolean isPSTArray(byte[] data) { return (data[0] == 1
	 * && data[1] == 1); } /*
	 */
	/*
	 * protected static int[] getBlockOffsets(RandomAccessFile in, byte[] data)
	 * throws IOException, PSTException { // is the data an array? if (!(data[0]
	 * == 1 && data[1] == 1)) { throw new
	 * PSTException("Unable to process array, does not appear to be one!"); }
	 * 
	 * // we are an array! // get the array items and merge them together int
	 * numberOfEntries = (int)PSTObject.convertLittleEndianBytesToLong(data, 2,
	 * 4); int[] output = new int[numberOfEntries]; int tableOffset = 8; int
	 * blockOffset = 0; for (int y = 0; y < numberOfEntries; y++) { // get the
	 * offset identifier long tableOffsetIdentifierIndex =
	 * PSTObject.convertLittleEndianBytesToLong(data, tableOffset,
	 * tableOffset+8); // clear the last bit of the identifier. Why so hard?
	 * tableOffsetIdentifierIndex = (tableOffsetIdentifierIndex & 0xfffffffe);
	 * OffsetIndexItem tableOffsetIdentifier = PSTObject.getOffsetIndexNode(in,
	 * tableOffsetIdentifierIndex); blockOffset += tableOffsetIdentifier.size;
	 * output[y] = blockOffset; tableOffset += 8; }
	 * 
	 * // replace the item data with the stuff from the array... return output;
	 * } /*
	 */

	/**
	 * Detect and load a PST Object from a file with the specified descriptor
	 * index
	 * 
	 * @param theFile
	 * @param descriptorIndex
	 * @return PSTObject with that index
	 * @throws IOException
	 * @throws PSTException
	 */
	public static PSTObject detectAndLoadPSTObject(PSTFile theFile, long descriptorIndex) throws IOException, PSTException {
		return PSTObject.detectAndLoadPSTObject(theFile, theFile.getDescriptorIndexNode(descriptorIndex));
	}

	/**
	 * Detect and load a PST Object from a file with the specified descriptor
	 * index
	 * 
	 * @param theFile
	 * @param folderIndexNode
	 * @return PSTObject with that index
	 * @throws IOException
	 * @throws PSTException
	 */
	static PSTObject detectAndLoadPSTObject(PSTFile theFile, DescriptorIndexNode folderIndexNode) throws IOException, PSTException {
		int nidType = (folderIndexNode.descriptorIdentifier & 0x1F);
		if (nidType == 0x02 || nidType == 0x03 || nidType == 0x04) {

			PSTTableBC table = new PSTTableBC(new PSTNodeInputStream(theFile, theFile.getOffsetIndexNode(folderIndexNode.dataOffsetIndexIdentifier)));

			HashMap<Integer, PSTDescriptorItem> localDescriptorItems = null;
			if (folderIndexNode.localDescriptorsOffsetIndexIdentifier != 0) {
				localDescriptorItems = theFile.getPSTDescriptorItems(folderIndexNode.localDescriptorsOffsetIndexIdentifier);
			}

			if (nidType == 0x02 || nidType == 0x03) {
				return new PSTFolder(theFile, folderIndexNode, table, localDescriptorItems);
			} else {
				return PSTObject.createAppropriatePSTMessageObject(theFile, folderIndexNode, table, localDescriptorItems);
			}
		} else {
			throw new PSTException("Unknown child type with offset id: " + folderIndexNode.localDescriptorsOffsetIndexIdentifier);
		}
	}

	static PSTMessage createAppropriatePSTMessageObject(PSTFile theFile, DescriptorIndexNode folderIndexNode, PSTTableBC table, HashMap<Integer, PSTDescriptorItem> localDescriptorItems) {

		PSTTableBCItem item = table.getItems().get(0x001a);
		String messageClass = "";
		if (item != null) {
			messageClass = item.getStringValue();
		}

		if (messageClass.equals("IPM.Note")) {
			return new PSTMessage(theFile, folderIndexNode, table, localDescriptorItems);
		} else if (messageClass.equals("IPM.Appointment") || messageClass.equals("IPM.OLE.CLASS.{00061055-0000-0000-C000-000000000046}") || messageClass.startsWith("IPM.Schedule.Meeting")) {
			return new PSTAppointment(theFile, folderIndexNode, table, localDescriptorItems);
		} else if (messageClass.equals("IPM.Contact")) {
			return new PSTContact(theFile, folderIndexNode, table, localDescriptorItems);
		} else if (messageClass.equals("IPM.Task")) {
			return new PSTTask(theFile, folderIndexNode, table, localDescriptorItems);
		} else if (messageClass.equals("IPM.Activity")) {
			return new PSTActivity(theFile, folderIndexNode, table, localDescriptorItems);
		} else if (messageClass.equals("IPM.Post.Rss")) {
			return new PSTRss(theFile, folderIndexNode, table, localDescriptorItems);
		} else {
			// System.err.println("Unknown message type: "+messageClass);
		}

		return new PSTMessage(theFile, folderIndexNode, table, localDescriptorItems);
	}

	static String guessPSTObjectType(PSTFile theFile, DescriptorIndexNode folderIndexNode) throws IOException, PSTException {

		PSTTableBC table = new PSTTableBC(new PSTNodeInputStream(theFile, theFile.getOffsetIndexNode(folderIndexNode.dataOffsetIndexIdentifier)));

		// get the table items and look at the types we are dealing with
		Set<Integer> keySet = table.getItems().keySet();
		Iterator<Integer> iterator = keySet.iterator();

		while (iterator.hasNext()) {
			Integer key = iterator.next();
			if (key.intValue() >= 0x0001 && key.intValue() <= 0x0bff) {
				return "Message envelope";
			} else if (key.intValue() >= 0x1000 && key.intValue() <= 0x2fff) {
				return "Message content";
			} else if (key.intValue() >= 0x3400 && key.intValue() <= 0x35ff) {
				return "Message store";
			} else if (key.intValue() >= 0x3600 && key.intValue() <= 0x36ff) {
				return "Folder and address book";
			} else if (key.intValue() >= 0x3700 && key.intValue() <= 0x38ff) {
				return "Attachment";
			} else if (key.intValue() >= 0x3900 && key.intValue() <= 0x39ff) {
				return "Address book";
			} else if (key.intValue() >= 0x3a00 && key.intValue() <= 0x3bff) {
				return "Messaging user";
			} else if (key.intValue() >= 0x3c00 && key.intValue() <= 0x3cff) {
				return "Distribution list";
			}
		}
		return "Unknown";
	}

	/**
	 * the code below was taken from a random apache project
	 * http://www.koders.com
	 * /java/fidA9D4930E7443F69F32571905DD4CA01E4D46908C.aspx my bit-shifting
	 * isn't that 1337
	 */

	/**
	 * <p>
	 * The difference between the Windows epoch (1601-01-01 00:00:00) and the
	 * Unix epoch (1970-01-01 00:00:00) in milliseconds: 11644473600000L. (Use
	 * your favorite spreadsheet program to verify the correctness of this
	 * value. By the way, did you notice that you can tell from the epochs which
	 * operating system is the modern one? :-))
	 * </p>
	 */
	private static final long EPOCH_DIFF = 11644473600000L;

	/**
	 * <p>
	 * Converts a Windows FILETIME into a {@link Date}. The Windows FILETIME
	 * structure holds a date and time associated with a file. The structure
	 * identifies a 64-bit integer specifying the number of 100-nanosecond
	 * intervals which have passed since January 1, 1601. This 64-bit value is
	 * split into the two double words stored in the structure.
	 * </p>
	 * 
	 * @param high
	 *            The higher double word of the FILETIME structure.
	 * @param low
	 *            The lower double word of the FILETIME structure.
	 * @return The Windows FILETIME as a {@link Date}.
	 */
	protected static Date filetimeToDate(final int high, final int low) {
		final long filetime = ((long) high) << 32 | (low & 0xffffffffL);
		// System.out.printf("0x%X\n", filetime);
		final long ms_since_16010101 = filetime / (1000 * 10);
		final long ms_since_19700101 = ms_since_16010101 - EPOCH_DIFF;
		return new Date(ms_since_19700101);
	}

	public static Calendar apptTimeToCalendar(int minutes) {
		final long ms_since_16010101 = minutes * (60 * 1000L);
		final long ms_since_19700101 = ms_since_16010101 - EPOCH_DIFF;
		Calendar c = Calendar.getInstance(PSTTimeZone.utcTimeZone);
		c.setTimeInMillis(ms_since_19700101);
		return c;
	}

	public static Calendar apptTimeToUTC(int minutes, PSTTimeZone tz) {
		// Must convert minutes since 1/1/1601 in local time to UTC
		// There's got to be a better way of doing this...
		// First get a UTC calendar object that contains _local time_
		Calendar cUTC = PSTObject.apptTimeToCalendar(minutes);
		if (tz != null) {
			// Create an empty Calendar object with the required time zone
			Calendar cLocal = Calendar.getInstance(tz.getSimpleTimeZone());
			cLocal.clear();

			// Now transfer the local date/time from the UTC calendar object
			// to the object that knows about the time zone...
			cLocal.set(cUTC.get(Calendar.YEAR), cUTC.get(Calendar.MONTH), cUTC.get(Calendar.DATE), cUTC.get(Calendar.HOUR_OF_DAY), cUTC.get(Calendar.MINUTE), cUTC.get(Calendar.SECOND));

			// Get the true UTC from the local time calendar object.
			// Drop any milliseconds, they won't be printed anyway!
			long utcs = cLocal.getTimeInMillis() / 1000;

			// Finally, set the true UTC in the UTC calendar object
			cUTC.setTimeInMillis(utcs * 1000);
		} // else hope for the best!

		return cUTC;
	}
}
