package iped.parsers.skype.v8;

import iped.parsers.skype.SkypeMessage;;

public class SkypeMessageV12 extends SkypeMessage {
    String JSONdata;

    public String getJSONdata() {
        return JSONdata;
    }

    public void setJSONdata(String json) {
        this.JSONdata = json;
    }

}
