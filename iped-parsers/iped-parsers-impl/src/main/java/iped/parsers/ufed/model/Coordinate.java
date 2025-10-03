package iped.parsers.ufed.model;

import java.util.StringJoiner;

import iped.data.IItemReader;
import iped.parsers.ufed.reference.ReferencedLocation;

/**
 * Represents a <model type="Coordinate"> element.
 */
public class Coordinate extends BaseModel {

    private static final long serialVersionUID = -958609997234875590L;

    private transient ReferencedLocation referencedLocation;

    public Coordinate() {
        super("Coordinate");
    }

    public String getLongitude() { return (String) getField("Longitude"); }
    public String getLatitude() { return (String) getField("Latitude"); }

    public ReferencedLocation getReferencedLocation() {
        return referencedLocation;
    }

    public ReferencedLocation setReferencedLocation(IItemReader locationItem) {
        referencedLocation = new ReferencedLocation(locationItem);
        return referencedLocation;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Coordinate.class.getSimpleName() + "[", "]")
                .add("Latitude=" + getLatitude())
                .add("Longitude=" + getLongitude())
                .toString();
    }
}