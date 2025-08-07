package iped.engine.task.aleapp;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.data.ICaseData;
import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

/**
 * A utility class containing helper methods for ALEAPP.
 */
public final class AleappUtils {

    private AleappUtils() {
    }

    public static String globToLuceneQuery(String basePath, String globPattern) {
        StringBuilder query = new StringBuilder();
        query.append(BasicProps.PATH).append(":\"").append(basePath).append("\"");

        String globQuery = globToLuceneQuery(globPattern);
        if (StringUtils.isNotBlank(globQuery)) {
            query.append(" && (").append(globQuery).append(")");
        }

        query.append(" && -fileFragment:true");

        return query.toString();
    }

    /**
     * Converts a glob pattern to a Lucene query.
     *
     * @param glob The glob pattern to convert.
     * @return A Lucene query string, or an empty string if the input is blank.
     */
    public static String globToLuceneQuery(String glob) {

        if (StringUtils.isBlank(glob)) {
            return StringUtils.EMPTY;
        }

        String trimmedGlob = StringUtils.trim(glob);

        if (!StringUtils.contains(trimmedGlob, '/')) {
            String cleanedGlob = StringUtils.replace(trimmedGlob, "*", "");
            return String.format("%s:\"%s\"", BasicProps.NAME, cleanedGlob);
        }

        Path globPath = Paths.get(trimmedGlob);
        Path parent = globPath.getParent();
        String fileName = globPath.getFileName().toString();

        // Determine the parent path string; it can be null for root-level files.
        String parentPathString = (parent != null) ? parent.toString() : "";

        String[] cleanedPaths = StringUtils.split(parentPathString, '*');
        String pathQuery = Arrays
                .stream(cleanedPaths)
                .map(term -> StringUtils.strip(term, "/"))
                .filter(StringUtils::isNotBlank)
                .map(term -> String.format("%s:\"%s\"", BasicProps.PATH, term))
                .collect(Collectors.joining(" && "));

        if (StringUtils.equalsAny(fileName, "*", "*.*")) {
            return pathQuery;
        }

        String cleanedName = StringUtils.replace(fileName, "*", "");
        String nameQuery = StringUtils.isNotBlank(cleanedName) ? String.format("%s:\"%s\"", BasicProps.NAME, cleanedName) : "";

        if (StringUtils.isNoneBlank(pathQuery, nameQuery)) {
            return String.format("(%s) && (%s)", pathQuery, nameQuery);
        } else if (StringUtils.isNotBlank(nameQuery)) {
            return nameQuery;
        } else {
            return pathQuery;
        }
    }

    public static IItemSearcher getSearcher(ICaseData caseData) {
        return (IItemSearcher) caseData.getCaseObject(IItemSearcher.class.getName());
    }

    public static IItemReader findItemByPath(ICaseData caseData, String path) {
        String itemPath = StringUtils.substringAfter(path, FileSeeker.IPED_PATH_PREFIX);
        String query = BasicProps.PATH + ":\"" + itemPath + "\"";
        return getSearcher(caseData).search(query).stream().filter(item -> itemPath.equals(item.getPath())).findFirst().orElse(null);
    }

}
