package iped.parsers.chat;

import org.apache.commons.lang3.StringUtils;

public class WhatsAppPartyStringBuilder extends PartyStringBuilder {

    private static final String PHONE_PREFIX = "+";

    @Override
    public String build() {

        String phoneFromUserID = StringUtils.substringBefore(userId, '@');
        if (StringUtils.isNotBlank(phoneFromUserID)) {
            phoneFromUserID = StringUtils.prependIfMissing(phoneFromUserID, PHONE_PREFIX);
        }
        String phoneStr = StringUtils.firstNonBlank(phoneFromUserID, phoneNumber);

        if (StringUtils.isNotBlank(name)) {
            if (StringUtils.isBlank(phoneStr) || StringUtils.equalsAny(name, phoneNumber, phoneFromUserID)) {
                return name;
            }
            return name + " (" + phoneStr + ")";
        } else if (StringUtils.isNotBlank(phoneStr)) {
            return phoneStr;
        } else {
            return "";
        }
    }
}
