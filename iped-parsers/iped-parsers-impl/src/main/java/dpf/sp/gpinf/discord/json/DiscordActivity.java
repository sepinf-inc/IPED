package dpf.sp.gpinf.discord.json;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordActivity {

	@Override
	public String toString() {
		return "DiscordActivity [type=" + type + ", party_id=" + party_id + "]";
	}

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

	@JsonProperty("type")
	private String type;

	@JsonProperty("party_id")
	private String party_id;

	
}
