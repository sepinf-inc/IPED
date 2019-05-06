package macee.instance;

import java.io.Serializable;

public interface StorageInfo extends Serializable {

    String getVolumeLabel();

    String getAbsolutePath();

    long getAvailableBytes();

    boolean writable();
}
