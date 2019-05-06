package dpf.sp.gpinf.indexer.parsers.lnk;

public class LNKShellItemNetwork {
	private int typeClass;
	private byte flags;
	
	private String Location, Description, Comments;

	public int getTypeClass() {
		return typeClass;
	}

	public void setTypeClass(int typeClass) {
		this.typeClass = typeClass;
	}

	public byte getFlags() {
		return flags;
	}

	public void setFlags(byte flags) {
		this.flags = flags;
	}

	public String getLocation() {
		return Location;
	}

	public void setLocation(String location) {
		Location = location;
	}

	public String getDescription() {
		return Description;
	}
	
	public void setDescription(String description) {
		Description = description;
	}

	public String getComments() {
		return Comments;
	}

	public void setComments(String comments) {
		Comments = comments;
	}

	public String getTypeStr() {
		if ((typeClass & 0Xf) == 0X01)
			return "Domain/Workgroup name"; //$NON-NLS-1$
		else if ((typeClass & 0Xf) == 0X02)
			return "Server UNC path"; //$NON-NLS-1$
		else if ((typeClass & 0Xf) == 0X03)
			return "Share UNC path"; //$NON-NLS-1$
		else if ((typeClass & 0Xf) == 0X06)
			return "Microsoft Windows Network"; //$NON-NLS-1$
		else if ((typeClass & 0Xf) == 0X07)
			return "Entire Network"; //$NON-NLS-1$
		else 
			return "Unknown"; //$NON-NLS-1$
	}
}