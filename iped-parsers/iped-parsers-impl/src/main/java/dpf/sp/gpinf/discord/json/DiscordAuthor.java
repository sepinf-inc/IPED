package dpf.sp.gpinf.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordAuthor {
	@JsonProperty("id")
	public String id;

	@JsonProperty("username")
	public String username;

	@JsonProperty("avatar")
	public String avatar;

	@JsonProperty("discriminator")
	public String discriminator;

	@JsonProperty("public_flags")
	public int public_flags;

	@JsonProperty("bot")
	public boolean bot;

	@JsonProperty("name")
	public String name;

	@JsonProperty("url")
	public String url;

	@JsonProperty("icon_url")
	public String icon_url;

	@JsonProperty("proxy_icon_url")
	public String proxy_icon_url;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public String getFullUsername() {
		return username + "#" + discriminator;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}

	public String getAvatar() {
		return avatar;
	}

	public void setAvatar(String avatar) {
		this.avatar = avatar;
	}

	public String getDiscriminator() {
		return discriminator;
	}

	public void setDiscriminator(String discriminator) {
		this.discriminator = discriminator;
	}

	public int getPublic_flags() {
		return public_flags;
	}

	public void setPublic_flags(int public_flags) {
		this.public_flags = public_flags;
	}

	public boolean isBot() {
		return bot;
	}

	public void setBot(boolean bot) {
		this.bot = bot;
	}

	public String getName() {
		return name == null ? "" : name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getIcon_url() {
		return icon_url;
	}

	public void setIcon_url(String icon_url) {
		this.icon_url = icon_url;
	}

	public String getProxy_icon_url() {
		return proxy_icon_url;
	}

	public void setProxy_icon_url(String proxy_icon_url) {
		this.proxy_icon_url = proxy_icon_url;
	}

	@Override
	public String toString() {
		return "DiscordAuthor [id=" + id + ", username=" + username + ", avatar=" + avatar + ", discriminator="
				+ discriminator + ", public_flags=" + public_flags + ", bot=" + bot + ", name=" + name + ", url=" + url
				+ ", icon_url=" + icon_url + ", proxy_icon_url=" + proxy_icon_url + "]";
	}

}
