package dpf.sp.gpinf.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordEmoji {

    @JsonProperty("animated")
    public boolean animated;

    @JsonProperty("id")
    public long id;

    @JsonProperty("name")
    public String name;

    public boolean isAnimated() {
        return animated;
    }

    public void setAnimated(boolean animated) {
        this.animated = animated;
    }

    public Object getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "DiscordEmoji [animated=" + animated + ", id=" + id + ", name=" + name + "]";
    }

}
