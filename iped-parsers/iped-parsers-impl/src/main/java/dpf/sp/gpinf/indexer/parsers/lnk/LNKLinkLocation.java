package dpf.sp.gpinf.indexer.parsers.lnk;

public class LNKLinkLocation {
    private String volumeLabel, localPath, commonPath, driveSerial;
    private String localPathUnicode, commonPathUnicode, volumeLabelUnicode;
    private String netShare, netDevName;
    private String netShareUnicode, netDevNameUnicode;
    private int flagsLocation, flagsNetwork, netProviderType, driveType;

    public String getDriveSerial() {
        return driveSerial;
    }

    public void setDriveSerial(String driveSerial) {
        if (driveSerial != null)
            this.driveSerial = "0x" + driveSerial; //$NON-NLS-1$
        else
            this.driveSerial = null;
    }

    public int getDriveType() {
        return driveType;
    }

    public void setDriveType(int driveType) {
        this.driveType = driveType;
    }

    public String getVolumeLabel() {
        return volumeLabel;
    }

    public void setVolumeLabel(String volumeLabel) {
        this.volumeLabel = volumeLabel;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getCommonPath() {
        return commonPath;
    }

    public void setCommonPath(String commonPath) {
        this.commonPath = commonPath;
    }

    public String getNetShare() {
        return netShare;
    }

    public void setNetShare(String netShare) {
        this.netShare = netShare;
    }

    public String getNetDevName() {
        return netDevName;
    }

    public void setNetDevName(String netDevName) {
        this.netDevName = netDevName;
    }

    public int getNetProviderType() {
        return netProviderType;
    }

    public void setNetProviderType(int netProviderType) {
        this.netProviderType = netProviderType;
    }

    public int getFlagsLocation() {
        return flagsLocation;
    }

    public void setFlagsLocation(int flagsLocation) {
        this.flagsLocation = flagsLocation;
    }

    public int getFlagsNetwork() {
        return flagsNetwork;
    }

    public void setFlagsNetwork(int flagsNetwork) {
        this.flagsNetwork = flagsNetwork;
    }

    public String getLocalPathUnicode() {
        return localPathUnicode;
    }

    public void setLocalPathUnicode(String localPathUnicode) {
        this.localPathUnicode = localPathUnicode;
    }

    public String getCommonPathUnicode() {
        return commonPathUnicode;
    }

    public void setCommonPathUnicode(String commonPathUnicode) {
        this.commonPathUnicode = commonPathUnicode;
    }

    public String getNetShareUnicode() {
        return netShareUnicode;
    }

    public void setNetShareUnicode(String netShareUnicode) {
        this.netShareUnicode = netShareUnicode;
    }

    public String getNetDevNameUnicode() {
        return netDevNameUnicode;
    }

    public void setNetDevNameUnicode(String netDevNameUnicode) {
        this.netDevNameUnicode = netDevNameUnicode;
    }

    public String getVolumeLabelUnicode() {
        return volumeLabelUnicode;
    }

    public void setVolumeLabelUnicode(String volumeLabelUnicode) {
        this.volumeLabelUnicode = volumeLabelUnicode;
    }

    public String getDriveTypeStr() {
        switch (driveType) {
            case 1:
                return "DRIVE_NO_ROOT_DIR"; //$NON-NLS-1$
            case 2:
                return "DRIVE_REMOVABLE"; //$NON-NLS-1$
            case 3:
                return "DRIVE_FIXED"; //$NON-NLS-1$
            case 4:
                return "DRIVE_REMOTE"; //$NON-NLS-1$
            case 5:
                return "DRIVE_CDROM"; //$NON-NLS-1$
            case 6:
                return "DRIVE_RAMDISK"; //$NON-NLS-1$
            default:
                return "DRIVE_UNKNOWN"; //$NON-NLS-1$
        }
    }
}
