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

import java.io.IOException;

/**
 * The descriptor items contain information that describes a PST object. This is
 * like extended table entries, usually when the data cannot fit in a
 * traditional table item.
 * 
 * @author Richard Johnson
 */
class PSTDescriptorItem {
    PSTDescriptorItem(byte[] data, int offset, PSTFile pstFile) {
        this.pstFile = pstFile;

        if (pstFile.getPSTFileType() == PSTFile.PST_TYPE_ANSI) {
            descriptorIdentifier = (int) PSTObject.convertLittleEndianBytesToLong(data, offset, offset + 4);
            offsetIndexIdentifier = ((int) PSTObject.convertLittleEndianBytesToLong(data, offset + 4, offset + 8))
                    & 0xfffffffe;
            subNodeOffsetIndexIdentifier = (int) PSTObject.convertLittleEndianBytesToLong(data, offset + 8, offset + 12)
                    & 0xfffffffe;
        } else {
            descriptorIdentifier = (int) PSTObject.convertLittleEndianBytesToLong(data, offset, offset + 4);
            offsetIndexIdentifier = ((int) PSTObject.convertLittleEndianBytesToLong(data, offset + 8, offset + 16))
                    & 0xfffffffe;
            subNodeOffsetIndexIdentifier = (int) PSTObject.convertLittleEndianBytesToLong(data, offset + 16,
                    offset + 24) & 0xfffffffe;
        }
    }

    public byte[] getData() throws IOException, PSTException {
        if (dataBlockData != null) {
            return dataBlockData;
        }

        PSTNodeInputStream in = pstFile.readLeaf(offsetIndexIdentifier);
        byte[] out = new byte[(int) in.length()];
        in.read(out);
        dataBlockData = out;
        return dataBlockData;
    }

    public int[] getBlockOffsets() throws IOException, PSTException {
        if (dataBlockOffsets != null) {

            return dataBlockOffsets;
        }
        Long[] offsets = pstFile.readLeaf(offsetIndexIdentifier).getBlockOffsets();
        int[] offsetsOut = new int[offsets.length];
        for (int x = 0; x < offsets.length; x++) {
            offsetsOut[x] = offsets[x].intValue();
        }
        return offsetsOut;
    }

    public int getDataSize() throws IOException, PSTException {
        return pstFile.getLeafSize(offsetIndexIdentifier);
    }

    // Public data
    int descriptorIdentifier;
    int offsetIndexIdentifier;
    int subNodeOffsetIndexIdentifier;

    // These are private to ensure that getData()/getBlockOffets() are used
    // private PSTFile.PSTFileBlock dataBlock = null;
    byte[] dataBlockData = null;
    int[] dataBlockOffsets = null;
    private PSTFile pstFile;

    @Override
    public String toString() {
        return "PSTDescriptorItem\n" + "   descriptorIdentifier: " + descriptorIdentifier + "\n"
                + "   offsetIndexIdentifier: " + offsetIndexIdentifier + "\n" + "   subNodeOffsetIndexIdentifier: "
                + subNodeOffsetIndexIdentifier + "\n";

    }

}
