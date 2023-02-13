package iped.parsers.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DiscordMessageReference {

    @JsonProperty("channel_id")
    private String channel_id;

    @JsonProperty("guild_id")
    private String guild_id;

    @JsonProperty("message_id")
    private String message_id;

    public String getChannel_id() {
        return channel_id;
    }

    public void setChannel_id(String channel_id) {
        this.channel_id = channel_id;
    }

    public String getGuild_id() {
        return guild_id;
    }

    public void setGuild_id(String guild_id) {
        this.guild_id = guild_id;
    }

    public String getMessage_id() {
        return message_id;
    }

    public void setMessage_id(String message_id) {
        this.message_id = message_id;
    }

    @Override
    public String toString() {
        return "DiscordMessageReference [channel_id=" + channel_id + ", guild_id=" + guild_id + ", message_id=" + message_id + "]";
    }

}
