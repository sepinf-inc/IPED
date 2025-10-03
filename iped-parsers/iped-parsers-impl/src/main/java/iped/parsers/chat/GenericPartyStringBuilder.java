package iped.parsers.chat;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class GenericPartyStringBuilder extends PartyStringBuilder {

    private static final String FIELDS_SEPARATOR = " | ";

    @Override
    public String build() {

        String usernameStr = null;
        String phoneNumberStr = null;
        String userIdStr = null;

        if (StringUtils.isNotBlank(username) && !StringUtils.equals(username, name)) {
            usernameStr = "@" + username;
        }
        if (StringUtils.isNotBlank(phoneNumber) && !StringUtils.equalsAny(phoneNumber, username, name)) {
            phoneNumberStr = "📞:" + phoneNumber;
        }
        if (StringUtils.isNotBlank(userId) && !StringUtils.equalsAny(userId, phoneNumber, username, name)) {
            userIdStr = "ID:" + userId;
        }

        String fieldsStr = Arrays.asList(usernameStr, phoneNumberStr, userIdStr).stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(FIELDS_SEPARATOR));

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
