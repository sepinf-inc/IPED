/*
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

import java.io.UnsupportedEncodingException;

/**
 * An implementation of the LZFu algorithm to decompress RTF content
 * 
 * @author Richard Johnson
 */
public class LZFu {

    public static final String LZFU_HEADER = "{\\rtf1\\ansi\\mac\\deff0\\deftab720{\\fonttbl;}{\\f0\\fnil \\froman \\fswiss \\fmodern \\fscript \\fdecor MS Sans SerifSymbolArialTimes New RomanCourier{\\colortbl\\red0\\green0\\blue0\n\r\\par \\pard\\plain\\f0\\fs20\\b\\i\\u\\tab\\tx";

    public static String decode(byte[] data) throws PSTException {

        @SuppressWarnings("unused")
        int compressedSize = (int) PSTObject.convertLittleEndianBytesToLong(data, 0, 4);
        int uncompressedSize = (int) PSTObject.convertLittleEndianBytesToLong(data, 4, 8);
        int compressionSig = (int) PSTObject.convertLittleEndianBytesToLong(data, 8, 12);
        @SuppressWarnings("unused")
        int compressedCRC = (int) PSTObject.convertLittleEndianBytesToLong(data, 12, 16);

        if (compressionSig == 0x75465a4c) {
            // we are compressed...
            byte[] output = new byte[uncompressedSize];
            int outputPosition = 0;
            byte[] lzBuffer = new byte[4096];
            // preload our buffer.
            try {
                byte[] bytes = LZFU_HEADER.getBytes("US-ASCII");
                System.arraycopy(bytes, 0, lzBuffer, 0, LZFU_HEADER.length());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            int bufferPosition = LZFU_HEADER.length();
            int currentDataPosition = 16;

            // next byte is the flags,
            while (currentDataPosition < data.length - 2 && outputPosition < output.length) {
                int flags = data[currentDataPosition++] & 0xFF;
                for (int x = 0; x < 8 && outputPosition < output.length; x++) {
                    boolean isRef = ((flags & 1) == 1);
                    flags >>= 1;
                    if (isRef) {
                        // get the starting point for the buffer and the
                        // length to read
                        int refOffsetOrig = data[currentDataPosition++] & 0xFF;
                        int refSizeOrig = data[currentDataPosition++] & 0xFF;
                        int refOffset = (refOffsetOrig << 4) | (refSizeOrig >>> 4);
                        int refSize = (refSizeOrig & 0xF) + 2;
                        // refOffset &= 0xFFF;
                        try {
                            // copy the data from the buffer
                            int index = refOffset;
                            for (int y = 0; y < refSize && outputPosition < output.length; y++) {
                                output[outputPosition++] = lzBuffer[index];
                                lzBuffer[bufferPosition] = lzBuffer[index];
                                bufferPosition++;
                                bufferPosition %= 4096;
                                ++index;
                                index %= 4096;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } else {
                        // copy the byte over
                        lzBuffer[bufferPosition] = data[currentDataPosition];
                        bufferPosition++;
                        bufferPosition %= 4096;
                        output[outputPosition++] = data[currentDataPosition++];
                    }
                }
            }

            if (outputPosition != uncompressedSize) {
                throw new PSTException(String.format("Error decompressing RTF! Expected %d bytes, got %d bytes\n",
                        uncompressedSize, outputPosition));
            }
            return new String(output).trim();

        } else if (compressionSig == 0x414c454d) {
            // we are not compressed!
            // just return the rest of the contents as a string
            byte[] output = new byte[data.length - 16];
            System.arraycopy(data, 16, output, 0, data.length - 16);
            return new String(output).trim();
        }

        return "";
    }
}
