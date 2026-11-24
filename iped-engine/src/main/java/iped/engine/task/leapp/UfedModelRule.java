package iped.engine.task.leapp;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.engine.datasource.UfedXmlReader;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;

/**
 * Identifies the models decoded by UFED that hold the same data a LEAPP plugin decodes: a model type (e.g. "Call",
 * "Chat") and, optionally, the source application UFED reports for it (e.g. "Telegram").
 *
 * <p>
 * Written as {@code Type} (any source), {@code Type:Source} or {@code Type:-} (models with no source at all, as UFED
 * reports the native call log and contact list).
 */
public class UfedModelRule implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String NO_SOURCE = "-";

    private static final String SOURCE_META = ExtraProperties.UFED_META_PREFIX + "Source";

    private final String type;

    // null: any source, empty: models with no source
    private final String source;

    public UfedModelRule(String type, String source) {
        this.type = Objects.requireNonNull(type);
        this.source = source;
    }

    public static UfedModelRule parse(String text) {
        String type = StringUtils.strip(StringUtils.substringBefore(text, ":"));
        if (StringUtils.isEmpty(type)) {
            throw new IllegalArgumentException("Invalid UFED model, expected Type[:Source]: '" + text + "'");
        }
        if (!StringUtils.contains(text, ':')) {
            return new UfedModelRule(type, null);
        }
        String source = StringUtils.strip(StringUtils.substringAfter(text, ":"));
        if (StringUtils.isEmpty(source)) {
            throw new IllegalArgumentException("Invalid UFED model, missing source (use '" + NO_SOURCE
                    + "' for models with no source): '" + text + "'");
        }
        return new UfedModelRule(type, NO_SOURCE.equals(source) ? "" : source);
    }

    /** Parses a comma separated list of models. */
    public static List<UfedModelRule> parseList(String text) {
        List<UfedModelRule> rules = new ArrayList<>();
        for (String value : StringUtils.split(StringUtils.defaultString(text), ',')) {
            if (StringUtils.isNotBlank(value)) {
                rules.add(parse(value));
            }
        }
        return rules;
    }

    public String getType() {
        return type;
    }

    public String getSource() {
        return source;
    }

    private String getMediaType() {
        // media types are always lower case (see MediaType)
        return ("application/" + UfedXmlReader.UFED_MIME_PREFIX + type).toLowerCase();
    }

    /**
     * The query that selects the candidate items in the case. It may select more than the models of this rule (the
     * path and the source are matched by their terms), so each hit must be confirmed with {@link #matches}.
     *
     * @param decodedDataPath the in-case path of the decoded data folder of the ufdr
     */
    String toQuery(IItemSearcher searcher, String decodedDataPath) {
        StringBuilder query = new StringBuilder();
        query.append(LeappUtils.phrase(BasicProps.CONTENTTYPE, getMediaType()));
        query.append(" && ").append(LeappUtils.phrase(BasicProps.PATH, decodedDataPath));
        if (StringUtils.isNotEmpty(source)) {
            query.append(" && ").append(LeappUtils.phrase(searcher.escapeQuery(SOURCE_META), source));
        }
        return query.toString();
    }

    boolean matches(IItemReader item, String decodedDataPath) {
        if (item.getMediaType() == null || !getMediaType().equals(item.getMediaType().toString())
                || !StringUtils.startsWith(item.getPath(), decodedDataPath + "/")) {
            return false;
        }
        if (source == null) {
            return true;
        }
        String itemSource = StringUtils.trimToEmpty(item.getMetadata().get(SOURCE_META));
        return source.equalsIgnoreCase(itemSource);
    }

    /**
     * Whether the ufdr holds at least one model of this rule.
     *
     * @param ufdrPath the in-case path of the ufdr item
     */
    public boolean existsIn(IItemSearcher searcher, String ufdrPath) {
        String decodedDataPath = ufdrPath + "/" + UfedXmlReader.DECODED_DATA_FOLDER_NAME;
        // iterating lazily loads only the items up to the first confirmed one
        for (IItemReader item : searcher.searchIterable(toQuery(searcher, decodedDataPath))) {
            if (matches(item, decodedDataPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UfedModelRule)) {
            return false;
        }
        UfedModelRule other = (UfedModelRule) obj;
        return type.equalsIgnoreCase(other.type) && StringUtils.equalsIgnoreCase(source, other.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type.toLowerCase(), StringUtils.lowerCase(source));
    }

    @Override
    public String toString() {
        if (source == null) {
            return type;
        }
        return type + ":" + (source.isEmpty() ? NO_SOURCE : source);
    }
}
