package dpf.sp.gpinf.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordImage {
	@JsonProperty("url")
	    public String url;
	
	@JsonProperty("proxy_url")
	    public String proxy_url;
	
	@JsonProperty("width")
	    public int width;
	
	@JsonProperty("height")
	public int height;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getProxy_url() {
		return proxy_url;
	}

	public void setProxy_url(String proxy_url) {
		this.proxy_url = proxy_url;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

}
