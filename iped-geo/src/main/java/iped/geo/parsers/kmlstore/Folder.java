package iped.geo.parsers.kmlstore;

import java.util.List;

public class Folder {
    List<Object> features;
    String name;
    boolean isTrack;

    public List<Object> getFeatures() {
        return features;
    }

    public void setFeatures(List<Object> features) {
        this.features = features;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isTrack() {
        return isTrack;
    }

    public void setTrack(boolean isTrack) {
        this.isTrack = isTrack;
    }
}
