package iped.parsers.ufed.reference;

import static iped.parsers.ufed.util.UfedUtils.readUfedMetadata;

import iped.data.IItemReader;
import iped.properties.ExtraProperties;

public class ReferencedLocation extends AbstractReferencedItem {

    public ReferencedLocation(IItemReader item) {
        super(item);
    }

    public String getLocations() {
        return item.getMetadata().get(ExtraProperties.LOCATIONS);
    }

    public String getType() {
        return readUfedMetadata(item, "Type");
    }

    public String getName() {
        return readUfedMetadata(item, "Name");
    }

    public String getDescription() {
        return readUfedMetadata(item, "Description");
    }

    public String getStreet1() {
        return readUfedMetadata(item, "Street1");
    }

    public String getHouseNumber() {
        return readUfedMetadata(item, "HouseNumber");
    }

    public String getCity() {
        return readUfedMetadata(item, "City");
    }

    public String getState() {
        return readUfedMetadata(item, "State");
    }

    public String getCountry() {
        return readUfedMetadata(item, "Country");
    }

    public String getPositionAddress() {
        return readUfedMetadata(item, "PositionAddress");
    }
}