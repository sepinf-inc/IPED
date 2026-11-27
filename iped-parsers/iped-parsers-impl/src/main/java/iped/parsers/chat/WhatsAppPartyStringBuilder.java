package iped.parsers.chat;

import org.apache.commons.lang3.StringUtils;

import iped.parsers.whatsapp.WAContact;
import iped.parsers.whatsapp.WhatsAppParser;

public class WhatsAppPartyStringBuilder extends PartyStringBuilder {

    private static final String PHONE_PREFIX = "+";
    private static final String FIELDS_SEPARATOR = " | ";

    @Override
    public String build() {

        String phoneFromUserID = null;
        if (StringUtils.endsWithAny(userId, WAContact.waSuffix, WAContact.waStatusSuffix)) {
            phoneFromUserID = StringUtils.substringBeforeLast(userId, "@");
        }

        String phoneStr = StringUtils.firstNonBlank(phoneFromUserID, phoneNumber);
        if (StringUtils.isNotBlank(phoneStr)) {
            phoneStr = StringUtils.prependIfMissing(phoneStr, PHONE_PREFIX);
        }

        String usernameStr = StringUtils.isNotBlank(username) ? StringUtils.prependIfMissing(username, "@") : null;

        if (StringUtils.isNotBlank(name)) {
            if (name.equals(WhatsAppParser.UNKNOWN_ACCOUNT)) {
                return name;
            }
            if (StringUtils.equalsAny(StringUtils.removeStart(name, PHONE_PREFIX), StringUtils.removeStart(phoneStr, PHONE_PREFIX))) {
                // name is the phone number itself
                name = phoneStr;
                phoneStr = null;
            }
            String fieldsStr = join(usernameStr, phoneStr);
            return StringUtils.isBlank(fieldsStr) ? name : name + " (" + fieldsStr + ")";
        } else {
            return join(usernameStr, phoneStr);
        }
    }

    private static String join(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                if (sb.length() > 0) {
                    sb.append(FIELDS_SEPARATOR);
                }
                sb.append(v);
            }
        }
        return sb.toString();
    }
}
