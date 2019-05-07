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
import java.util.Date;
import java.util.HashMap;

/**
 * PSTActivity represents Journal entries
 * 
 * @author Richard Johnson
 */
public class PSTActivity extends PSTMessage {

    /**
     * @param theFile
     * @param descriptorIndexNode
     * @throws PSTException
     * @throws IOException
     */
    public PSTActivity(PSTFile theFile, DescriptorIndexNode descriptorIndexNode) throws PSTException, IOException {
        super(theFile, descriptorIndexNode);
    }

    /**
     * @param theFile
     * @param folderIndexNode
     * @param table
     * @param localDescriptorItems
     */
    public PSTActivity(PSTFile theFile, DescriptorIndexNode folderIndexNode, PSTTableBC table,
            HashMap<Integer, PSTDescriptorItem> localDescriptorItems) {
        super(theFile, folderIndexNode, table, localDescriptorItems);
    }

    /**
     * Type
     */
    public String getLogType() {
        return getStringItem(pstFile.getNameToIdMapItem(0x00008700, PSTFile.PSETID_Log));
    }

    /**
     * Start
     */
    public Date getLogStart() {
        return getDateItem(pstFile.getNameToIdMapItem(0x00008706, PSTFile.PSETID_Log));
    }

    /**
     * Duration
     */
    public int getLogDuration() {
        return getIntItem(pstFile.getNameToIdMapItem(0x00008707, PSTFile.PSETID_Log));
    }

    /**
     * End
     */
    public Date getLogEnd() {
        return getDateItem(pstFile.getNameToIdMapItem(0x00008708, PSTFile.PSETID_Log));
    }

    /**
     * LogFlags
     */
    public int getLogFlags() {
        return getIntItem(pstFile.getNameToIdMapItem(0x0000870c, PSTFile.PSETID_Log));
    }

    /**
     * DocPrinted
     */
    public boolean isDocumentPrinted() {
        return (getBooleanItem(pstFile.getNameToIdMapItem(0x0000870e, PSTFile.PSETID_Log)));
    }

    /**
     * DocSaved
     */
    public boolean isDocumentSaved() {
        return (getBooleanItem(pstFile.getNameToIdMapItem(0x0000870f, PSTFile.PSETID_Log)));
    }

    /**
     * DocRouted
     */
    public boolean isDocumentRouted() {
        return (getBooleanItem(pstFile.getNameToIdMapItem(0x00008710, PSTFile.PSETID_Log)));
    }

    /**
     * DocPosted
     */
    public boolean isDocumentPosted() {
        return (getBooleanItem(pstFile.getNameToIdMapItem(0x00008711, PSTFile.PSETID_Log)));
    }

    /**
     * Type Description
     */
    public String getLogTypeDesc() {
        return getStringItem(pstFile.getNameToIdMapItem(0x00008712, PSTFile.PSETID_Log));
    }

    @Override
    public String toString() {
        return "Type ASCII or Unicode string: " + getLogType() + "\n" + "Start Filetime: " + getLogStart() + "\n"
                + "Duration Integer 32-bit signed: " + getLogDuration() + "\n" + "End Filetime: " + getLogEnd() + "\n"
                + "LogFlags Integer 32-bit signed: " + getLogFlags() + "\n" + "DocPrinted Boolean: "
                + isDocumentPrinted() + "\n" + "DocSaved Boolean: " + isDocumentSaved() + "\n" + "DocRouted Boolean: "
                + isDocumentRouted() + "\n" + "DocPosted Boolean: " + isDocumentPosted() + "\n"
                + "TypeDescription ASCII or Unicode string: " + getLogTypeDesc();

    }

}
