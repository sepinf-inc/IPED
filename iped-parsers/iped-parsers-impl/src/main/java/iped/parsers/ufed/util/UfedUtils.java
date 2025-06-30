package iped.parsers.ufed.util;

import java.util.Arrays;
import java.util.List;

import org.apache.tika.metadata.Metadata;

import iped.data.IItemReader;
import iped.properties.ExtraProperties;

public class UfedUtils {

    public static String readUfedMetadata(Metadata metadata, String property) {
        return metadata.get(ExtraProperties.UFED_META_PREFIX + property);
    }

    public static String readUfedMetadata(IItemReader item, String property) {
        return readUfedMetadata(item.getMetadata(), property);
    }

    public static List<String> readUfedMetadataArray(Metadata metadata, String property) {
        return Arrays.asList(metadata.getValues(ExtraProperties.UFED_META_PREFIX + property));
    }

    public static List<String> readUfedMetadataArray(IItemReader item, String property) {
        return readUfedMetadataArray(item.getMetadata(), property);
    }

    public static void removeUfedMetadata(Metadata metadata, String property) {
        metadata.remove(ExtraProperties.UFED_META_PREFIX + property);
    }

    public static void removeUfedMetadata(IItemReader item, String property) {
        removeUfedMetadata(item.getMetadata(), property);
    }
}