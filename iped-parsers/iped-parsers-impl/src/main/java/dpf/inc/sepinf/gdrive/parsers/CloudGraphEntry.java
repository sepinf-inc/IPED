package dpf.inc.sepinf.gdrive.parsers;

/**
 * Google Drive CloudGraph Entry bean
 * 
 * @author Matheus Bichara de Assumpção <bda.matheus@gmail.com>
 */

public class CloudGraphEntry {

	private String parent;
	private String filename;
	private String size;
	private String md5;
	private String doc_type;
	private String shared;
	private String modified;
	private String version;
	private String acl_role;
	private String download_restricted;
	private String photos_storage_policy;
	private String down_sample_status;
	private String doc_id;
	private String parent_doc_id;
	
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
	public String getSize() {
		return size;
	}
	public void setSize(String size) {
		this.size = size;
	}
	public String getMd5() {
		return md5;
	}
	public void setMd5(String md5) {
		this.md5 = md5;
	}
	public String getDoc_type() {
		return doc_type;
	}
	public void setDoc_type(String doc_type) {
		this.doc_type = doc_type;
	}
	public String getShared() {
		return shared;
	}
	public void setShared(String shared) {
		this.shared = shared;
	}
	public String getModified() {
		return modified;
	}
	public void setModified(String modified) {
		this.modified = modified;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getAcl_role() {
		return acl_role;
	}
	public void setAcl_role(String acl_role) {
		this.acl_role = acl_role;
	}
	public String getDownload_restricted() {
		return download_restricted;
	}
	public void setDownload_restricted(String download_restricted) {
		this.download_restricted = download_restricted;
	}
	public String getPhotos_storage_policy() {
		return photos_storage_policy;
	}
	public void setPhotos_storage_policy(String photos_storage_policy) {
		this.photos_storage_policy = photos_storage_policy;
	}
	public String getDown_sample_status() {
		return down_sample_status;
	}
	public void setDown_sample_status(String down_sample_status) {
		this.down_sample_status = down_sample_status;
	}
	public String getDoc_id() {
		return doc_id;
	}
	public void setDoc_id(String doc_id) {
		this.doc_id = doc_id;
	}
	public String getParent_doc_id() {
		return parent_doc_id;
	}
	public void setParent_doc_id(String parent_doc_id) {
		this.parent_doc_id = parent_doc_id;
	}

	
	
}
