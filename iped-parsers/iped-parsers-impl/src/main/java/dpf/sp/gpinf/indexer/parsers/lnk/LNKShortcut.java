package dpf.sp.gpinf.indexer.parsers.lnk;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

public class LNKShortcut {
	 
    private int dataLnkFlags = 0;
    
    private List<LNKShellItem> lstShellTargetID;
    
    private long createDate, accessDate, modifiedDate, fileSize ;
    
	private int iconIndex, showWindow, hotKey, fileAttributeFlags, dataLinkFlags, headerSize;

    protected static final String DATA_NAO_SETADA = "--/--/---- --:--:--"; //$NON-NLS-1$

    private LNKLinkLocation linkLocation = null;
    
    private String description, relativePath, workingDir, commandLineArgs, iconLocation;
    
    private LNKLinkTracker linkTracker = null;
    
	public LNKLinkTracker getLinkTracker() {
		return linkTracker;
	}

	public void setLinkTracker(LNKLinkTracker linkTracker) {
		this.linkTracker = linkTracker;
	}

	public LNKLinkLocation getLinkLocation() {
		return linkLocation;
	}

	public void setLinkLocation(LNKLinkLocation linkLocation) {
		this.linkLocation = linkLocation;
	}

	public LNKShortcut() {
		lstShellTargetID = new ArrayList<LNKShellItem>();
	}
	
    public int getHeaderSize() {
		return headerSize;
	}

	public void setHeaderSize(int headerSize) {
		this.headerSize = headerSize;
	}

	public int getDataLnkFlags() {
		return dataLnkFlags;
	}

	public void setDataLnkFlags(int dataLnkFlags) {
		this.dataLnkFlags = dataLnkFlags;
	}

	public String getCreateDate(DateFormat df) {
		return toDateStr(df, createDate);
	}

	public void setCreateDate(long createDate) {
		this.createDate = createDate;
	}

	public String getAccessDate(DateFormat df) {
		return toDateStr(df, accessDate);
	}

	public void setAccessDate(long accessDate) {
		this.accessDate = accessDate;
	}

	public String getModifiedDate(DateFormat df) {
		return toDateStr(df, modifiedDate);
	}

	public void setModifiedDate(long modifiedDate) {
		this.modifiedDate = modifiedDate;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public int getIconIndex() {
		return iconIndex;
	}

	public void setIconIndex(int iconIndex) {
		this.iconIndex = iconIndex;
	}

	public int getShowWindow() {
		return showWindow;
	}

	public void setShowWindow(int showWindow) {
		this.showWindow = showWindow;
	}

	public int getHotKey() {
		return hotKey;
	}

	public void setHotKey(int hotKey) {
		this.hotKey = hotKey;
	}

	public int getFileAttributeFlags() {
		return fileAttributeFlags;
	}

	public void setFileAttributeFlags(int fileAttributeFlags) {
		this.fileAttributeFlags = fileAttributeFlags;
	}

	public int getDataLinkFlags() {
		return dataLinkFlags;
	}

	public void setDataLinkFlags(int dataLinkFlags) {
		this.dataLinkFlags = dataLinkFlags;
	}

	public void addShellTargetID(LNKShellItem e) {
		lstShellTargetID.add(e);
	}

	public List<LNKShellItem> getShellTargetIDList() {
		return lstShellTargetID;
	}
	
	public boolean hasTargetIDList() {
		return ((DataFlags.HasTargetIDList.getFlag() & dataLinkFlags) > 0);
	}

	public boolean hasLinkLocation() {
		return ((DataFlags.HasLinkInfo.getFlag() & dataLinkFlags) > 0);
	}	

	public boolean hasName() {
		return ((DataFlags.HasName.getFlag() & dataLinkFlags) > 0);
	}	
	
	public boolean hasRelativePath() {
		return ((DataFlags.HasRelativePath.getFlag() & dataLinkFlags) > 0);
	}	

	public boolean hasWorkingDir() {
		return ((DataFlags.HasWorkingDir.getFlag() & dataLinkFlags) > 0);
	}	

	public boolean hasArguments() {
		return ((DataFlags.HasArguments.getFlag() & dataLinkFlags) > 0);
	}	
	
	public boolean hasIconLocation() {
		return ((DataFlags.HasIconLocation.getFlag() & dataLinkFlags) > 0);
	}	

	public boolean hasLinkTracker() {
		return (linkTracker != null);
	}
	
	public boolean isUnicode() {
		return ((DataFlags.IsUnicode.getFlag() & dataLinkFlags) > 0);
	}	
	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getRelativePath() {
		return relativePath;
	}

	public void setRelativePath(String relativePath) {
		this.relativePath = relativePath;
	}

	public String getWorkingDir() {
		return workingDir;
	}

	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}

	public String getCommandLineArgs() {
		return commandLineArgs;
	}

	public void setCommandLineArgs(String commandLineArgs) {
		this.commandLineArgs = commandLineArgs;
	}

	public String getIconLocation() {
		return iconLocation;
	}

	public void setIconLocation(String iconLocation) {
		this.iconLocation = iconLocation;
	}
	
	public static enum FileAttributeFlags {
		READONLY(0x00000001), 
		HIDDEN(0x00000002),
		SYSTEM(0x00000004),
		RESERVED_1(0x00000008), // Reserved, not used by the LNK format	Is a volume label
		DIRECTORY(0x00000010),
		ARCHIVE(0x00000020),
		DEVICE(0x00000040),
		NORMAL(0x00000080),
		TEMPORARY(0x00000100),
		SPARSE_FILE(0x00000200),
		REPARSE_POINT(0x00000400),
		COMPRESSED(0x00000800),
		OFFLINE(0x00001000),		
		NOT_CONTENT_INDEXED(0x00002000),
		ENCRYPTED(0x00004000),
		RESERVED_2(0x00000008), // Unknown (seen on Windows 95 FAT)
		VIRTUAL(0x00010000);
		
		private int flag;
        
        private FileAttributeFlags(int flag) {
            this.flag = flag;
        }
        
        public int getFlag() {
            return flag;
        }
	}
	
	public static String getFileAttributeFlagStr(int flAtt) {
		StringBuilder sb = new StringBuilder(""); //$NON-NLS-1$
        for(FileAttributeFlags enumItem : FileAttributeFlags.values()) {
            if((flAtt & enumItem.getFlag()) == enumItem.getFlag()) {
                sb.append(enumItem.name());
                sb.append(", "); //$NON-NLS-1$
            }
        }
        int i;
        if ((i = sb.length()) > 0) {
        	sb.delete(i-2, i);
        }
        return sb.toString();
	}

	public static enum DataFlags {
		HasTargetIDList(0x00000001), //The LNK file contains a link target identifier
		HasLinkInfo(0x00000002), //The LNK file contains location information
		HasName (0x00000004), //The LNK file contains a description data string
		HasRelativePath (0x00000008), //The LNK file contains a relative path data string
		HasWorkingDir (0x00000010), //The LNK file contains a working directory data string
		HasArguments(0x00000020), //The LNK file contains a command line arguments data string
		HasIconLocation(0x00000040), //The LNK file contains a custom icon location
		IsUnicode(0x00000080), //The data strings in the LNK file are stored in Unicode (UTF-16 little-endian) instead of ASCII
		ForceNoLinkInfo(0x00000100), //The location information is ignored
		HasExpString(0x00000200), //The LNK file contains environment variables location data block
		RunInSeparateProcess(0x00000400), //A 16-bit target application is run in a separate virtual machine.
		Reserved_1(0x00000800),
		HasDarwinID(0x00001000), //The LNK file contains a Darwin (Mac OS-X) properties data block
		RunAsUser(0x00002000), //The target application is run as a different user.
		HasExpIcon(0x00004000), //The LNK file contains an icon location data block
		NoPidlAlias(0x00008000), //The file system location is represented in the shell namespace when the path to an item is parsed into the link target identifiers Contains a known folder location data block?
		Reserved_2(0x00010000),
		RunWithShimLayer(0x00020000), //The target application is run with the shim layer. The LNK file contains shim layer properties data block.
		ForceNoLinkTrack(0x00040000), //The LNK does not contain a distributed link tracking data block
		EnableTargetMetadata(0x00080000), //The LNK file contains a metadata property store data block
		DisableLinkPathTracking(0x00100000), //The environment variables location block should be ignored
		DisableKnownFolderTracking(0x00200000),  //Unknown
		DisableKnownFolderAlias(0x00400000), //Unknown
		AllowLinkToLink(0x00800000), //Unknown
		UnaliasOnSave(0x01000000), //Unknown
		PreferEnvironmentPath(0x02000000), //Unknown
		KeepLocalIDListForUNCTarget(0x04000000); //Unknown
		
		private int flag;
        
        private DataFlags(int flag) {
            this.flag = flag;
        }
        
        public int getFlag() {
            return flag;
        }		
	}

	public static String getDataFlagStr(int dFlag) {
		StringBuilder sb = new StringBuilder(""); //$NON-NLS-1$
        for(DataFlags enumItem : DataFlags.values()) {
            if((dFlag & enumItem.getFlag()) == enumItem.getFlag()) {
                sb.append(enumItem.name());
                sb.append(", "); //$NON-NLS-1$
            }
        }
        int i;
        if ((i = sb.length()) > 0) {
        	sb.delete(i-2, i);
        }
        return sb.toString();
	}

	public String getStringDataFlags() {
		StringBuilder sb = new StringBuilder(""); //$NON-NLS-1$
		if ((DataFlags.HasName.getFlag() & dataLinkFlags) > 0) { 
			sb.append(DataFlags.HasName.name());
			sb.append(", "); //$NON-NLS-1$
		}
		if ((DataFlags.HasRelativePath.getFlag() & dataLinkFlags) > 0) { 
			sb.append(DataFlags.HasRelativePath.name());
			sb.append(", "); //$NON-NLS-1$
		}
		if ((DataFlags.HasWorkingDir.getFlag() & dataLinkFlags) > 0) { 
			sb.append(DataFlags.HasWorkingDir.name());
			sb.append(", "); //$NON-NLS-1$
		}
		if ((DataFlags.HasArguments.getFlag() & dataLinkFlags) > 0) { 
			sb.append(DataFlags.HasArguments.name());
			sb.append(", "); //$NON-NLS-1$
		}
		if ((DataFlags.HasIconLocation.getFlag() & dataLinkFlags) > 0) { 
			sb.append(DataFlags.HasIconLocation.name());
			sb.append(", "); //$NON-NLS-1$
		}
        int i;
        if ((i = sb.length()) > 0) {
        	sb.delete(i-2, i);
        }
        return sb.toString();
	}

	public static String toDateStr(DateFormat df, long ft) { 
		// FileTime do Windows = número de intervalos de 100 nanossegundos desde 1 de janeiro de 1601
		// Date.parse("1/1/1601") == 11644455600000L
		if (ft == 0) return LNKShortcut.DATA_NAO_SETADA;
		long tmpDt = (ft - 0x19db1ded53e8000L) / 10000;
		return df.format(tmpDt); 
	}
}