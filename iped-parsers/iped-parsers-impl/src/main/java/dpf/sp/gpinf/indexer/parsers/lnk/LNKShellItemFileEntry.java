package dpf.sp.gpinf.indexer.parsers.lnk;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class LNKShellItemFileEntry {
    private long fileSize, modifiedDate, createDate, accessDate;
    private int fileAttributeFlags, tipoShell;
    private String primaryName, secondaryName, guidShellFolder, ntfsRef, unknown;
    private StringBuffer extensionSigs, localizedNames;

    public int getTipoShell() {
        return tipoShell;
    }

    public void setTipoShell(int tipoShell) {
        this.tipoShell = tipoShell;
    }

    public String getExtensionsSig() {
        if (extensionSigs == null)
            return null;
        return extensionSigs.toString();
    }

    public void addExtensionSig(String extensionSig) {
        if (extensionSigs == null)
            extensionSigs = new StringBuffer(extensionSig);
        else {
            extensionSigs.append(", "); //$NON-NLS-1$
            extensionSigs.append(extensionSig);
        }
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getFileAttributeFlags() {
        return fileAttributeFlags;
    }

    public void setFileAttributeFlags(int fileAttributeFlags) {
        this.fileAttributeFlags = fileAttributeFlags;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public void setPrimaryName(String primaryName) {
        this.primaryName = primaryName;
    }

    public String getSecondaryName() {
        return secondaryName;
    }

    public void setSecondaryName(String secondaryName) {
        this.secondaryName = secondaryName;
    }

    public String getGuidShellFolder() {
        return guidShellFolder;
    }

    public void setGuidShellFolder(String guidShellFolder) {
        this.guidShellFolder = guidShellFolder;
    }

    public boolean isDirectory() {
        return ((tipoShell & 0X01) != 0);
    }

    public boolean isFile() {
        return ((tipoShell & 0X02) != 0);
    }

    public boolean hasUnicode() {
        return ((tipoShell & 0X04) != 0);
    }

    public boolean hasClassID() {
        return ((tipoShell & 0X80) != 0);
    }

    public String getUnknown() {
        return unknown;
    }

    public void setUnknown(String unknown) {
        this.unknown = unknown;
    }

    public String getCreateDate(DateFormat df) {
        return toDateStrFromFAT(df, createDate);
    }

    public void setCreateDate(long createDate) {
        this.createDate = createDate;
    }

    public String getAccessDate(DateFormat df) {
        return toDateStrFromFAT(df, accessDate);
    }

    public void setAccessDate(long accessDate) {
        this.accessDate = accessDate;
    }

    public String getModifiedDate(DateFormat df) {
        return toDateStrFromFAT(df, modifiedDate);
    }

    public void setModifiedDate(long modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getLocalizedNames() {
        if (localizedNames == null)
            return null;
        return localizedNames.toString();
    }

    public void addLocalizedName(String localizedName) {
        if (localizedNames == null)
            localizedNames = new StringBuffer(localizedName);
        else {
            localizedNames.append(", "); //$NON-NLS-1$
            localizedNames.append(localizedName);
        }
    }

    public String getNtfsRef() {
        return ntfsRef;
    }

    public void setNtfsRef(String ntfsRef) {
        this.ntfsRef = ntfsRef;
    }

    public static String toDateStrFromFAT(DateFormat df, long ft) {
        /*
         * The FAT date and time is a 32-bit value containing two 16-bit values: * The
         * date (lower 16-bit). * bits 0 - 4: day of month, where 1 represents the first
         * day * bits 5 - 8: month of year, where 1 represent January * bits 9 - 15:
         * year since 1980 * The time of day (upper 16-bit). * bits 0 - 4: seconds (in 2
         * second intervals) * bits 5 - 10: minutes * bits 11 - 15: hours 7137 7d28
         */
        if (ft == 0)
            return LNKShortcut.DATA_NAO_SETADA;
        int day = (int) (ft & 0x1f);
        int month = ((int) ((ft >> 5) & 0x0f)) - 1;
        int year = ((int) (ft >> 9) & 0x7f) + 1980;

        if (day < 0 || day > 31 || month < 0 || month > 11)
            return LNKShortcut.DATA_NAO_SETADA;

        ft = ft >> 16;
        int seconds = (int) (ft & 0x1f) * 2;
        int minutes = (int) ((ft >> 5) & 0x3f);
        int hours = ((int) (ft >> 11) & 0x1f);

        if (hours > 23 || minutes > 59 || seconds > 59)
            return LNKShortcut.DATA_NAO_SETADA;

        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT+0")); //$NON-NLS-1$
        calendar.set(year, month, day, hours, minutes, seconds);

        return df.format(calendar.getTime());
    }
}
