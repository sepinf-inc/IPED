package iped.parsers.zoomdpapi;

/**
 * System/hardware information extracted from the zoom_kv table.
 *
 * @author Calil Khalil (Hakal)
 */
public class ZoomSystemInfo {

    private String processor;
    private String videoController;
    private String computerSystem;
    private String clientGuid;
    private String fingerprint;

    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }

    public String getVideoController() { return videoController; }
    public void setVideoController(String videoController) { this.videoController = videoController; }

    public String getComputerSystem() { return computerSystem; }
    public void setComputerSystem(String computerSystem) { this.computerSystem = computerSystem; }

    public String getClientGuid() { return clientGuid; }
    public void setClientGuid(String clientGuid) { this.clientGuid = clientGuid; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
}
