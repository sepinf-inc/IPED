package iped.parsers.util;

import org.apache.commons.lang3.StringUtils;

public class ConversationUtils {

    public static final String TYPE_PRIVATE = "Private";
    public static final String TYPE_GROUP = "Group";
    public static final String TYPE_BROADCAST = "Broadcast";
    public static final String TYPE_UNKONWN = "Unknown";

    public static final String DIRECTION_INCOMING = "Incoming";
    public static final String DIRECTION_OUTGOING = "Outgoing";

    private static final String FIELD_SEPARATOR = "|";

    private ConversationUtils() {
    }

    public static String buidPartyString(String name, String id, String phone, String username, String source) {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name);
        }

        boolean allBlank = StringUtils.isAllBlank(id, phone, username);
        if (!allBlank) {
            if (name != null) {
                sb.append(" ");
            }
            sb.append("(");
            boolean needToSeparate = false;
            if ("WhatsApp".equalsIgnoreCase(source)) {
                String phoneFromId = StringUtils.substringBeforeLast(id, "@");
                sb.append("+").append(StringUtils.firstNonBlank(phone, phoneFromId));
            } else {
                if (id != null) {
                    sb.append("ID:").append(id);
                    needToSeparate = true;
                }
                if (phone != null) {
                    if (needToSeparate) {
                        sb.append(FIELD_SEPARATOR);
                    }
                    sb.append("tel:").append(phone);
                    needToSeparate = true;
                }
                if (username != null) {
                    if (needToSeparate) {
                        sb.append(FIELD_SEPARATOR);
                    }
                    sb.append("@").append(username);
                }
            }
            sb.append(")");
        }

        return sb.toString();
    }
}
