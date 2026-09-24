package iped.app.ui.ai.util;

import iped.data.IItem;
import iped.engine.lucene.analysis.CategoryTokenizer;
import iped.properties.ExtraProperties;

public class ContextItemValidator {

    private final AIWhatsappChatExtractor chatExtractor;

    public ContextItemValidator() {
        this(new AIWhatsappChatExtractor());
    }

    public ContextItemValidator(AIWhatsappChatExtractor chatExtractor) {
        this.chatExtractor = chatExtractor;
    }

    public String getRejectionReason(IItem item) {
        if (!chatExtractor.isWhatsAppChatType(item)) {
            return "Rejected: Not a WhatsApp chat item.";
        }

        if (hasEmptyFilesCategory(item)) {
            return "Rejected: Category is Empty Files.";
        }

        if (SummaryValueExtractor.hasSummary(item)) {
            return null;
        }

        Boolean isEmpty = readCommunicationIsEmpty(item);
        if (Boolean.TRUE.equals(isEmpty)) {
            return "Rejected: Communication is empty.";
        }
        return null;
    }

    private boolean hasEmptyFilesCategory(IItem item) {
        if (item == null) {
            return false;
        }

        if (item.getCategorySet() != null) {
            for (String category : item.getCategorySet()) {
                if (isEmptyFilesCategoryValue(category)) {
                    return true;
                }
            }
        }

        String categories = item.getCategories();
        if (categories != null && !categories.isBlank()) {
            String[] splitCategories = categories.split(String.valueOf(CategoryTokenizer.SEPARATOR));
            for (String category : splitCategories) {
                if (isEmptyFilesCategoryValue(category)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isEmptyFilesCategoryValue(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase();
        return normalized.equals("empty files") || normalized.contains("empty files");
    }

    private Boolean readCommunicationIsEmpty(IItem item) {
        if (item == null) {
            return null;
        }

        String[] keys = {
            ExtraProperties.COMMUNICATION_PREFIX + "isEmpty"
        };

        for (String key : keys) {
            String value = readFirstValue(item, key);
            if (value != null) {
                return Boolean.parseBoolean(value.trim().toLowerCase());
            }
        }
        return null;
    }

    private String readFirstValue(IItem item, String key) {
        Object extra = item.getExtraAttribute(key);
        if (extra != null) {
            if (extra instanceof String) {
                return (String) extra;
            }
            if (extra instanceof Boolean) {
                return String.valueOf(extra);
            }
            if (extra instanceof String[] && ((String[]) extra).length > 0) {
                return ((String[]) extra)[0];
            }
            return String.valueOf(extra);
        }

        if (item.getMetadata() == null) {
            return null;
        }

        String value = item.getMetadata().get(key);
        if (value != null) {
            return value;
        }

        String[] values = item.getMetadata().getValues(key);
        if (values != null && values.length > 0) {
            return values[0];
        }

        return null;
    }
}
