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
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.util.Messages;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;

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

    private static Logger logger = LoggerFactory.getLogger(LNKShortcutParser.class);

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("x-lnk")); //$NON-NLS-1$
    public static final String LNK_MIME_TYPE = "application/x-lnk"; //$NON-NLS-1$

    public static final String LNK_METADATA_PREFIX = "lnk:";
    public static final Property LNK_METADATA_CREATED = Property.internalDate(LNK_METADATA_PREFIX + BasicProps.CREATED);
    public static final Property LNK_METADATA_MODIFIED = Property.internalDate(LNK_METADATA_PREFIX + BasicProps.MODIFIED);
    public static final Property LNK_METADATA_ACCESSED = Property.internalDate(LNK_METADATA_PREFIX + BasicProps.ACCESSED);
    public static final String LNK_METADATA_TARGET_REFERENCED = LNK_METADATA_PREFIX + "targetReferenced";
    public static final String LNK_METADATA_TARGET_METADATA_DIFFERENT = LNK_METADATA_PREFIX + "targetDifferentMetadata";
    public static final String LNK_METADATA_TARGET_REF_STRATEGY = LNK_METADATA_PREFIX + "targetReferenceStrategy";

    public static final String LNK_TARGET_REF_STRATEGY_MFT = "MFT";
    public static final String LNK_TARGET_REF_STRATEGY_RELATIVEPATH = "RelativePath";
    public static final String LNK_TARGET_REF_STRATEGY_FULLLOCALPATH = "FullLocalPath";

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

            setMetadata(metadata, context, lnkObj);

            // Exibindo as informações obtidas a partir do header do lnk
            showHeader(lnkObj, df, xhtml);

            // HasLinkInfo
            if (lnkObj.hasLinkLocation()) {
                showLinkLocation(lnkObj, df, xhtml);
            }

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

    private void setMetadata(Metadata metadata, ParseContext context, LNKShortcut lnkObj) {
        metadata.set(LNK_METADATA_CREATED, lnkObj.getCreateDate());
        metadata.set(LNK_METADATA_MODIFIED, lnkObj.getModifiedDate());
        metadata.set(LNK_METADATA_ACCESSED, lnkObj.getAccessDate());

        metadata.set(LNK_METADATA_PREFIX + "description", lnkObj.getDescription());
        metadata.set(LNK_METADATA_PREFIX + "workingDir", lnkObj.getWorkingDir());
        metadata.set(LNK_METADATA_PREFIX + "cmdLineArgs", lnkObj.getCommandLineArgs());
        metadata.add(LNK_METADATA_PREFIX + "targetSize", Long.toString(lnkObj.getFileSize()));
        for (String flagStr : LNKShortcut.getFileAttributeFlagArray(lnkObj.getFileAttributeFlags())) {
            metadata.add(LNK_METADATA_PREFIX + "targetAttributes", flagStr);
        }
        for (String flagStr : LNKShortcut.getDataFlagArray(lnkObj.getDataLinkFlags())) {
            metadata.add(LNK_METADATA_PREFIX + "linkAttributes", flagStr);
        }

        if (lnkObj.hasLinkLocation()) {

            LNKLinkLocation lnkLoc = lnkObj.getLinkLocation();

            metadata.add(LNK_METADATA_PREFIX + "localPath", getUnicodeValue(lnkObj, lnkLoc.getLocalPathUnicode(), lnkLoc.getLocalPath()));
            metadata.add(LNK_METADATA_PREFIX + "commonPath", getUnicodeValue(lnkObj, lnkLoc.getCommonPathUnicode(), lnkLoc.getCommonPath()));
            metadata.add(LNK_METADATA_PREFIX + "volumeLabel", getUnicodeValue(lnkObj, lnkLoc.getVolumeLabelUnicode(), lnkLoc.getVolumeLabel()));
            metadata.add(LNK_METADATA_PREFIX + "networkShare", getUnicodeValue(lnkObj, lnkLoc.getNetShareUnicode(), lnkLoc.getNetShare()));
            metadata.set(LNK_METADATA_PREFIX + "networkDeviceName", getUnicodeValue(lnkObj, lnkLoc.getNetDevNameUnicode(), lnkLoc.getNetDevName()));
            metadata.set(LNK_METADATA_PREFIX + "driveType", lnkLoc.getDriveTypeStr());
            metadata.set(LNK_METADATA_PREFIX + "driveSerial", lnkLoc.getDriveSerial());

            String fullLocalPath = buildFullLocalPath(lnkObj);
            metadata.add(LNK_METADATA_PREFIX + "fullLocalPath", fullLocalPath);

            try {
                makeReference(metadata, context, lnkObj, fullLocalPath);
            } catch (Exception e) {
                logger.warn("Error making reference from LNK to file", e);
            }
        }
    }

    private static String getUnicodeValue(LNKShortcut lnkObj, String unicodeValue, String value) {
        return lnkObj.isUnicode() ? StringUtils.firstNonBlank(unicodeValue, value) : value;
    }

    // According to
    // https://github.com/libyal/liblnk/blob/main/documentation/Windows%20Shortcut%20File%20(LNK)%20format.asciidoc#4-location-information
    // the real local path is the concatenation of netShare, commonPath and localPath
    private String buildFullLocalPath(LNKShortcut lnkObj) {

        String fullLocalPath = "";

        LNKLinkLocation lnkLoc = lnkObj.getLinkLocation();
        if (lnkLoc == null) {
            return fullLocalPath;
        }

        String netShare = getUnicodeValue(lnkObj, lnkLoc.getNetShareUnicode(), lnkLoc.getNetShare());
        String commonPath = getUnicodeValue(lnkObj, lnkLoc.getCommonPathUnicode(), lnkLoc.getCommonPath());
        if (StringUtils.isNotEmpty(netShare)) {
            fullLocalPath = StringUtils.appendIfMissing(netShare, "\\");
        } else if (StringUtils.isNotEmpty(commonPath)) {
            fullLocalPath = StringUtils.appendIfMissing(commonPath, "\\");
        }
        fullLocalPath += getUnicodeValue(lnkObj, lnkLoc.getLocalPathUnicode(), lnkLoc.getLocalPath());

        return fullLocalPath;
    }

    private IItemReader makeReference(Metadata metadata, ParseContext context, LNKShortcut lnkObj, String fullLocalPath) {

        // ignores net share
        if (lnkObj.getLinkLocation().getNetShare() != null) {
            return null;
        }

        IItemSearcher searcher = context.get(IItemSearcher.class);
        if (searcher == null) {
            return null;
        }

        IItemReader lnkItem = context.get(IItemReader.class);
        String localPathName = StringUtils.substringAfterLast(fullLocalPath, '\\');

        // Strategy 1: MFT-based lookup
        {
            List<IItemReader> items = lookupUsingMFT(searcher, lnkObj, lnkItem);
            IItemReader result = setReferenceMetadata(metadata, context, lnkObj, localPathName, items);
            if (result != null) {
                metadata.set(LNK_METADATA_TARGET_REF_STRATEGY, LNK_TARGET_REF_STRATEGY_MFT);
                return result;
            }
        }

        // Strategy 2: Relative path lookup
        {
            List<IItemReader> items = lookupUsingRelativePath(searcher, lnkObj, lnkItem);
            IItemReader result = setReferenceMetadata(metadata, context, lnkObj, localPathName, items);
            if (result != null) {
                metadata.set(LNK_METADATA_TARGET_REF_STRATEGY, LNK_TARGET_REF_STRATEGY_RELATIVEPATH);
                return result;
            }
        }

        // Strategy 3: Full local path lookup
        {
            List<IItemReader> items = lookupUsingFullLocalPath(searcher, lnkObj, lnkItem, fullLocalPath);
            IItemReader result = setReferenceMetadata(metadata, context, lnkObj, localPathName, items);
            if (result != null) {
                metadata.set(LNK_METADATA_TARGET_REF_STRATEGY, LNK_TARGET_REF_STRATEGY_FULLLOCALPATH);
                return result;
            }
        }

        return null;
    }

    /**
     * Looks up items using a relative path specified in a LNK shortcut object.
     * 
     * <br/>
     * It constructs an absolute target path by normalizing the the parent directory
     * of {@code lnkItem}'s path combined with the LNK relative path.
     * 
     * <br/>
     * In the end it is checked if results path exactly matches with the constructed
     * absolute path.
     *
     * @param lnkObj  The LNK object parsed from lnk file.
     * @param lnkItem The LNK item to be added to the case.
     * @return The corresponding target files, or {@code null} if preconditions are
     *         not met.
     */
    private List<IItemReader> lookupUsingRelativePath(IItemSearcher searcher, LNKShortcut lnkObj, IItemReader lnkItem) {

        if (!lnkObj.hasRelativePath() || lnkItem == null || lnkObj.getRelativePath() == null) {
            return null;
        }

        String lnkRelativePath = lnkObj.getRelativePath().replace('\\', '/');
        String fullPathInCase = Paths.get(lnkItem.getPath(), "..", lnkRelativePath).normalize().toString();
        List<IItemReader> items = searcher
                .search(BasicProps.PATH + ":\"" + searcher.escapeQuery(fullPathInCase) + "\"");
        items.removeIf(item -> !item.getPath().equals(fullPathInCase)); // must match exactly the path
        return items;
    }

    /**
     * Attempts to find the file targeted by the LNK object ({@code lnkObj}) within
     * the case. Identification primarily relies on the NTFS file reference (MFT
     * entry index and sequence number) obtained from the {@code lnkObj}.
     * 
     * <br/>
     * A file is considered the target if its NTFS file reference matches that from
     * the {@code lnkObj}, and, additionally, the creation timestamp of the target
     * file, as recorded in the {@code lnkObj}, matches the creation timestamp of
     * the candidate target file (useful to find files that might have originated
     * from different volumes).
     *
     * @param lnkObj  The LNK object parsed from lnk file.
     * @param lnkItem The LNK item to be added to the case.
     * @return The corresponding target files, or {@code null} if preconditions are
     *         not met.
     */
    private List<IItemReader> lookupUsingMFT(IItemSearcher searcher, LNKShortcut lnkObj, IItemReader lnkItem) {

        if (lnkItem == null || !lnkObj.hasTargetIDList() || lnkObj.getShellTargetIDList().isEmpty()) {
            return null;
        }

        if (lnkObj.getCreateDate() == null) {
            return null;
        }

        LNKShellItem lastTarget = lnkObj.getShellTargetIDList().get(lnkObj.getShellTargetIDList().size() - 1);
        if (!lastTarget.hasFileEntry()) {
            return null;
        }

        LNKShellItemFileEntry fEntry = lastTarget.getFileEntry();
        if (fEntry.getIndMft() < 0 || fEntry.getSeqMft() < 0) {
            return null;
        }

        return searcher.search(BasicProps.META_ADDRESS + ":" + fEntry.getIndMft() //
                + " && " + BasicProps.MFT_SEQUENCE + ":" + fEntry.getSeqMft() //
                + " && " + BasicProps.CREATED + ":\"" + DateUtil.dateToString(lnkObj.getCreateDate()) + "\"");
    }

    private List<IItemReader> lookupUsingFullLocalPath(IItemSearcher searcher, LNKShortcut lnkObj, IItemReader lnkItem,
            String fullLocalPath) {

        fullLocalPath = StringUtils.removeStart(fullLocalPath, "file://");
        fullLocalPath = fullLocalPath.replace('\\', '/');
        fullLocalPath = "/" + StringUtils.substringAfter(fullLocalPath, ":/");
        final String fullLocalPathFinal = fullLocalPath;
        List<IItemReader> items = searcher.search(BasicProps.PATH + ":\"" + searcher.escapeQuery(fullLocalPath) + "\"");
        items.removeIf(item -> !item.getPath().endsWith(fullLocalPathFinal)); // path must ends with fullLocalPathFinal

        // Disconsider if fullPath is not relative to a root item (named as "/").
        // This avoid a fullLocalPath = "/x/y/a.png" to wrongly reference a file like "/image.E01/vol_2/Images/foo/x/y/a.png"
        int fullLocalPathParts = StringUtils.countMatches(fullLocalPathFinal, '/');
        for (Iterator<IItemReader> iter = items.iterator(); iter.hasNext();) {
            IItemReader item = iter.next();

            // walk until find the relative root
            IItemReader currentItem = item;
            for (int i = 0; i < fullLocalPathParts; i++) {
                List<IItemReader> parents = searcher.search(BasicProps.ID + ":" + currentItem.getParentId());
                if (parents.isEmpty()) {
                    currentItem = null;
                    break;
                }
                currentItem = parents.get(0);
            }

            // if relative root is not "/", then the item is not related to the link
            if (currentItem == null || !(currentItem.getName().equals("/") || currentItem.isRoot())) {
                iter.remove();
            }
        }
        return items;
    }

    private IItemReader setReferenceMetadata(Metadata metadata, ParseContext context, LNKShortcut lnkObj,
            String localPathName, List<IItemReader> items) {

        if (items == null || items.isEmpty()) {
            return null;
        }

        IItemReader item;
        if (items.size() > 1) {
            item = selectBestItem(lnkObj, items);
            logger.warn("More than one file referenced to the link. Using only the best one. {}",
                    items.stream().map(IItemReader::getPath).toString());
        } else {
            item = items.get(0);
        }

        metadata.set(LNK_METADATA_TARGET_REFERENCED, Boolean.toString(true));
        metadata.set(ExtraProperties.LINKED_ITEMS, BasicProps.ID + ":" + item.getId());

        Date created = item.getCreationDate();
        if (created != null && lnkObj.getCreateDate() != null
                && !DateUtils.truncatedEquals(created, lnkObj.getCreateDate(), Calendar.SECOND)) {
            metadata.add(LNK_METADATA_TARGET_METADATA_DIFFERENT, BasicProps.CREATED);
        }

        Date modifiedDate = item.getModDate();
        if (modifiedDate != null && lnkObj.getModifiedDate() != null
                && !DateUtils.truncatedEquals(modifiedDate, lnkObj.getModifiedDate(), Calendar.SECOND)) {
            metadata.add(LNK_METADATA_TARGET_METADATA_DIFFERENT, BasicProps.MODIFIED);
        }

        if (item.getLength() != lnkObj.getFileSize()) {
            metadata.add(LNK_METADATA_TARGET_METADATA_DIFFERENT, BasicProps.LENGTH);
        }

        if (!item.getName().equals(localPathName)) {
            metadata.add(LNK_METADATA_TARGET_METADATA_DIFFERENT, BasicProps.NAME);
        }
        return item;
    }

    private IItemReader selectBestItem(LNKShortcut lnkObj, List<IItemReader> items) {

        Collections.sort(items, (o1, o2) -> {
            return getItemScore(lnkObj, o2) - getItemScore(lnkObj, o1);
        });

        return items.get(0);
    }

    private int getItemScore(LNKShortcut lnkObj, IItemReader item) {
        boolean sameSize = item.getLength() != null ? item.getLength() == lnkObj.getFileSize() : false;
        if (item.getCreationDate() != null && lnkObj.getCreateDate() != null) {
            boolean sameCreated = DateUtils.truncatedEquals(item.getCreationDate(), lnkObj.getCreateDate(), Calendar.SECOND);
            if (!item.isDeleted() && sameCreated) {
                return sameSize ? 20 : 19;
            } else if (sameCreated) {
                return sameSize ? 18 : 17;
            } else if (!item.isDeleted()) {
                return sameSize ? 16 : 15;
            } else {
                return sameSize ? 14 : 13;
            }
        } else {
            if (!item.isDeleted()) {
                return sameSize ? 10 : 9;
            } else {
                return sameSize ? 8 : 7;
            }
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