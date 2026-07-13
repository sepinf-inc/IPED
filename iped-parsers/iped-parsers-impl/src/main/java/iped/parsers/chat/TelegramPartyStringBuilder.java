package iped.parsers.chat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class TelegramPartyStringBuilder extends PartyStringBuilder {

    private static final String FIELDS_SEPARATOR = " | ";
    private static final String PHONE_PREFIX = "+";

    @Override
    public String build() {

        String usernameStr = null;
        String phoneNumberStr = null;

        if (StringUtils.isNotBlank(username) && !StringUtils.equals(username, name)) {
            usernameStr = "@" + username;
        }
        if (StringUtils.isNotBlank(phoneNumber) && !StringUtils.equalsAny(phoneNumber, username, name)) {
            phoneNumberStr = StringUtils.prependIfMissing(phoneNumber, PHONE_PREFIX);
        }

        String fieldsStr = Arrays.asList(usernameStr, phoneNumberStr).stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(FIELDS_SEPARATOR));

        if (StringUtils.isNotBlank(userId) && StringUtils.isBlank(fieldsStr)) {
            fieldsStr = "ID:" + userId;
        }

        if (StringUtils.isNoneBlank(name, fieldsStr)) {
            return name + " (" + fieldsStr + ")";

        } else if (StringUtils.isNotBlank(name)) {
            return name;

        } else if (StringUtils.isNotBlank(fieldsStr)) {
            return "[" + fieldsStr + "]";
        } else {
            return "";
        }
    }
}
