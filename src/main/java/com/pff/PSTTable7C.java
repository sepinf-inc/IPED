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

/*
 import java.io.UnsupportedEncodingException;
 /**/
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Specific functions for the 7c table type ("Table Context"). This is used for
 * attachments.
 * 
 * @author Richard Johnson
 */
class PSTTable7C extends PSTTable {

	private final int BLOCK_SIZE = 8176;

	private List<HashMap<Integer, PSTTable7CItem>> items = null;
	private int numberOfDataSets = 0;
	private int cCols = 0;
	private int TCI_bm = 0;
	private NodeInfo rowNodeInfo = null;
	private int TCI_1b = 0;
	private int overrideCol = -1;

	protected PSTTable7C(PSTNodeInputStream in, HashMap<Integer, PSTDescriptorItem> subNodeDescriptorItems) throws PSTException, java.io.IOException {
		this(in, subNodeDescriptorItems, -1);
	}

	protected PSTTable7C(PSTNodeInputStream in, HashMap<Integer, PSTDescriptorItem> subNodeDescriptorItems, int entityToExtract) throws PSTException, java.io.IOException {
		super(in, subNodeDescriptorItems);

		if (tableTypeByte != 0x7c) {
			// System.out.println(Long.toHexString(this.tableTypeByte));
			throw new PSTException("unable to create PSTTable7C, table does not appear to be a 7c!");
		}

		// TCINFO header is in the hidUserRoot node
		// byte[] tcHeaderNode = getNodeInfo(hidUserRoot);
		NodeInfo tcHeaderNode = getNodeInfo(hidUserRoot);
		int offset = 0;

		// get the TCINFO header information
		// int cCols =
		// (int)PSTObject.convertLittleEndianBytesToLong(tcHeaderNode, offset+1,
		// offset+2);
		cCols = (int) tcHeaderNode.seekAndReadLong(offset + 1, 1);
		@SuppressWarnings("unused")
		// int TCI_4b =
		// (int)PSTObject.convertLittleEndianBytesToLong(tcHeaderNode, offset+2,
		// offset+4);
		int TCI_4b = (int) tcHeaderNode.seekAndReadLong(offset + 2, 2);
		@SuppressWarnings("unused")
		// int TCI_2b =
		// (int)PSTObject.convertLittleEndianBytesToLong(tcHeaderNode, offset+4,
		// offset+6);
		int TCI_2b = (int) tcHeaderNode.seekAndReadLong(offset + 4, 2);
		// int TCI_1b =
		// (int)PSTObject.convertLittleEndianBytesToLong(tcHeaderNode, offset+6,
		// offset+8);
		TCI_1b = (int) tcHeaderNode.seekAndReadLong(offset + 6, 2);
		// int TCI_bm =
		// (int)PSTObject.convertLittleEndianBytesToLong(tcHeaderNode, offset+8,
		// offset+10);
		TCI_bm = (int) tcHeaderNode.seekAndReadLong(offset + 8, 2);
		// int hidRowIndex =
		// (int)PSTObject.convertLittleEndianBytesToLong(tcHeaderNode,
		// offset+10, offset+14);
		int hidRowIndex = (int) tcHeaderNode.seekAndReadLong(offset + 10, 4);
		// int hnidRows =
		// (int)PSTObject.convertLittleEndianBytesToLong(tcHeaderNode,
		// offset+14, offset+18);// was 18
		int hnidRows = (int) tcHeaderNode.seekAndReadLong(offset + 14, 4);
		// 18..22 hidIndex - deprecated

		// 22... column descriptors
		offset += 22;
		if (cCols != 0) {
			columnDescriptors = new ColumnDescriptor[cCols];

			for (int col = 0; col < cCols; ++col) {
				// columnDescriptors[col] = new ColumnDescriptor(tcHeaderNode,
				// offset);
				columnDescriptors[col] = new ColumnDescriptor(tcHeaderNode, offset);
				// System.out.println("iBit: "+col+" "
				// +columnDescriptors[col].iBit);
				if (columnDescriptors[col].id == entityToExtract) {
					overrideCol = col;
				}
				offset += 8;
			}
		}

		// if we are asking for a specific column, only get that!
		if (overrideCol > -1) {
			cCols = overrideCol + 1;
		}

		// Read the key table
		/* System.out.printf("Key table:\n"); /* */
		keyMap = new HashMap<Integer, Integer>();
		// byte[] keyTableInfo = getNodeInfo(hidRoot);
		NodeInfo keyTableInfo = getNodeInfo(hidRoot);
		numberOfKeys = keyTableInfo.length() / (sizeOfItemKey + sizeOfItemValue);
		offset = 0;
		for (int x = 0; x < numberOfKeys; x++) {
			int Context = (int) keyTableInfo.seekAndReadLong(offset, sizeOfItemKey);
			offset += sizeOfItemKey;
			int RowIndex = (int) keyTableInfo.seekAndReadLong(offset, sizeOfItemValue);
			offset += sizeOfItemValue;
			keyMap.put(Context, RowIndex);
		}

		// Read the Row Matrix
		rowNodeInfo = getNodeInfo(hnidRows);
		// numberOfDataSets = (rowNodeInfo.endOffset - rowNodeInfo.startOffset)
		// / TCI_bm;

		description += "Number of keys: " + numberOfKeys + "\n" + "Number of columns: " + cCols + "\n" + "Row Size: " + TCI_bm + "\n" + "hidRowIndex: " + hidRowIndex + "\n" + "hnidRows: " + hnidRows
				+ "\n";

		int numberOfBlocks = rowNodeInfo.length() / BLOCK_SIZE;
		int numberOfRowsPerBlock = BLOCK_SIZE / TCI_bm;
		@SuppressWarnings("unused")
		int blockPadding = BLOCK_SIZE - (numberOfRowsPerBlock * TCI_bm);
		numberOfDataSets = (numberOfBlocks * numberOfRowsPerBlock) + ((rowNodeInfo.length() % BLOCK_SIZE) / TCI_bm);
	}

	/**
	 * get all the items parsed out of this table.
	 * 
	 * @return
	 */
	List<HashMap<Integer, PSTTable7CItem>> getItems() throws PSTException, IOException {
		if (items == null) {
			items = getItems(-1, -1);
		}
		return items;
	}

	List<HashMap<Integer, PSTTable7CItem>> getItems(int startAtRecord, int numberOfRecordsToReturn) throws PSTException, IOException {
		List<HashMap<Integer, PSTTable7CItem>> itemList = new ArrayList<HashMap<Integer, PSTTable7CItem>>();

		// okay, work out the number of records we have
		int numberOfBlocks = rowNodeInfo.length() / BLOCK_SIZE;
		int numberOfRowsPerBlock = BLOCK_SIZE / TCI_bm;
		int blockPadding = BLOCK_SIZE - (numberOfRowsPerBlock * TCI_bm);
		numberOfDataSets = (numberOfBlocks * numberOfRowsPerBlock) + ((rowNodeInfo.length() % BLOCK_SIZE) / TCI_bm);

		if (startAtRecord == -1) {
			numberOfRecordsToReturn = numberOfDataSets;
			startAtRecord = 0;
		}

		// repeat the reading process for every dataset
		int currentValueArrayStart = ((startAtRecord / numberOfRowsPerBlock) * BLOCK_SIZE) + ((startAtRecord % numberOfRowsPerBlock) * TCI_bm);

		if (numberOfRecordsToReturn > this.getRowCount() - startAtRecord) {
			numberOfRecordsToReturn = this.getRowCount() - startAtRecord;
		}

		int dataSetNumber = 0;
		// while ( currentValueArrayStart + ((cCols+7)/8) + TCI_1b <=
		// rowNodeInfo.length())
		for (int rowCounter = 0; rowCounter < numberOfRecordsToReturn; rowCounter++) {
			HashMap<Integer, PSTTable7CItem> currentItem = new HashMap<Integer, PSTTable7CItem>();
			// add on some padding for block boundries?
			if (rowNodeInfo.in.getPSTFile().getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
				if (currentValueArrayStart >= BLOCK_SIZE) {
					currentValueArrayStart = currentValueArrayStart + (4) * (currentValueArrayStart / BLOCK_SIZE);
				}
				if (rowNodeInfo.startOffset + currentValueArrayStart + TCI_1b > rowNodeInfo.in.length()) {
					continue;
				}
			} else {
				if ((currentValueArrayStart % BLOCK_SIZE) > BLOCK_SIZE - TCI_bm) {
					// adjust!
					// currentValueArrayStart += 8176 - (currentValueArrayStart
					// % 8176);
					currentValueArrayStart += blockPadding;
					if (currentValueArrayStart + TCI_bm > rowNodeInfo.length()) {
						continue;
					}
				}
			}
			byte[] bitmap = new byte[(cCols + 7) / 8];
			// System.arraycopy(rowNodeInfo, currentValueArrayStart+TCI_1b,
			// bitmap, 0, bitmap.length);
			rowNodeInfo.in.seek(rowNodeInfo.startOffset + currentValueArrayStart + TCI_1b);
			rowNodeInfo.in.read(bitmap);

			// int id =
			// (int)PSTObject.convertLittleEndianBytesToLong(rowNodeInfo,
			// currentValueArrayStart, currentValueArrayStart+4);
			int id = (int) rowNodeInfo.seekAndReadLong(currentValueArrayStart, 4);

			// Put into the item map as PidTagLtpRowId (0x67F2)
			PSTTable7CItem item = new PSTTable7CItem();
			item.itemIndex = -1;
			item.entryValueType = 3;
			item.entryType = 0x67F2;
			item.entryValueReference = id;
			item.isExternalValueReference = true;
			currentItem.put(item.entryType, item);

			int col = 0;
			if (overrideCol > -1) {
				col = overrideCol;
			}
			for (; col < cCols; ++col) {
				// Does this column exist for this row?
				int bitIndex = columnDescriptors[col].iBit / 8;
				int bit = columnDescriptors[col].iBit % 8;
				if (bitIndex >= bitmap.length || (bitmap[bitIndex] & (1 << bit)) == 0) {
					// Column doesn't exist
					// System.out.printf("Col %d (0x%04X) not present\n", col,
					// columnDescriptors[col].id); /**/

					continue;
				}

				item = new PSTTable7CItem();
				item.itemIndex = col;

				item.entryValueType = columnDescriptors[col].type;
				item.entryType = columnDescriptors[col].id;
				item.entryValueReference = 0;

				switch (columnDescriptors[col].cbData) {
				case 1: // Single byte data
					// item.entryValueReference =
					// rowNodeInfo[currentValueArrayStart+columnDescriptors[col].ibData]
					// & 0xFF;
					item.entryValueReference = (int) rowNodeInfo.seekAndReadLong(currentValueArrayStart + columnDescriptors[col].ibData, 1) & 0xFF;
					item.isExternalValueReference = true;
					/*
					 * System.out.printf("\tboolean: %s %s\n",
					 * PSTFile.getPropertyDescription(item.entryType,
					 * item.entryValueType), item.entryValueReference == 0 ?
					 * "false" : "true"); /*
					 */
					break;

				case 2: // Two byte data
					/*
					 * item.entryValueReference =
					 * (rowNodeInfo[currentValueArrayStart
					 * +columnDescriptors[col].ibData] & 0xFF) |
					 * ((rowNodeInfo[currentValueArrayStart
					 * +columnDescriptors[col].ibData+1] & 0xFF) << 8);
					 */
					item.entryValueReference = (int) rowNodeInfo.seekAndReadLong(currentValueArrayStart + columnDescriptors[col].ibData, 2) & 0xFFFF;
					item.isExternalValueReference = true;
					/*
					 * short i16 = (short)item.entryValueReference;
					 * System.out.printf("\tInteger16: %s %d\n",
					 * PSTFile.getPropertyDescription(item.entryType,
					 * item.entryValueType), i16); /*
					 */
					break;

				case 8: // 8 byte data
					item.data = new byte[8];
					// System.arraycopy(rowNodeInfo,
					// currentValueArrayStart+columnDescriptors[col].ibData,
					// item.data, 0, 8);
					rowNodeInfo.in.seek(rowNodeInfo.startOffset + currentValueArrayStart + columnDescriptors[col].ibData);
					rowNodeInfo.in.read(item.data);
					/*
					 * System.out.printf("\tInteger64: %s\n",
					 * PSTFile.getPropertyDescription(item.entryType,
					 * item.entryValueType)); /*
					 */
					break;

				default:// Four byte data

					/*
					 * if (numberOfIndexLevels > 0 ) {
					 * System.out.println("here");
					 * System.out.println(rowNodeInfo.length());
					 * PSTObject.printHexFormatted(rowNodeInfo, true);
					 * System.exit(0); }
					 */

					// item.entryValueReference =
					// (int)PSTObject.convertLittleEndianBytesToLong(rowNodeInfo,
					// currentValueArrayStart+columnDescriptors[col].ibData,
					// currentValueArrayStart+columnDescriptors[col].ibData+4);
					item.entryValueReference = (int) rowNodeInfo.seekAndReadLong(currentValueArrayStart + columnDescriptors[col].ibData, 4);
					if (columnDescriptors[col].type == 0x0003 || columnDescriptors[col].type == 0x0004 || columnDescriptors[col].type == 0x000A) {
						// True 32bit data
						item.isExternalValueReference = true;
						/*
						 * System.out.printf("\tInteger32: %s %d\n",
						 * PSTFile.getPropertyDescription(item.entryType,
						 * item.entryValueType), item.entryValueReference); /*
						 */
						break;
					}

					// Variable length data so it's an hnid
					if ((item.entryValueReference & 0x1F) != 0) {
						// Some kind of external reference...
						item.isExternalValueReference = true;
						/*
						 * System.out.printf("\tOther: %s 0x%08X\n",
						 * PSTFile.getPropertyDescription(item.entryType,
						 * item.entryValueType), item.entryValueReference); /*
						 */
						break;
					}

					if (item.entryValueReference == 0) {
						/*
						 * System.out.printf("\tOther: %s 0 bytes\n",
						 * PSTFile.getPropertyDescription(item.entryType,
						 * item.entryValueType)); /*
						 */
						item.data = new byte[0];
						break;
					} else {
						NodeInfo entryInfo = getNodeInfo(item.entryValueReference);
						// Nassif patch
						if (entryInfo != null) {
							item.data = new byte[entryInfo.length()];
							// System.arraycopy(entryInfo, 0, item.data, 0,
							// item.data.length);
							entryInfo.in.seek(entryInfo.startOffset);
							entryInfo.in.read(item.data);
						} else {
							// Nassif patch
							item.data = new byte[0];
						}

					}
					/*
					 * if ( item.entryValueType != 0x001F ) {
					 * System.out.printf("\tOther: %s %d bytes\n",
					 * PSTFile.getPropertyDescription(item.entryType,
					 * item.entryValueType), item.data.length); } else { try {
					 * String s = new String(item.data, "UTF-16LE");
					 * System.out.printf("\tString: %s \"%s\"\n",
					 * PSTFile.getPropertyDescription(item.entryType,
					 * item.entryValueType), s); } catch
					 * (UnsupportedEncodingException e) { e.printStackTrace(); }
					 * } /*
					 */
					break;
				}

				currentItem.put(item.entryType, item);

				// description += item.toString()+"\n\n";
			}
			itemList.add(dataSetNumber, currentItem);
			dataSetNumber++;
			currentValueArrayStart += TCI_bm;
		}

		// System.out.println(description);

		return itemList;
	}

	class ColumnDescriptor {
		ColumnDescriptor(NodeInfo nodeInfo, int offset) throws PSTException, IOException {
			// type = (int)(PSTObject.convertLittleEndianBytesToLong(data,
			// offset, offset+2) & 0xFFFF);
			type = ((int) nodeInfo.seekAndReadLong(offset, 2) & 0xFFFF);
			// id = (int)(PSTObject.convertLittleEndianBytesToLong(data,
			// offset+2, offset+4) & 0xFFFF);
			id = (int) (nodeInfo.seekAndReadLong(offset + 2, 2) & 0xFFFF);
			// ibData = (int)(PSTObject.convertLittleEndianBytesToLong(data,
			// offset+4, offset+6) & 0xFFFF);
			ibData = (int) (nodeInfo.seekAndReadLong(offset + 4, 2) & 0xFFFF);
			// cbData = (int)data[offset+6] & 0xFF;
			cbData = nodeInfo.in.read() & 0xFF;
			// iBit = (int)data[offset+7] & 0xFF;
			iBit = nodeInfo.in.read() & 0xFF;
		}

		int type;
		int id;
		int ibData;
		int cbData;
		int iBit;
	}

	@Override
	public int getRowCount() {
		return this.numberOfDataSets;
	}

	/*
	 * Not used... public HashMap<Integer, PSTTable7CItem> getItem(int
	 * itemNumber) { if ( items == null || itemNumber >= items.size() ) { return
	 * null; }
	 * 
	 * return items.get(itemNumber); } /*
	 */
	@Override
	public String toString() {
		return this.description;
	}

	public String getItemsString() {
		if (items == null) {
			return "";
		}

		return items.toString();
	}

	ColumnDescriptor[] columnDescriptors = null;
	HashMap<Integer, Integer> keyMap = null;
}
