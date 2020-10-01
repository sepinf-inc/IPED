package dpf.inc.sepinf.gdrive.parsers;

/**
 * Google Drive Snapshot Entry bean
 * 
 * @author Matheus Bichara de Assumpção <bda.matheus@gmail.com>
 */

public class SnapshotEntry {

	private String aclRole;
	private String docType;
	private String parent;
	private String filename;
	private String md5;
	private String modified;
	private String cloudSize;
	private String originalSize;
	private String removed;
	private String shared;
	private String isFolder;
	private String localParent;
	private String localFilename;
	private String localSize;
	private String localMd5;
	private String localModified;
	private String md5Check;
	private String cloudLocalDatesCheck;
	private String volume;
	private String childVolume;
	private String parentVolume;
	public String getAclRole() {
		return aclRole;
	}
	public void setAclRole(String aclRole) {
		this.aclRole = aclRole;
	}
	public String getDocType() {
		return docType;
	}
	public void setDocType(String docType) {
		this.docType = docType;
	}
	public String getParent() {
		return parent;
	}
	public void setParent(String parent) {
		this.parent = parent;
	}
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public String getMd5() {
		return md5;
	}
	public void setMd5(String md5) {
		this.md5 = md5;
	}
	public String getModified() {
		return modified;
	}
	public void setModified(String modified) {
		this.modified = modified;
	}
	public String getCloudSize() {
		return cloudSize;
	}
	public void setCloudSize(String cloudSize) {
		this.cloudSize = cloudSize;
	}
	public String getOriginalSize() {
		return originalSize;
	}
	public void setOriginalSize(String originalSize) {
		this.originalSize = originalSize;
	}
	public String getRemoved() {
		return removed;
	}
	public void setRemoved(String removed) {
		this.removed = removed;
	}
	public String getShared() {
		return shared;
	}
	public void setShared(String shared) {
		this.shared = shared;
	}
	public String getIsFolder() {
		return isFolder;
	}
	public void setIsFolder(String isFolder) {
		this.isFolder = isFolder;
	}
	public String getLocalParent() {
		return localParent;
	}
	public void setLocalParent(String localParent) {
		this.localParent = localParent;
	}
	public String getLocalFilename() {
		return localFilename;
	}
	public void setLocalFilename(String localFilename) {
		this.localFilename = localFilename;
	}
	public String getLocalSize() {
		return localSize;
	}
	public void setLocalSize(String localSize) {
		this.localSize = localSize;
	}
	public String getLocalMd5() {
		return localMd5;
	}
	public void setLocalMd5(String localMd5) {
		this.localMd5 = localMd5;
	}
	public String getLocalModified() {
		return localModified;
	}
	public void setLocalModified(String localModified) {
		this.localModified = localModified;
	}
	public String getMd5Check() {
		return md5Check;
	}
	public void setMd5Check(String md5Check) {
		this.md5Check = md5Check;
	}
	public String getCloudLocalDatesCheck() {
		return cloudLocalDatesCheck;
	}
	public void setCloudLocalDatesCheck(String cloudLocalDatesCheck) {
		this.cloudLocalDatesCheck = cloudLocalDatesCheck;
	}
	public String getVolume() {
		return volume;
	}
	public void setVolume(String volume) {
		this.volume = volume;
	}
	public String getChildVolume() {
		return childVolume;
	}
	public void setChildVolume(String childVolume) {
		this.childVolume = childVolume;
	}
	public String getParentVolume() {
		return parentVolume;
	}
	public void setParentVolume(String parentVolume) {
		this.parentVolume = parentVolume;
	}
		
}
