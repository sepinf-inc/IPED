package iped.parsers.chat;

import org.apache.commons.lang3.StringUtils;

public class EmailPartyStringBuilder extends PartyStringBuilder {

    @Override
    public String build() {
        if (StringUtils.isNoneBlank(name, userId) && !name.equals(userId)) {
            return name + " <" + userId + ">";
        }
        if (StringUtils.isNotBlank(userId)) {
            return "<" + userId + ">";
        }
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        return "";
    }
}
