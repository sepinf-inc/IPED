package dpf.sp.gpinf.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordReaction {

    @JsonProperty("emoji")
    public DiscordEmoji emoji;

    @JsonProperty("count")
    public int count;

    @JsonProperty("me")
    public boolean me;

    public DiscordEmoji getEmoji() {
        return emoji;
    }

    public void setEmoji(DiscordEmoji emoji) {
        this.emoji = emoji;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isMe() {
        return me;
    }

    public void setMe(boolean me) {
        this.me = me;
    }

    @Override
    public String toString() {
        return "DiscordReaction [emoji=" + emoji + ", count=" + count + ", me=" + me + "]";
    }

}
