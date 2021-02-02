package dpf.inc.sepinf.UsnJrnl;

import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class UsnJrnlEntry {
	
	private int tam;
	private int majorVersion;
	private int minorVersion;
	private long mftRef;
	private long parentMftRef;
	private long USN;
	private Date fileTime;
	private int reasonFlag;
	private int sourceInformation;
	private int securityId;
	private int fileAttributes;
	private int sizeofFileName;
	private int offsetFilename;
	private String fileName;
	
	private HashMap<Integer, String> reasonFlags=new HashMap<>();
	private HashMap<Integer, String> fileAttributesToString=new HashMap<>();
	
	public UsnJrnlEntry() {
		initHashMaps();
	}
	
	private void initHashMaps() {
		reasonFlags.put(0x01, "The file was overwritten");
		reasonFlags.put(0x02, "The file or directory was added to");
		reasonFlags.put(0x04, "The file or directory was truncated");
		reasonFlags.put(0x10, "The named data streams for a file is overwritten");
		reasonFlags.put(0x20, "A named data streams for the file were added");
		reasonFlags.put(0x40, "A named data streams for the file was truncated");
		reasonFlags.put(0x100, "The file or directory was created for the first time");
		reasonFlags.put(0x200, "The file or directory was deleted");
		reasonFlags.put(0x400, "The file's or directory's extended attributes were changed");
		reasonFlags.put(0x800, "The access rights to the file or directory was changed");
		reasonFlags.put(0x1000, "The file or directory was renamed(previous name)");
		reasonFlags.put(0x2000, "The file or directory was renamed(new name)");
		reasonFlags.put(0x4000, "A user changed the FILE_ATTRIBUTE_NOT_CONTENT_INDEXED attribute");
		reasonFlags.put(0x8000, "A user has either changed one or more file or directory attributes or one or more time stamps");
		reasonFlags.put(0x10000, "A hard link was added to or removed from the file or directory");
		reasonFlags.put(0x20000, "The compression state of the file or directory was changed from or to compressed.");
		reasonFlags.put(0x40000, "The file or directory was encrypted or decrypted");
		reasonFlags.put(0x80000, "The object identifier of the file or directory was changed");
		reasonFlags.put(0x100000, "The reparse point contained in the file or directory was changed, or a reparse point was added to or deleted from the file ordirectory");
		reasonFlags.put(0x200000, "A named stream has been added to or removed from the file, or a named stream has been renamed.");
		reasonFlags.put(0x80000000, "The file or directory was closed");
		
		
		fileAttributesToString.put(0x01, "READONLY");
		fileAttributesToString.put(0x02, "HIDDEN");
		fileAttributesToString.put(0x04, "SYSTEM");
		fileAttributesToString.put(0x10, "DIRECTORY_ID");
		fileAttributesToString.put(0x20, "FILE/DIRECTORY");
		fileAttributesToString.put(0x40, "RESERVED");
		fileAttributesToString.put(0x80, "A file that does not have other attributes set");
		fileAttributesToString.put(0x100, "TEMPORARY");
		fileAttributesToString.put(0x200, "SPARSE");
		fileAttributesToString.put(0x400, "SYMBOLIC");
		fileAttributesToString.put(0x800, "COMPRESSED");
		fileAttributesToString.put(0x1000, "OFFILINE_STORAGE");
		fileAttributesToString.put(0x2000, "NOT_INDEXED");
		fileAttributesToString.put(0x4000, "ENCRYPTED");
		fileAttributesToString.put(0x8000, "INTEGRITY");
		fileAttributesToString.put(0x10000, "RESERVED");
		fileAttributesToString.put(0x20000, "The user data stream not to be read by the background data integrity scanner");
		
	}
	
	
	public String getReasons() {
		
		StringBuilder sb=new StringBuilder();
		
		for(int k :reasonFlags.keySet()) {
			if( (k & reasonFlag)!=0) {
				if(sb.length()>0) {
					sb.append(", ");
				}
				sb.append(reasonFlags.get(k));
			}
		}
		
		return sb.toString();
		
	}
public String getHumanAttributes() {
		
		StringBuilder sb=new StringBuilder();
		
		for(int k :fileAttributesToString.keySet()) {
			if( (k & this.fileAttributes)!=0) {
				if(sb.length()>0) {
					sb.append(", ");
				}
				sb.append(fileAttributesToString.get(k));
			}
		}
		
		return sb.toString();
		
	}
	
	public int getMajorVersion() {
		return majorVersion;
	}
	public void setMajorVersion(int majorVersion) {
		this.majorVersion = majorVersion;
	}
	public int getMinorVersion() {
		return minorVersion;
	}
	public void setMinorVersion(int minorVersion) {
		this.minorVersion = minorVersion;
	}
	public long getMftRef() {
		return mftRef;
	}
	public void setMftRef(long mftRef) {
		this.mftRef = mftRef;
	}
	public long getParentMftRef() {
		return parentMftRef;
	}
	public void setParentMftRef(long parentMftRef) {
		this.parentMftRef = parentMftRef;
	}
	public long getUSN() {
		return USN;
	}
	public void setUSN(long uSN) {
		USN = uSN;
	}
	public Date getFileTime() {
		return fileTime;
	}
	public void setFileTime(Date fileTime) {
		this.fileTime = fileTime;
	}
	
	
	/** Difference between Filetime epoch and Unix epoch (in ms). */
	private static final long FILETIME_EPOCH_DIFF = 11644473600000L;

	/** One millisecond expressed in units of 100s of nanoseconds. */
	private static final long FILETIME_ONE_MILLISECOND = 10 * 1000;

	public static long filetimeToMillis(final long filetime) {
	    return (filetime / FILETIME_ONE_MILLISECOND) - FILETIME_EPOCH_DIFF;
	}
	
	
	public void setFileTime(long fileTime) {
		this.fileTime = new Date(filetimeToMillis(fileTime));
	}
	public int getReasonFlag() {
		return reasonFlag;
	}
	public void setReasonFlag(int reasonFlag) {
		this.reasonFlag = reasonFlag;
	}
	public int getSourceInformation() {
		return sourceInformation;
	}
	public void setSourceInformation(int sourceInformation) {
		this.sourceInformation = sourceInformation;
	}
	public int getSecurityId() {
		return securityId;
	}
	public void setSecurityId(int securityId) {
		this.securityId = securityId;
	}
	public int getFileAttributes() {
		return fileAttributes;
	}
	public void setFileAttributes(int fileAttributes) {
		this.fileAttributes = fileAttributes;
	}
	public int getSizeofFileName() {
		return sizeofFileName;
	}
	public void setSizeofFileName(int sizeofFileName) {
		this.sizeofFileName = sizeofFileName;
	}
	public int getOffsetFilename() {
		return offsetFilename;
	}
	public void setOffsetFilename(int offsetFilename) {
		this.offsetFilename = offsetFilename;
	}
	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	public int getTam() {
		return tam;
	}
	public void setTam(int tam) {
		this.tam = tam;
	}

}
