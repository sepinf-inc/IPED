package iped.engine.task.leapp;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

/**
 * What a LEAPP tool runs over the file system of a ufdr, whose data was already decoded by UFED and is imported by
 * IPED from the report.
 */
public enum UfdrDecodingMode {

    /** Runs every enabled plugin: results may duplicate the data decoded by UFED. */
    ALL,

    /** Prefers the data decoded by UFED: plugins whose equivalent UFED models exist in the ufdr are skipped. */
    EXTERNAL,

    /** Does not run the tool over ufdr extractions. */
    NONE;

    public static UfdrDecodingMode parse(String value) {
        for (UfdrDecodingMode mode : values()) {
            if (mode.name().equalsIgnoreCase(StringUtils.strip(value))) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid ufdr decoding mode '" + value + "', possible values: "
                + Arrays.stream(values()).map(mode -> mode.name().toLowerCase()).collect(Collectors.joining(", ")));
    }
}
