/*
 * Copyright 2015-2015, Gabriel Francisco
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package iped.parsers.lnk;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItem;
import iped.data.IItemReader;
import iped.parsers.util.Messages;
import iped.properties.BasicProps;

/**
 * Parser para arquivos de atalho (LNK) do Windows Referencias utilizadas sobre
 * o formato:
 * https://github.com/libyal/liblnk/blob/master/documentation/Windows%20Shortcut%20File%20%28LNK%29%20format.asciidoc#overview
 * (Windows Shortcut File LNK)
 * https://github.com/libyal/libfwsi/blob/master/documentation/Windows%20Shell%20Item%20format.asciidoc#extension_block_0xbeef0017
 * (Windows Shell Item Format)
 * 
 * @author Gabriel
 */
public class LNKShortcutParser extends AbstractParser {
    private static final long serialVersionUID = -3156133141331973368L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-lnk")); //$NON-NLS-1$
    public static final String LNK_MIME_TYPE = "application/x-lnk"; //$NON-NLS-1$

    public static final String LNK_META_PREFIX = "lnk:";

    private static final Property LNK_CREATED_META = Property.internalDate(LNK_META_PREFIX + BasicProps.CREATED);
    private static final Property LNK_MODIFIED_META = Property.internalDate(LNK_META_PREFIX + BasicProps.MODIFIED);
    private static final Property LNK_ACCESSED_META = Property.internalDate(LNK_META_PREFIX + BasicProps.ACCESSED);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        final DateFormat df = new SimpleDateFormat(Messages.getString("LNKShortcutParser.DateFormat")); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone("GMT+0")); //$NON-NLS-1$

        metadata.set(HttpHeaders.CONTENT_TYPE, LNK_MIME_TYPE);
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("head"); //$NON-NLS-1$
        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters(
                "table {border-collapse: collapse; font-size:11pt; font-family: arial, verdana, sans-serif; width:100%; word-break: break-all; word-wrap: break-word; align:center; } table.t {margin-bottom:20px;} td { padding: 2px; } th {background-color:#D7D7D7; border: 1px solid black; padding: 3px; text-align: left; font-weight: normal;} td.a {background-color:#FFFFFF; border: 1px solid black; text-align:left; width:240px; } td.b {background-color:#FFFFFF; border: 1px solid black; text-align:left;} td.s1 {font-size:10pt; background-color:#F2F2F2; width:170px; border: 1px solid black; text-align:left;} td.s2 {font-size:10pt; background-color:#F2F2F2; border: 1px solid black; text-align:left;}"); //$NON-NLS-1$
        // xhtml.characters("img.hex
        // {content:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABYAAAAVCAMAAAB1/u6nAAAAXVBMVEX+/v4zNDV+fn62trb4+Pju7u79/f3v7+/8/Pzw8PD7+/vx8fHt7e309PTz8/P5+fny8vL6+vrs7Oz39/f29vavr6+IiIjU1NTi4uKbm5uqqqqLi4ukpKSRkZGXl5f9/FtxAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3wcIDCwkVACGvgAAAJRJREFUGNNtzNsOgyAQBNBZpfeqtIgCCv//mWVqfNE9yexMSBStCu3scODmFt7gxHj4q6I+3xUe6alISB9FwjrIMDBS1cMxrFiNGMOw9rEiOnGOYXGxI+LIr2Uctzv+T0TppOsYFhe7oPTS9wxrHwXZirUMf7JtsRn5pchYHooF000xYboo6nN4n4QJjQ/fg+AbNKofIFYRhWhhphkAAAAASUVORK5CYII=');}
        // ");
        xhtml.characters("textarea {readonly: readonly; height: 60px; width: 100%; resize: none;}"); //$NON-NLS-1$
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.endElement("head"); //$NON-NLS-1$
        xhtml.newline();

        xhtml.startElement("body"); //$NON-NLS-1$

        try {
            LNKShortcut lnkObj = LNKParser.parseFromStream(stream);

            setMetadata(context, metadata, lnkObj);


            // Exibindo as informações obtidas a partir do header do lnk
            showHeader(lnkObj, df, xhtml);

            // HasLinkInfo
            if (lnkObj.hasLinkLocation())
                showLinkLocation(lnkObj, df, xhtml);

            // HasName HasRelativePath HasWorkingDir HasArguments HasIconLocation
            if (lnkObj.hasName() || lnkObj.hasRelativePath() || lnkObj.hasWorkingDir() || lnkObj.hasArguments()
                    || lnkObj.hasIconLocation())
                showDataStrings(lnkObj, df, xhtml);

            // HasTargetIDList
            if (lnkObj.hasTargetIDList())
                showTargetIDList(lnkObj, df, xhtml);

            // Extra data: distributed link tracker properties
            if (lnkObj.hasLinkTracker())
                showLinkTracker(lnkObj, df, xhtml);

        } catch (Exception e) {
            throw new TikaException("LNKShortcutParser Exception. " + e.toString(), e); //$NON-NLS-1$
        }
        xhtml.endElement("body"); //$NON-NLS-1$
        xhtml.endDocument();
    }

    private void setMetadata(ParseContext context, Metadata metadata, LNKShortcut lnkObj) {

        IItemReader lnkItemReader = context.get(IItemReader.class);
        if (lnkItemReader instanceof IItem) {
            IItem lnkItem = (IItem) lnkItemReader;
            if (lnkItem.getCreationDate() == null) {
                lnkItem.setCreationDate(lnkObj.getCreateDate());
            }
            if (lnkItem.getModDate() == null) {
                lnkItem.setModificationDate(lnkObj.getModifiedDate());
            }
            if (lnkItem.getAccessDate() == null) {
                lnkItem.setAccessDate(lnkObj.getAccessDate());
            }
        }
        metadata.set(LNK_CREATED_META, lnkObj.getCreateDate());
        metadata.set(LNK_MODIFIED_META, lnkObj.getModifiedDate());
        metadata.set(LNK_ACCESSED_META, lnkObj.getAccessDate());

        metadata.set(LNK_META_PREFIX + "targetSize", Long.toString(lnkObj.getFileSize()));
        metadata.set(LNK_META_PREFIX + "targetAttr", LNKShortcut.getFileAttributeFlagStr(lnkObj.getFileAttributeFlags()));
        for (String flagItem : LNKShortcut.getDataFlagArray(lnkObj.getDataLinkFlags())) {
            metadata.add(LNK_META_PREFIX + "linkAttr", flagItem);
        }
        metadata.set(LNK_META_PREFIX + "iconIndex", String.valueOf(lnkObj.getIconIndex()));
        metadata.set(LNK_META_PREFIX + "windowAttr", String.valueOf(lnkObj.getShowWindow()));
        metadata.set(LNK_META_PREFIX + "hotKey", String.valueOf(lnkObj.getHotKey()));

        metadata.set(LNK_META_PREFIX + "description", lnkObj.getDescription());
        metadata.set(LNK_META_PREFIX + "relativePath", lnkObj.getRelativePath());
        metadata.set(LNK_META_PREFIX + "getWorkingDir", lnkObj.getWorkingDir());
        metadata.set(LNK_META_PREFIX + "cmdLineArgs", lnkObj.getCommandLineArgs());
        metadata.set(LNK_META_PREFIX + "iconLocation", lnkObj.getIconLocation());

        LNKLinkTracker lnkTracker = lnkObj.getLinkTracker();
        if (lnkTracker != null) {
            metadata.set(LNK_META_PREFIX + "machineId", lnkTracker.getMachineId());
            metadata.set(LNK_META_PREFIX + "droidVolId", lnkTracker.getDroidVolumeId());
            metadata.set(LNK_META_PREFIX + "droidFileId", lnkTracker.getDroidFileId());
            metadata.set(LNK_META_PREFIX + "birthDroidVolId", lnkTracker.getBirthDroidVolumeId());
            metadata.set(LNK_META_PREFIX + "birthDroidFileId", lnkTracker.getBirthDroidFileId());
        }

        LNKLinkLocation objItem = lnkObj.getLinkLocation();
        if (objItem != null) {
            boolean isUnicode = LNKShortcut.DataFlags.IsUnicode.match(lnkObj.getDataLinkFlags());
            metadata.set(LNK_META_PREFIX + "driveType", objItem.getDriveTypeStr());
            metadata.set(LNK_META_PREFIX + "driveSerial", objItem.getDriveSerial());
            metadata.set(LNK_META_PREFIX + "volumeLabel", isUnicode ? objItem.getVolumeLabelUnicode() : objItem.getVolumeLabel());
            metadata.set(LNK_META_PREFIX + "localPath", isUnicode ? objItem.getLocalPathUnicode() : objItem.getLocalPath());
            metadata.set(LNK_META_PREFIX + "commonPath", isUnicode ? objItem.getCommonPathUnicode() : objItem.getCommonPath());
            metadata.set(LNK_META_PREFIX + "netDeviceName", isUnicode ? objItem.getNetDevNameUnicode() : objItem.getNetDevName());
            metadata.set(LNK_META_PREFIX + "netShare", isUnicode ? objItem.getNetShareUnicode() : objItem.getNetShare());
            metadata.set(LNK_META_PREFIX + "netProviderType", String.format("0x%08x", objItem.getNetProviderType()));
            metadata.set(LNK_META_PREFIX + "locationFlags", String.format("0x%08x", objItem.getFlagsLocation()));
        }
    }

    private void showHeader(LNKShortcut lnkObj, DateFormat df, XHTMLContentHandler xhtml) throws Exception {
        xhtml.startElement("table", "class", "t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRowHeader(xhtml, Messages.getString("LNKShortcutParser.FileHeader")); //$NON-NLS-1$
        addRow(xhtml, Messages.getString("LNKShortcutParser.Created"), lnkObj.getCreateDate(df), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.Accessed"), lnkObj.getAccessDate(df), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.Modified"), lnkObj.getModifiedDate(df), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.TargetSize"), //$NON-NLS-1$
                String.format("%,d bytes", lnkObj.getFileSize()), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        int flAtt = lnkObj.getFileAttributeFlags();
        String strFlAtt = LNKShortcut.getFileAttributeFlagStr(flAtt);
        addRow(xhtml, Messages.getString("LNKShortcutParser.TargetAttr"), strFlAtt + String.format(" (0x%08x)", flAtt), //$NON-NLS-1$ //$NON-NLS-2$
                "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$
        int dataLnkFlags = lnkObj.getDataLinkFlags();
        String strDataFlag = LNKShortcut.getDataFlagStr(dataLnkFlags);
        addRow(xhtml, Messages.getString("LNKShortcutParser.LinkAttr"), //$NON-NLS-1$
                strDataFlag + String.format(" (0x%08x)", dataLnkFlags), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.IconIndex"), String.valueOf(lnkObj.getIconIndex()), "a", //$NON-NLS-1$ //$NON-NLS-2$
                "b"); //$NON-NLS-1$
        addRow(xhtml, Messages.getString("LNKShortcutParser.WindowAttr"), String.valueOf(lnkObj.getShowWindow()), "a", //$NON-NLS-1$ //$NON-NLS-2$
                "b"); //$NON-NLS-1$
        addRow(xhtml, Messages.getString("LNKShortcutParser.HotKey"), String.valueOf(lnkObj.getHotKey()), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.endElement("table"); //$NON-NLS-1$
    }

    private void showDataStrings(LNKShortcut lnkObj, DateFormat df, XHTMLContentHandler xhtml) throws Exception {
        xhtml.startElement("table", "class", "t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRowHeader(xhtml, Messages.getString("LNKShortcutParser.DataStrings") + lnkObj.getStringDataFlags() + ")"); //$NON-NLS-1$//$NON-NLS-2$
        addRow(xhtml, Messages.getString("LNKShortcutParser.Description"), lnkObj.getDescription(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.RelativePath"), lnkObj.getRelativePath(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.WorkingDir"), lnkObj.getWorkingDir(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.CmdLineArgs"), lnkObj.getCommandLineArgs(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRow(xhtml, Messages.getString("LNKShortcutParser.IconLocation"), lnkObj.getIconLocation(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.endElement("table"); //$NON-NLS-1$
    }

    private void showLinkTracker(LNKShortcut lnkObj, DateFormat df, XHTMLContentHandler xhtml) throws Exception {
        LNKLinkTracker lnkTracker = lnkObj.getLinkTracker();
        if (lnkTracker != null) {
            xhtml.startElement("table", "class", "t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRowHeader(xhtml, Messages.getString("LNKShortcutParser.DistributedLinkTrackProps")); //$NON-NLS-1$
            addRow(xhtml, Messages.getString("LNKShortcutParser.MachineId"), lnkTracker.getMachineId(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.DroidVolId"), lnkTracker.getDroidVolumeId(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.DroidFileId"), lnkTracker.getDroidFileId(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.BirthDroidVolId"), lnkTracker.getBirthDroidVolumeId(), //$NON-NLS-1$
                    "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$
            addRow(xhtml, Messages.getString("LNKShortcutParser.BirthDroidFileId"), lnkTracker.getBirthDroidFileId(), //$NON-NLS-1$
                    "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$
            xhtml.endElement("table"); //$NON-NLS-1$
        }
    }

    private void showTargetIDList(LNKShortcut lnkObj, DateFormat df, XHTMLContentHandler xhtml) throws Exception {
        xhtml.startElement("table", "class", "t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        addRowHeader(xhtml, Messages.getString("LNKShortcutParser.LinkTargetID")); //$NON-NLS-1$
        List<LNKShellItem> lstTarget = lnkObj.getShellTargetIDList();
        for (int i = 0; i < lstTarget.size(); i++) {
            LNKShellItem lnkShell = (LNKShellItem) lstTarget.get(i);
            if (lnkShell.hasFileEntry()) {
                LNKShellItemFileEntry fEntry = lnkShell.getFileEntry();

                xhtml.startElement("tr"); //$NON-NLS-1$
                xhtml.startElement("td", "class", "a"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                xhtml.characters(lnkShell.getName());
                xhtml.endElement("td"); //$NON-NLS-1$
                xhtml.startElement("td", "class", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                xhtml.startElement("table"); //$NON-NLS-1$
                if (fEntry.isDirectory()) {
                    addRow(xhtml, Messages.getString("LNKShortcutParser.EntryType"), //$NON-NLS-1$
                            Messages.getString("LNKShortcutParser.Directory"), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } else if (fEntry.isFile()) {
                    addRow(xhtml, Messages.getString("LNKShortcutParser.EntryType"), //$NON-NLS-1$
                            Messages.getString("LNKShortcutParser.File"), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                int flAtt = fEntry.getFileAttributeFlags();
                String strFlAtt = LNKShortcut.getFileAttributeFlagStr(flAtt);
                addRow(xhtml, Messages.getString("LNKShortcutParser.FileAttr"), //$NON-NLS-1$
                        strFlAtt + String.format(" (0x%08x)", flAtt), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                addRow(xhtml, Messages.getString("LNKShortcutParser.PrimaryName"), fEntry.getPrimaryName(), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (fEntry.getFileSize() > 0) {
                    addRow(xhtml, Messages.getString("LNKShortcutParser.Size"), //$NON-NLS-1$
                            String.format("%,d bytes", fEntry.getFileSize()), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                }
                addRow(xhtml, Messages.getString("LNKShortcutParser.LastMod"), fEntry.getModifiedDate(df), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                addRow(xhtml, Messages.getString("LNKShortcutParser.Extensions"), fEntry.getExtensionsSig(), "s1", //$NON-NLS-1$ //$NON-NLS-2$
                        "s2"); //$NON-NLS-1$
                addRow(xhtml, Messages.getString("LNKShortcutParser.Created"), fEntry.getCreateDate(df), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                addRow(xhtml, Messages.getString("LNKShortcutParser.Accessed"), fEntry.getAccessDate(df), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                addRow(xhtml, Messages.getString("LNKShortcutParser.SecondaryName"), fEntry.getSecondaryName(), "s1", //$NON-NLS-1$ //$NON-NLS-2$
                        "s2"); //$NON-NLS-1$
                addRow(xhtml, Messages.getString("LNKShortcutParser.LocalizedNames"), fEntry.getLocalizedNames(), "s1", //$NON-NLS-1$ //$NON-NLS-2$
                        "s2"); //$NON-NLS-1$
                addRow(xhtml, Messages.getString("LNKShortcutParser.NtfsRef"), fEntry.getNtfsRef(), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                addRow(xhtml, Messages.getString("LNKShortcutParser.UnknownData"), fEntry.getUnknown(), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                xhtml.endElement("table"); //$NON-NLS-1$
                xhtml.endElement("td"); //$NON-NLS-1$
                xhtml.endElement("tr"); //$NON-NLS-1$
                xhtml.newline();

            } else if (lnkShell.hasNetworkLocation()) {
                LNKShellItemNetwork netLoc = lnkShell.getNetworkLocation();
                xhtml.startElement("tr"); //$NON-NLS-1$
                xhtml.startElement("td", "class", "a"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                xhtml.characters(lnkShell.getName());
                xhtml.endElement("td"); //$NON-NLS-1$
                xhtml.startElement("td", "class", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                xhtml.startElement("table"); //$NON-NLS-1$

                addRow(xhtml, Messages.getString("LNKShortcutParser.NetItemType"), netLoc.getTypeStr(), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                addRow(xhtml, Messages.getString("LNKShortcutParser.NetItemName"), netLoc.getLocation(), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                addRow(xhtml, Messages.getString("LNKShortcutParser.NetDescription"), netLoc.getDescription(), "s1", //$NON-NLS-1$ //$NON-NLS-2$
                        "s2"); //$NON-NLS-1$
                addRow(xhtml, Messages.getString("LNKShortcutParser.NetComments"), netLoc.getComments(), "s1", "s2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                xhtml.endElement("table"); //$NON-NLS-1$
                xhtml.endElement("td"); //$NON-NLS-1$
                xhtml.endElement("tr"); //$NON-NLS-1$
                xhtml.newline();
            } else {
                xhtml.startElement("tr"); //$NON-NLS-1$
                xhtml.startElement("td", "class", "a"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                xhtml.characters(lnkShell.getName());
                if (lnkShell.isUnparsed()) {
                    xhtml.startElement("p", "align", "center"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    xhtml.characters(Messages.getString("LNKShortcutParser.DataNotDecoded")); //$NON-NLS-1$
                    xhtml.endElement("p"); //$NON-NLS-1$
                }
                xhtml.endElement("td"); //$NON-NLS-1$
                xhtml.startElement("td", "class", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                List<String> lstTmp = lnkShell.getListValues();
                for (int j = 0; j < lstTmp.size(); j++) {
                    if (lnkShell.isUnparsed()) {
                        xhtml.startElement("textarea", "readonly", "readonly"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        xhtml.characters(lstTmp.get(j));
                        xhtml.endElement("textarea"); //$NON-NLS-1$
                    } else {
                        xhtml.characters(lstTmp.get(j));
                    }
                    xhtml.startElement("br"); //$NON-NLS-1$
                }
                xhtml.endElement("td"); //$NON-NLS-1$
                xhtml.endElement("tr"); //$NON-NLS-1$
                xhtml.newline();
            }
        }
        xhtml.endElement("table"); //$NON-NLS-1$
    }

    private void showLinkLocation(LNKShortcut lnkObj, DateFormat df, XHTMLContentHandler xhtml) throws Exception {
        LNKLinkLocation objItem = lnkObj.getLinkLocation();
        if (objItem != null) {
            xhtml.startElement("table", "class", "t"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRowHeader(xhtml, Messages.getString("LNKShortcutParser.LinkLocationInfo")); //$NON-NLS-1$
            addRow(xhtml, Messages.getString("LNKShortcutParser.DriveType"), objItem.getDriveTypeStr(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.DriveSerial"), objItem.getDriveSerial(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.VolumeLabel"), objItem.getVolumeLabel(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.VolumeLabelUnicode"), objItem.getVolumeLabelUnicode(), //$NON-NLS-1$
                    "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$
            addRow(xhtml, Messages.getString("LNKShortcutParser.LocalPath"), objItem.getLocalPath(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.CommonPath"), objItem.getCommonPath(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.LocalPathUnicode"), objItem.getLocalPathUnicode(), "a", //$NON-NLS-1$ //$NON-NLS-2$
                    "b"); //$NON-NLS-1$
            addRow(xhtml, Messages.getString("LNKShortcutParser.CommonPathUnicode"), objItem.getCommonPathUnicode(), //$NON-NLS-1$
                    "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$
            addRow(xhtml, Messages.getString("LNKShortcutParser.NetDeviceName"), objItem.getNetDevName(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.NetDevNameUnicode"), objItem.getNetDevNameUnicode(), //$NON-NLS-1$
                    "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$
            addRow(xhtml, Messages.getString("LNKShortcutParser.NetShare"), objItem.getNetShare(), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.NetShareUnicode"), objItem.getNetShareUnicode(), "a", //$NON-NLS-1$ //$NON-NLS-2$
                    "b"); //$NON-NLS-1$
            addRow(xhtml, Messages.getString("LNKShortcutParser.NetProviderType"), //$NON-NLS-1$
                    String.format(" (0x%08x)", objItem.getNetProviderType()), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            addRow(xhtml, Messages.getString("LNKShortcutParser.LocationFlags"), //$NON-NLS-1$
                    String.format(" (0x%08x)", objItem.getFlagsLocation()), "a", "b"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            xhtml.endElement("table"); //$NON-NLS-1$
        }
    }

    private static final void addRow(XHTMLContentHandler xhtml, String nmStr, String vlStr, String classNm,
            String classVl) throws Exception {
        if (nmStr == null || vlStr == null || classNm == null || classVl == null)
            return;
        xhtml.startElement("tr"); //$NON-NLS-1$
        xhtml.startElement("td", "class", classNm); //$NON-NLS-1$ //$NON-NLS-2$
        xhtml.characters(nmStr);
        xhtml.endElement("td"); //$NON-NLS-1$
        xhtml.startElement("td", "class", classVl); //$NON-NLS-1$ //$NON-NLS-2$
        xhtml.characters(vlStr);
        xhtml.endElement("td"); //$NON-NLS-1$
        xhtml.endElement("tr"); //$NON-NLS-1$
        xhtml.newline();
    }

    private static final void addRowHeader(XHTMLContentHandler xhtml, String nmStr) throws Exception {
        xhtml.startElement("tr"); //$NON-NLS-1$
        xhtml.startElement("th", "colspan", "2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.characters(nmStr);
        xhtml.endElement("th"); //$NON-NLS-1$
        xhtml.endElement("tr"); //$NON-NLS-1$
        xhtml.newline();
    }
}