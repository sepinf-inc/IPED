package dpf.sp.gpinf.indexer.parsers.lnk;

public class LNKLinkTracker {
	private String machineId, droidVolumeId, droidFileId, birthDroidVolumeId, birthDroidFileId;

	public String getMachineId() {
		return machineId;
	}

	public void setMachineId(String machineId) {
		this.machineId = machineId;
	}

	public String getDroidVolumeId() {
		return droidVolumeId;
	}

	public void setDroidVolumeId(String droidVolumeId) {
		this.droidVolumeId = droidVolumeId;
	}

	public String getDroidFileId() {
		return droidFileId;
	}

	public void setDroidFileId(String droidFileId) {
		this.droidFileId = droidFileId;
	}

	public String getBirthDroidVolumeId() {
		return birthDroidVolumeId;
	}

	public void setBirthDroidVolumeId(String birthDroidVolumeId) {
		this.birthDroidVolumeId = birthDroidVolumeId;
	}

	public String getBirthDroidFileId() {
		return birthDroidFileId;
	}

	public void setBirthDroidFileId(String birthDroidFileId) {
		this.birthDroidFileId = birthDroidFileId;
	}
}