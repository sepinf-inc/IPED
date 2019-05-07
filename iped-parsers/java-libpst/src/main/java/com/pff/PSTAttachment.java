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
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;

/**
 * Class containing attachment information
 * 
 * @author Richard Johnson
 */
public class PSTAttachment extends PSTObject {

    PSTAttachment(PSTFile theFile, PSTTableBC table, HashMap<Integer, PSTDescriptorItem> localDescriptorItems) {
        super(theFile, null, table, localDescriptorItems);
    }

    public int getSize() {
        return this.getIntItem(0x0e20);
    }

    @Override
    public Date getCreationTime() {
        return this.getDateItem(0x3007);
    }

    public Date getModificationTime() {
        return this.getDateItem(0x3008);
    }

    public PSTMessage getEmbeddedPSTMessage() throws IOException, PSTException {
        PSTNodeInputStream in = null;
        if (getIntItem(0x3705) == PSTAttachment.ATTACHMENT_METHOD_EMBEDDED) {
            PSTTableBCItem item = items.get(0x3701);
            if (item.entryValueType == 0x0102) {
                if (!item.isExternalValueReference) {
                    in = new PSTNodeInputStream(this.pstFile, item.data);
                } else {
                    // We are in trouble!
                    throw new PSTException("External reference in getEmbeddedPSTMessage()!\n");
                }
            } else if (item.entryValueType == 0x000D) {
                int descriptorItem = (int) PSTObject.convertLittleEndianBytesToLong(item.data, 0, 4);
                // PSTObject.printHexFormatted(item.data, true);
                PSTDescriptorItem descriptorItemNested = this.localDescriptorItems.get(descriptorItem);
                in = new PSTNodeInputStream(this.pstFile, descriptorItemNested);
                this.localDescriptorItems
                        .putAll(pstFile.getPSTDescriptorItems(descriptorItemNested.subNodeOffsetIndexIdentifier));
                /*
                 * if ( descriptorItemNested != null ) { try { data =
                 * descriptorItemNested.getData(); blockOffsets =
                 * descriptorItemNested.getBlockOffsets(); } catch (Exception e) {
                 * e.printStackTrace();
                 * 
                 * data = null; blockOffsets = null; } }
                 */
            }

            if (in == null) {
                return null;
            }

            try {
                PSTTableBC attachmentTable = new PSTTableBC(in);
                return PSTObject.createAppropriatePSTMessageObject(pstFile, this.descriptorIndexNode, attachmentTable,
                        localDescriptorItems);
            } catch (PSTException e) {
                e.printStackTrace();
            }
            return null;
        }
        return null;
    }

    public InputStream getFileInputStream() throws IOException, PSTException {

        PSTTableBCItem attachmentDataObject = items.get(0x3701);

        if (attachmentDataObject.isExternalValueReference) {
            PSTDescriptorItem descriptorItemNested = this.localDescriptorItems
                    .get(attachmentDataObject.entryValueReference);
            return new PSTNodeInputStream(this.pstFile, descriptorItemNested);
        } else {
            // internal value references are never encrypted
            return new PSTNodeInputStream(this.pstFile, attachmentDataObject.data, false);
        }

    }

    public int getFilesize() throws PSTException, IOException {
        PSTTableBCItem attachmentDataObject = items.get(0x3701);
        if (attachmentDataObject.isExternalValueReference) {
            PSTDescriptorItem descriptorItemNested = this.localDescriptorItems
                    .get(attachmentDataObject.entryValueReference);
            if (descriptorItemNested == null) {
                throw new PSTException(
                        "missing attachment descriptor item for: " + attachmentDataObject.entryValueReference);
            }
            return descriptorItemNested.getDataSize();
        } else {
            // raw attachment data, right there!
            return attachmentDataObject.data.length;
        }

    }

    // attachment properties

    /**
     * Attachment (short) filename ASCII or Unicode string
     */
    public String getFilename() {
        return this.getStringItem(0x3704);
    }

    public static final int ATTACHMENT_METHOD_NONE = 0;
    public static final int ATTACHMENT_METHOD_BY_VALUE = 1;
    public static final int ATTACHMENT_METHOD_BY_REFERENCE = 2;
    public static final int ATTACHMENT_METHOD_BY_REFERENCE_RESOLVE = 3;
    public static final int ATTACHMENT_METHOD_BY_REFERENCE_ONLY = 4;
    public static final int ATTACHMENT_METHOD_EMBEDDED = 5;
    public static final int ATTACHMENT_METHOD_OLE = 6;

    /**
     * Attachment method Integer 32-bit signed 0 => None (No attachment) 1 => By
     * value 2 => By reference 3 => By reference resolve 4 => By reference only 5 =>
     * Embedded message 6 => OLE
     */
    public int getAttachMethod() {
        return this.getIntItem(0x3705);
    }

    /**
     * Attachment size
     */
    public int getAttachSize() {
        return this.getIntItem(0x0e20);
    }

    /**
     * Attachment number
     */
    public int getAttachNum() {
        return this.getIntItem(0x0e21);
    }

    /**
     * Attachment long filename ASCII or Unicode string
     */
    public String getLongFilename() {
        return this.getStringItem(0x3707);
    }

    /**
     * Attachment (short) pathname ASCII or Unicode string
     */
    public String getPathname() {
        return this.getStringItem(0x3708);
    }

    /**
     * Attachment Position Integer 32-bit signed
     */
    public int getRenderingPosition() {
        return this.getIntItem(0x370b);
    }

    /**
     * Attachment long pathname ASCII or Unicode string
     */
    public String getLongPathname() {
        return this.getStringItem(0x370d);
    }

    /**
     * Attachment mime type ASCII or Unicode string
     */
    public String getMimeTag() {
        return this.getStringItem(0x370e);
    }

    /**
     * Attachment mime sequence
     */
    public int getMimeSequence() {
        return this.getIntItem(0x3710);
    }

    /**
     * Attachment Content ID
     */
    public String getContentId() {
        return this.getStringItem(0x3712);
    }

    /**
     * Attachment not available in HTML
     */
    public boolean isAttachmentInvisibleInHtml() {
        int actionFlag = this.getIntItem(0x3714);
        return ((actionFlag & 0x1) > 0);
    }

    /**
     * Attachment not available in RTF
     */
    public boolean isAttachmentInvisibleInRTF() {
        int actionFlag = this.getIntItem(0x3714);
        return ((actionFlag & 0x2) > 0);
    }

    /**
     * Attachment is MHTML REF
     */
    public boolean isAttachmentMhtmlRef() {
        int actionFlag = this.getIntItem(0x3714);
        return ((actionFlag & 0x4) > 0);
    }

    /**
     * Attachment content disposition
     */
    public String getAttachmentContentDisposition() {
        return this.getStringItem(0x3716);
    }

}
