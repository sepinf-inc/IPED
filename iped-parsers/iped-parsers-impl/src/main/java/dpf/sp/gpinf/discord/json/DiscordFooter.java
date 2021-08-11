package dpf.sp.gpinf.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordFooter {

    @JsonProperty("text")
    public String text;

    @JsonProperty("proxy_icon_url")
    public String proxyIconURL;

    @JsonProperty("icon_url")
    public String iconURL;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getProxyIconURL() {
        return proxyIconURL;
    }

    public void setProxyIconURL(String proxyIconURL) {
        this.proxyIconURL = proxyIconURL;
    }

    public String getIconURL() {
        return iconURL;
    }

    public void setIconURL(String iconURL) {
        this.iconURL = iconURL;
    }

}
