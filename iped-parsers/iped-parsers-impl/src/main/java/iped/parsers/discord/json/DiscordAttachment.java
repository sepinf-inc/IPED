package iped.parsers.discord.json;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

import iped.parsers.util.ChildPornHashLookup;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordAttachment {
    @JsonProperty("id")
    private String id;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("size")
    private int size;

    @JsonProperty("url")
    private String url;

    @JsonProperty("proxy_url")
    private String proxy_url;

    @JsonProperty("width")
    private int width;

    @JsonProperty("height")
    private int height;

    @JsonProperty("ephemeral")
    private boolean ephemeral;

    @JsonProperty("content_type")
    private String content_type;

    private String mediaHash;

    private Set<String> childPornSets = new HashSet<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProxy_url() {
        return proxy_url;
    }

    public void setProxy_url(String proxy_url) {
        this.proxy_url = proxy_url;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public String getContent_type() {
        return content_type;
    }

    public void setContent_type(String content_type) {
        this.content_type = content_type;
    }

    public String getMediaHash() {
        return mediaHash;
    }

    public void setMediaHash(String mediaHash) {
        this.mediaHash = mediaHash;
        childPornSets.addAll(ChildPornHashLookup.lookupHash(mediaHash));
    }

    public Set<String> getChildPornSets() {
        return childPornSets;
    }

    public void setChildPornSets(Set<String> childPornSets) {
        this.childPornSets = childPornSets;
    }

    @Override
    public String toString() {
        return "DiscordAttachment [id=" + id + ", filename=" + filename + ", size=" + size + ", url=" + url + ", proxy_url=" + proxy_url + ", width=" + width + ", height=" + height + ", ephemeral=" + ephemeral + ", content_type="
                + content_type + ", mediaHash=" + mediaHash + ", childPornSets=" + childPornSets + "]";
    }

}
