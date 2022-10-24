package iped.parsers.mail.win10.entries;

public class AttachmentEntry extends AbstractEntry {
    private long messageId;
    private long attachSize;
    private long attachCID;
    private String fileName;
    private String originalFileName;
	private String mimeTag;
    private Boolean received;
	private String caseQuery;

    public AttachmentEntry(int rowId) {
		super(rowId);
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

	public String getOriginalFileName() {
		return this.originalFileName;
	}

	public void setOriginalFileName(String originalFileName) {
		this.originalFileName = originalFileName;
	}
	
	public String getMimeTag() {
		return this.mimeTag;
	}

	public void setMimeTag(String mimeTag) {
		this.mimeTag = mimeTag;
	}

	public void setReceived(Boolean received) {
		this.received = received;
	}

    public Boolean isReceived() {
        return this.received;
    }

	public String getCaseQuery() {
		return this.caseQuery;
	}

	public void setCaseQuery(String caseQuery) {
		this.caseQuery = caseQuery;
	}

}
