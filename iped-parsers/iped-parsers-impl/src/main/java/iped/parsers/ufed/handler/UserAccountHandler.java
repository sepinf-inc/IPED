package iped.parsers.ufed.handler;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import iped.data.IItemReader;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.UserAccount;

public class UserAccountHandler extends AccountableHandler<UserAccount> {

    public UserAccountHandler(UserAccount model, IItemReader item) {
        super(model, item);
    }

    @Override
    public String getTitle() {

        String name = model.getName();
        String userID = model.getUserID()
                .flatMap(list -> list.stream().findFirst())
                .map(ContactEntry::getValue)
                .orElse(null);
        String phoneNumber = model.getPhoneNumber()
                .flatMap(list -> list.stream().findFirst())
                .map(ContactEntry::getValue)
                .orElse(null);

        String data = Arrays.asList(name, userID, phoneNumber).stream()
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(" | "));

        String sourceStr = model.getSource() != null ? "-[" + model.getSource() + "]" : "";

        return new StringBuilder()
                .append(model.getModelType())
                .append(sourceStr)
                .append("-[")
                .append(StringUtils.firstNonBlank(data, model.getId()))
                .append("]")
                .toString();
    }
}
