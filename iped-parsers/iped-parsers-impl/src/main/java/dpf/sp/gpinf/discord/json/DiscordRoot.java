package dpf.sp.gpinf.discord.json;

import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordRoot {

    @JsonProperty("id")
    public String id;

    @JsonProperty("type")
    public int type;

    @JsonProperty("content")
    public String messageContent;

    @JsonProperty("channel_id")
    public String channel_id;

    @JsonProperty("author")
    public DiscordAuthor author;

    @JsonProperty("attachments")
    public List<DiscordAttachment> attachments;

    @JsonProperty("embeds")
    public List<DiscordEmbed> embeds;

    @JsonProperty("mentions")
    public List<DiscordMention> mentions;

    @JsonProperty("mention_roles")
    public List<Object> mention_roles;

    @JsonProperty("mention_everyone")
    public boolean mention_everyone;

    @JsonProperty("pinned")
    public boolean pinned;

    @JsonProperty("tts")
    public boolean tts;

    @JsonProperty("timestamp")
    public Date timestamp;

    @JsonProperty("edited_timestamp")
    public Date editedTimestamp;

    @JsonProperty("flags")
    public int flags;

    @JsonProperty("reactions")
    public List<DiscordReaction> reactions;

    @JsonProperty("call")
    public DiscordCall call;

    @JsonProperty("webhook_id")
    public String webhookId;

    public String getWebhookId() {
        return webhookId;
    }

    public void setWebhookId(String webhookId) {
        this.webhookId = webhookId;
    }

    public DiscordCall getCall() {
        return call;
    }

    public void setCall(DiscordCall call) {
        this.call = call;
    }

    public List<DiscordReaction> getReactions() {
        return reactions;
    }

    public void setReactions(List<DiscordReaction> reactions) {
        this.reactions = reactions;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public String getChannel_id() {
        return channel_id;
    }

    public void setChannel_id(String channel_id) {
        this.channel_id = channel_id;
    }

    public DiscordAuthor getAuthor() {
        return author;
    }

    public void setAuthor(DiscordAuthor author) {
        this.author = author;
    }

    public List<DiscordAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<DiscordAttachment> attachments) {
        this.attachments = attachments;
    }

    public List<DiscordEmbed> getEmbeds() {
        return embeds;
    }

    public void setEmbeds(List<DiscordEmbed> embeds) {
        this.embeds = embeds;
    }

    public List<DiscordMention> getMentions() {
        return mentions;
    }

    public void setMentions(List<DiscordMention> mentions) {
        this.mentions = mentions;
    }

    public List<Object> getMention_roles() {
        return mention_roles;
    }

    public void setMention_roles(List<Object> mention_roles) {
        this.mention_roles = mention_roles;
    }

    public boolean isMention_everyone() {
        return mention_everyone;
    }

    public void setMention_everyone(boolean mention_everyone) {
        this.mention_everyone = mention_everyone;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isTts() {
        return tts;
    }

    public void setTts(boolean tts) {
        this.tts = tts;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Date getEditedTimestamp() {
        return editedTimestamp;
    }

    public void setEditedTimestamp(Date editedTimestamp) {
        this.editedTimestamp = editedTimestamp;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    @Override
    public String toString() {
        return "DiscordRoot [" + "id=" + id + ", type=" + type + ", messageContent=" + messageContent + ", channel_id="
                + channel_id + ", author=" + author.toString() + ", attachments=" + attachments.toString() + ", embeds="
                + embeds.toString() + ", mentions=" + mentions.toString() + ", mention_roles="
                + mention_roles.toString() + ", pinned=" + pinned + ", mention_everyone=" + mention_everyone + ", tts="
                + tts + ", timestamp=" + timestamp + ", editedTimestamp=" + editedTimestamp + ", flags=" + flags
                + ", reactions=" + reactions.toString() + ", call=" + call.toString() + "]";
    }

}
