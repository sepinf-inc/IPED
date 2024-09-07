package iped.parsers.ufed;

import java.util.Arrays;
import java.util.List;

import iped.data.IItemReader;
import iped.properties.ExtraProperties;

public class UfedUtils {

    public static String readUfedMetadata(IItemReader item, String property) {
        return item.getMetadata().get(ExtraProperties.UFED_META_PREFIX + property);
    }
    
    public static List<String> readUfedMetadataArray(IItemReader item, String property) {
        return Arrays.asList(item.getMetadata().getValues(ExtraProperties.UFED_META_PREFIX + property));
    }
}
