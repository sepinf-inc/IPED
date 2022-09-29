package iped.parsers.mail.win10.entries;

public class AttachmentEntry extends AbstractEntry {
    private int rowId;
    private long messageId;
    private long attachSize;
    private long attachCID;
    private String fileName;
    private String attachFilePath;
    private Boolean received;

    public AttachmentEntry(int rowId, int messageId, long attachSize, long attachCID, String fileName, String filePath, Boolean received) {
        super(rowId);
        this.messageId = messageId;
        this.attachSize = attachSize;
        this.fileName = fileName;
        this.attachFilePath = filePath;
        this.received = received;
    }

	public int getRowId() {
		return this.rowId;
	}

	public void setRowId(int rowId) {
		this.rowId = rowId;
	}

	public long getMessageId() {
		return this.messageId;
	}

	public void setMessageId(long messageId) {
		this.messageId = messageId;
	}

	public long getAttachSize() {
		return this.attachSize;
	}

	public void setAttachSize(long attachSize) {
		this.attachSize = attachSize;
	}

	public long getAttachCID() {
		return this.attachCID;
	}

	public void setAttachCID(long attachCID) {
		this.attachCID = attachCID;
	}

	public String getFileName() {
		return this.fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
    public String getAttachFilePath() {
		return this.attachFilePath;
	}

	public void setAttachFilePath(String filePath) {
		this.attachFilePath = filePath;
	}

	public void setReceived(Boolean received) {
		this.received = received;
	}

    public Boolean isReceived() {
        return this.received;
    }
}
