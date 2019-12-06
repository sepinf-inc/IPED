package dpf.mt.gpinf.skype.parser.v8;

import dpf.mt.gpinf.skype.parser.SkypeConversation;

public class SkypeConversationV14 extends SkypeConversation{
	String json;

	public String getJSONdata() {
		return json;		
	}

	public void setJSONdata(String json) {
		this.json = json;
	}
}
