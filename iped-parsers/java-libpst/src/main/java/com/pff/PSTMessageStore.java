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
import java.util.UUID;

/**
 * Object that represents the message store. Not much use other than to get the
 * "name" of the PST file.
 * 
 * @author Richard Johnson
 */
public class PSTMessageStore extends PSTObject {

    PSTMessageStore(PSTFile theFile, DescriptorIndexNode descriptorIndexNode) throws PSTException, IOException {
        super(theFile, descriptorIndexNode);
    }

    /**
     * Get the tag record key, unique to this pst
     */
    public UUID getTagRecordKeyAsUUID() {
        // attempt to find in the table.
        int guidEntryType = 0x0ff9;
        if (this.items.containsKey(guidEntryType)) {
            PSTTableBCItem item = this.items.get(guidEntryType);
            int offset = 0;
            byte[] bytes = item.data;
            long mostSigBits = (PSTObject.convertLittleEndianBytesToLong(bytes, offset, offset + 4) << 32)
                    | (PSTObject.convertLittleEndianBytesToLong(bytes, offset + 4, offset + 6) << 16)
                    | PSTObject.convertLittleEndianBytesToLong(bytes, offset + 6, offset + 8);
            long leastSigBits = PSTObject.convertBigEndianBytesToLong(bytes, offset + 8, offset + 16);
            return new UUID(mostSigBits, leastSigBits);
        }
        return null;
    }

    /**
     * get the message store display name
     */
    @Override
    public String getDisplayName() {
        // attempt to find in the table.
        int displayNameEntryType = 0x3001;
        if (this.items.containsKey(displayNameEntryType)) {
            return this.getStringItem(displayNameEntryType);
            // PSTTableBCItem item =
            // (PSTTableBCItem)this.items.get(displayNameEntryType);
            // return new String(item.getStringValue());
        }
        return "";
    }

    public String getDetails() {
        return this.items.toString();
    }

}
