package iped.parsers.discord.json;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordRoot implements Comparable<DiscordRoot> {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private int type;

    @JsonProperty("content")
    private String messageContent;

    @JsonProperty("channel_id")
    private String channel_id;

    @JsonProperty("author")
    private DiscordAuthor author;

    @JsonProperty("referenced_message")
    private DiscordReferencedMessage referencedMessage;

    @JsonProperty("message_reference")
    private DiscordMessageReference messageReference;

    @JsonProperty("activity")
    private DiscordActivity activity;

    @JsonProperty("attachments")
    private List<DiscordAttachment> attachments;

    @JsonProperty("embeds")
    private List<DiscordEmbed> embeds;

    @JsonProperty("mentions")
    private List<DiscordMention> mentions;

    @JsonProperty("mention_roles")
    private List<Object> mention_roles;

    @JsonProperty("mention_everyone")
    private boolean mention_everyone;

    @JsonProperty("pinned")
    private boolean pinned;

    @JsonProperty("tts")
    private boolean tts;

    @JsonProperty("timestamp")
    private Date timestamp;

    @JsonProperty("edited_timestamp")
    private Date editedTimestamp;

    @JsonProperty("flags")
    private int flags;

    @JsonProperty("reactions")
    private List<DiscordReaction> reactions;

    @JsonProperty("call")
    private DiscordCall call;

    @JsonProperty("webhook_id")
    private String webhookId;

    @JsonProperty("components")
    private List<Object> components;

    public List<Object> getComponents() {
        return components;
    }

    public void setComponents(List<Object> components) {
        this.components = components;
    }

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

    public DiscordReferencedMessage getReferencedMessage() {
        return referencedMessage;
    }

    public void setReferencedMessage(DiscordReferencedMessage referencedMessage) {
        this.referencedMessage = referencedMessage;
    }

    public DiscordMessageReference getMessageReference() {
        return messageReference;
    }

    public void setMessageReference(DiscordMessageReference messageReference) {
        this.messageReference = messageReference;
    }

    public DiscordActivity getActivity() {
        return activity;
    }

    public void setActivity(DiscordActivity activity) {
        this.activity = activity;
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
        return "DiscordRoot [id=" + id + ", type=" + type + ", messageContent=" + messageContent + ", channel_id=" + channel_id + ", author=" + author + ", referencedMessage=" + referencedMessage + ", messageReference=" + messageReference
                + ", activity=" + activity + ", attachments=" + attachments + ", embeds=" + embeds + ", mentions=" + mentions + ", mention_roles=" + mention_roles + ", mention_everyone=" + mention_everyone + ", pinned=" + pinned + ", tts="
                + tts + ", timestamp=" + timestamp + ", editedTimestamp=" + editedTimestamp + ", flags=" + flags + ", reactions=" + reactions + ", call=" + call + ", webhookId=" + webhookId + ", components=" + components + "]";
    }

    @Override
    public int compareTo(DiscordRoot o) {
        if (this.timestamp == null) {
            if (o.timestamp == null) {
                return 0;
            } else {
                return -1;
            }
        } else if (o.timestamp == null) {
            return 1;
        }
        return this.timestamp.compareTo(o.timestamp);
    }

}
