package dpf.sp.gpinf.discord.json;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordEmbed {
    @JsonProperty("type")
    public String type;

    @JsonProperty("description")
    public String description;

    @JsonProperty("fields")
    public List<DiscordField> fields;

    @JsonProperty("author")
    public DiscordAuthor author;

    @JsonProperty("thumbnail")
    public DiscordThumbnail thumbnail;

    @JsonProperty("footer")
    public DiscordFooter footer;

    @JsonProperty("url")
    public String url;

    @JsonProperty("title")
    public String title;

    @JsonProperty("color")
    public int color;

    @JsonProperty("provider")
    public DiscordProvider provider;

    @JsonProperty("video")
    public DiscordVideo video;

    @JsonProperty("image")
    public DiscordImage image;

    @JsonProperty("timestamp")
    private Date timestamp;

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public DiscordImage getImage() {
        return image;
    }

    public void setImage(DiscordImage image) {
        this.image = image;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<DiscordField> getFields() {
        return fields;
    }

    public void setFields(List<DiscordField> fields) {
        this.fields = fields;
    }

    public DiscordAuthor getAuthor() {
        return author;
    }

    public void setAuthor(DiscordAuthor author) {
        this.author = author;
    }

    public DiscordThumbnail getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(DiscordThumbnail thumbnail) {
        this.thumbnail = thumbnail;
    }

    public DiscordFooter getFooter() {
        return footer;
    }

    public void setFooter(DiscordFooter footer) {
        this.footer = footer;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public DiscordProvider getProvider() {
        return provider;
    }

    public void setProvider(DiscordProvider provider) {
        this.provider = provider;
    }

    public DiscordVideo getVideo() {
        return video;
    }

    public void setVideo(DiscordVideo video) {
        this.video = video;
    }
}
