package iped.parsers.discord.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DiscordSticker {
    @JsonProperty("id")
    private String id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("format_type")
    private String formatType;
    String hash = null;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormatType() {
        return formatType;
    }

    public void setFormatType(String formatType) {
        this.formatType = formatType;
    }

    public void setMediaHash(String hash) {
        this.hash = hash;
    }

    public String getMediaHash() {
        return hash;
    }

}
