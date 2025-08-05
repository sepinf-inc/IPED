package iped.parsers.chat;

import org.apache.commons.lang3.StringUtils;

public class InstagramPartyStringBuilder extends PartyStringBuilder {

    @Override
    public String build() {
        if (StringUtils.isNoneBlank(name, userId)) {
            return name + " (@" + userId + ")";
        }
        if (StringUtils.isNotBlank(userId)) {
            return "@" + userId;
        }
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        return "";
    }
}
