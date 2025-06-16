package iped.parsers.ufed.model;

import java.util.StringJoiner;

/**
 * Represents a <model type="Coordinate"> element.
 */
public class Coordinate extends BaseModel {

    private static final long serialVersionUID = -958609997234875590L;

    public Coordinate() {
        super("Coordinate");
    }

    public Double getLongitude() { return (Double) getField("Longitude"); }
    public Double getLatitude() { return (Double) getField("Latitude"); }

    @Override
    public String toString() {
        return new StringJoiner(", ", Coordinate.class.getSimpleName() + "[", "]")
                .add("Latitude=" + getLatitude())
                .add("Longitude=" + getLongitude())
                .toString();
    }
}