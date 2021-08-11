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
public class DiscordCall {

    @JsonProperty("participants")
    private List<String> participants;

    @JsonProperty("ended_timestamp")
    private Date endedTimestamp;

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> participants) {
        this.participants = participants;
    }

    public String getParticipantsNames(List<DiscordRoot> drl) {

        List<String> participantsNames = new ArrayList<String>();

        for (DiscordRoot dr : drl) {
            for (String name : participants) {
                if (name.equals(dr.getAuthor().getId())) {
                    participantsNames.add(dr.getAuthor().getFullUsername());
                }
            }
        }

        return String.join(", ", participantsNames);
    }

    public Date getEndedTimestamp() {
        return endedTimestamp;
    }

    public void setEnded_timestamp(Date endedTimestamp) {
        this.endedTimestamp = endedTimestamp;
    }

    @Override
    public String toString() {
        return "DiscordCall [participants=" + participants + ", endedTimestamp=" + endedTimestamp + "]";
    }

}
