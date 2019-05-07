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

import java.util.HashMap;

/**
 * The BC Table type. (Property Context) Used by pretty much everything.
 * 
 * @author Richard Johnson
 */
class PSTTableBC extends PSTTable {

    private HashMap<Integer, PSTTableBCItem> items = new HashMap<Integer, PSTTableBCItem>();

    private StringBuilder descBuffer = new StringBuilder();
    private boolean isDescNotYetInitiated = false;

    PSTTableBC(PSTNodeInputStream in) throws PSTException, java.io.IOException {
        super(in, new HashMap<Integer, PSTDescriptorItem>());
        // data = null; // No direct access to data!

        if (tableTypeByte != 0xffffffbc) {
            // System.out.println(Long.toHexString(this.tableTypeByte));
            throw new PSTException("unable to create PSTTableBC, table does not appear to be a bc!");
        }

        // go through each of the entries.
        // byte[] keyTableInfo = getNodeInfo(hidRoot);
        NodeInfo keyTableInfoNodeInfo = getNodeInfo(hidRoot);
        byte[] keyTableInfo = new byte[keyTableInfoNodeInfo.length()];
        keyTableInfoNodeInfo.in.seek(keyTableInfoNodeInfo.startOffset);
        keyTableInfoNodeInfo.in.read(keyTableInfo);

        // PSTObject.printHexFormatted(keyTableInfo, true);
        // System.out.println(in.length());
        // System.exit(0);
        numberOfKeys = keyTableInfo.length / (sizeOfItemKey + sizeOfItemValue);

        descBuffer.append("Number of entries: " + numberOfKeys + "\n");

        // Read the key table
        int offset = 0;
        for (int x = 0; x < numberOfKeys; x++) {

            PSTTableBCItem item = new PSTTableBCItem();
            item.itemIndex = x;
            item.entryType = (int) PSTObject.convertLittleEndianBytesToLong(keyTableInfo, offset + 0, offset + 2);
            // item.entryType =(int)in.seekAndReadLong(offset, 2);
            item.entryValueType = (int) PSTObject.convertLittleEndianBytesToLong(keyTableInfo, offset + 2, offset + 4);
            // item.entryValueType = (int)in.seekAndReadLong(offset+2, 2);
            item.entryValueReference = (int) PSTObject.convertLittleEndianBytesToLong(keyTableInfo, offset + 4,
                    offset + 8);
            // item.entryValueReference = (int)in.seekAndReadLong(offset+4, 4);

            // Data is in entryValueReference for all types <= 4 bytes long
            switch (item.entryValueType) {

                case 0x0002: // 16bit integer
                    item.entryValueReference &= 0xFFFF;
                case 0x0003: // 32bit integer
                case 0x000A: // 32bit error code
                    /*
                     * System.out.printf("Integer%s: 0x%04X:%04X, %d\n", (item.entryValueType ==
                     * 0x0002) ? "16" : "32", item.entryType, item.entryValueType,
                     * item.entryValueReference); /*
                     */
                case 0x0001: // Place-holder
                case 0x0004: // 32bit floating
                    item.isExternalValueReference = true;
                    break;

                case 0x000b: // Boolean - a single byte
                    item.entryValueReference &= 0xFF;
                    /*
                     * System.out.printf("boolean: 0x%04X:%04X, %s\n", item.entryType,
                     * item.entryValueType, (item.entryValueReference == 0) ? "false" : "true"); /*
                     */
                    item.isExternalValueReference = true;
                    break;

                case 0x000D:
                default:
                    // Is it in the local heap?
                    item.isExternalValueReference = true; // Assume not
                    // System.out.println(item.entryValueReference);
                    // byte[] nodeInfo = getNodeInfo(item.entryValueReference);
                    NodeInfo nodeInfoNodeInfo = getNodeInfo(item.entryValueReference);
                    if (nodeInfoNodeInfo == null) {
                        // It's an external reference that we don't deal with here.
                        /*
                         * System.out.printf("%s: %shid 0x%08X\n", (item.entryValueType == 0x1f ||
                         * item.entryValueType == 0x1e) ? "String" : "Other",
                         * PSTFile.getPropertyDescription(item.entryType, item.entryValueType),
                         * item.entryValueReference); /*
                         */
                    } else {
                        // Make a copy of the data
                        // item.data = new
                        // byte[nodeInfo.endOffset-nodeInfo.startOffset];
                        byte[] nodeInfo = new byte[nodeInfoNodeInfo.length()];
                        nodeInfoNodeInfo.in.seek(nodeInfoNodeInfo.startOffset);
                        nodeInfoNodeInfo.in.read(nodeInfo);
                        item.data = nodeInfo; // should be new array, so just use it
                        // System.arraycopy(nodeInfo.data, nodeInfo.startOffset,
                        // item.data, 0, item.data.length);
                        item.isExternalValueReference = false;
                        /*
                         * if ( item.entryValueType == 0x1f || item.entryValueType == 0x1e ) { try { //
                         * if ( item.entryType == 0x0037 ) { String temp = new String(item.data,
                         * item.entryValueType == 0x1E ? "UTF8" : "UTF-16LE");
                         * System.out.printf("String: 0x%04X:%04X, \"%s\"\n", item.entryType,
                         * item.entryValueType, temp); } } catch (UnsupportedEncodingException e) {
                         * e.printStackTrace(); } } else {
                         * 
                         * System.out.printf("Other: 0x%04X:%04X, %d bytes\n", item.entryType,
                         * item.entryValueType, item.data.length);
                         * 
                         * } /*
                         */
                    }
                    break;
            }

            offset = offset + 8;

            items.put(item.entryType, item);
            // description += item.toString()+"\n\n";

        }

        releaseRawData();
    }

    /**
     * get the items parsed out of this table.
     * 
     * @return
     */
    public HashMap<Integer, PSTTableBCItem> getItems() {
        return this.items;
    }

    @Override
    public String toString() {

        if (isDescNotYetInitiated) {
            isDescNotYetInitiated = false;

            for (Integer curItem : items.keySet()) {
                descBuffer.append(items.get(curItem).toString() + "\n\n");
            }
            // description += item.toString()+"\n\n";
        }

        return this.description + descBuffer.toString();
    }
}
