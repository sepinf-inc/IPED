package iped.parsers.skype.v8;

import iped.parsers.skype.SkypeContact;

public class SkypeContactV8 extends SkypeContact {

    String thumbUrl;
    String JSONdata;

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getJSONdata() {
        return JSONdata;
    }

    public void setJSONdata(String jSONdata) {
        JSONdata = jSONdata;
    }

}
