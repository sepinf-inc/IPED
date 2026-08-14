package iped.app.ui.ai.util;

import iped.data.IItem;
import iped.properties.ExtraProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Normalizes the flexible storage formats used for AI summaries.
 */
public final class SummaryValueExtractor {

    private SummaryValueExtractor() {
    }

    public static boolean hasSummary(IItem item) {
        return !extractSummaryValues(item).isEmpty();
    }

    public static String extractSummary(IItem item) {
        List<String> summaries = extractSummaryValues(item);
        if (summaries.isEmpty()) {
            return null;
        }

        String joined = String.join("\n", summaries).trim();
        return joined.isEmpty() ? null : joined;
    }

    private static List<String> extractSummaryValues(IItem item) {
        List<String> values = new ArrayList<>();
        if (item == null) {
            return values;
        }

        addValues(values, item.getExtraAttribute(ExtraProperties.SUMMARY));
        if (!values.isEmpty()) {
            return values;
        }

        if (item.getMetadata() == null) {
            return values;
        }

        String[] metadataValues = item.getMetadata().getValues(ExtraProperties.SUMMARY);
        if (metadataValues != null) {
            for (String value : metadataValues) {
                addValue(values, value);
            }
        }

        if (values.isEmpty()) {
            addValue(values, item.getMetadata().get(ExtraProperties.SUMMARY));
        }

        return values;
    }

    private static void addValues(List<String> values, Object extraValue) {
        if (extraValue instanceof String) {
            addValue(values, extraValue);
        } else if (extraValue instanceof Collection<?>) {
            for (Object value : (Collection<?>) extraValue) {
                addValue(values, value);
            }
        } else if (extraValue instanceof Object[]) {
            for (Object value : (Object[]) extraValue) {
                addValue(values, value);
            }
        } else if (extraValue instanceof String[]) {
            for (String value : (String[]) extraValue) {
                addValue(values, value);
            }
        }
    }

    private static void addValue(List<String> values, Object value) {
        if (value == null) {
            return;
        }

        String text = value.toString().trim();
        if (!text.isEmpty()) {
            values.add(text);
        }
    }
}