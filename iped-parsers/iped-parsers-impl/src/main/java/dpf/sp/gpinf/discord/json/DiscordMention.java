package dpf.sp.gpinf.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordMention {

    @JsonProperty("id")
    private String id;

    @JsonProperty("username")
    private String username;

    @JsonProperty("avatar")
    private String avatar;

    @JsonProperty("discriminator")
    private String discriminator;

    @JsonProperty("public_flags")
    private int public_flags;

    @JsonProperty("bot")
    private boolean bot;

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

    @Override
    public String toString() {
        return "DiscordMention [id=" + id + ", username=" + username + ", avatar=" + avatar + ", discriminator="
                + discriminator + ", public_flags=" + public_flags + ", bot=" + bot + "]";
    }

}
