package iped.parsers.skype.v8;

import iped.parsers.skype.SkypeConversation;

public class SkypeConversationV14 extends SkypeConversation {
    String json;

    public String getJSONdata() {
        return json;
    }

    public void setJSONdata(String json) {
        this.json = json;
    }
}
