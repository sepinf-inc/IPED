package iped.parsers.chat;

import org.apache.commons.lang3.StringUtils;

import iped.parsers.whatsapp.WAContact;
import iped.parsers.whatsapp.WhatsAppParser;

public class WhatsAppPartyStringBuilder extends PartyStringBuilder {

    private static final String PHONE_PREFIX = "+";

    @Override
    public String build() {

        String phoneFromUserID = null;
        if (StringUtils.endsWithAny(userId, WAContact.waSuffix, WAContact.waStatusSuffix)) {
            phoneFromUserID = StringUtils.substringBeforeLast(userId, "@");
            if (StringUtils.isNotBlank(phoneFromUserID)) {
                phoneFromUserID = StringUtils.prependIfMissing(phoneFromUserID, PHONE_PREFIX);
            }
        }

        String phoneStr = StringUtils.firstNonBlank(phoneFromUserID, phoneNumber);
        if (StringUtils.isNotBlank(phoneStr)) {
            phoneStr = StringUtils.prependIfMissing(phoneStr, PHONE_PREFIX);
        }

        if (StringUtils.isNotBlank(name)) {
            if (StringUtils.isBlank(phoneStr) || name.equals(WhatsAppParser.UNKNOWN_ACCOUNT)) {
                return name;
            }
            if (StringUtils.equalsAny(name, phoneNumber, phoneFromUserID)) {
                return StringUtils.prependIfMissing(name, PHONE_PREFIX);
            }
            return name + " (" + phoneStr + ")";
        } else if (StringUtils.isNotBlank(phoneStr)) {
            return phoneStr;
        } else {
            return "";
        }
    }
}
