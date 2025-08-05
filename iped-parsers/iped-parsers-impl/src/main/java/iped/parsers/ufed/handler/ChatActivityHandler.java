package iped.parsers.ufed.handler;

import iped.parsers.ufed.model.ChatActivity;

public class ChatActivityHandler extends BaseModelHandler<ChatActivity> {

    public ChatActivityHandler(ChatActivity model) {
        super(model);
    }

    @Override
    public String getTitle() {

        String actionStr = model.getAction() != null ? "-" + model.getAction() : "";

        return new StringBuilder()
                .append(model.getModelType())
                .append(actionStr)
                .append("-[")
                .append(model.getId())
                .append("]")
                .toString();
    }
}
