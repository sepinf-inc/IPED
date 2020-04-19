package dpf.mt.gpinf.skype.parser.v8;

import dpf.mt.gpinf.skype.parser.SkypeMessage;;

public class SkypeMessageV12 extends SkypeMessage {
	String JSONdata;

	public String getJSONdata() {
		return JSONdata;		
	}

	public void setJSONdata(String json) {
		this.JSONdata = json;
	}

}
