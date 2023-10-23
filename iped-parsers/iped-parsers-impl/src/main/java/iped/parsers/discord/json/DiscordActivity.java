package iped.parsers.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordActivity {

    @JsonProperty("type")
    private String type;

    @JsonProperty("party_id")
    private String party_id;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getParty_id() {
        return party_id;
    }

    public void setParty_id(String party_id) {
        this.party_id = party_id;
    }

    @Override
    public String toString() {
        return "DiscordActivity [type=" + type + ", party_id=" + party_id + "]";
    }

}
