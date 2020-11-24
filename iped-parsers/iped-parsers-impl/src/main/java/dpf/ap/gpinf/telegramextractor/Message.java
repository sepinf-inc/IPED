/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.ap.gpinf.telegramextractor;

import java.util.Date;
import java.util.List;

import dpf.ap.gpinf.interfacetelegram.MessageInterface;
import dpf.ap.gpinf.interfacetelegram.PhotoData;
import dpf.sp.gpinf.indexer.parsers.util.ChildPornHashLookup;

public class Message implements MessageInterface {
    private long id;
    private String mediaHash = null;
    private String mediaFile = null;
    private String mediaName = null;
    private String mediaExt = null;
    private String mediaComment = null;
    boolean fromMe = false;
    private String type = null;
    private Contact from = null;
    private Chat chat = null;
    private String data = null;
    private Date timeStamp = null;
    private String mediaMime = null;
    private boolean link = false;
    private byte[] linkImage = null;
    private byte[] thumb = null;
    private String hashThumb = null;
    private List<PhotoData> names = null;
    private int mediasize = 0;
    private long toId = 0;
    private Double latitude = null;
    private Double longitude = null;
    private boolean childPorn = false;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getMediaHash() {
        return mediaHash;
    }

    public void setMediaHash(String mediaHash) {
        this.mediaHash = mediaHash;
        if (mediaHash != null) {
            setChildPorn(ChildPornHashLookup.lookupHash(mediaHash));
        }
    }

    public String getMediaFile() {
        return mediaFile;
    }

    public void setMediaFile(String mediaFile) {
        this.mediaFile = mediaFile;
    }

    public boolean isFromMe() {
        return fromMe;
    }

    public void setFromMe(boolean fromMe) {
        this.fromMe = fromMe;
    }

    public Message(long id, Chat c) {
        this.id = id;
        setChat(c);
    }

    public Contact getFrom() {
        return from;
    }

    public void setFrom(Contact from) {
        this.from = from;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getMediaMime() {
        return mediaMime;
    }

    public void setMediaMime(String mediaMime) {
        this.mediaMime = mediaMime;
    }

    public boolean isLink() {
        return link;
    }

    public void setLink(boolean link) {
        this.link = link;
    }

    public byte[] getLinkImage() {
        return linkImage;
    }

    public void setLinkImage(byte[] linkImage) {
        this.linkImage = linkImage;
    }

    public byte[] getThumb() {
        return thumb;
    }

    public void setThumb(byte[] thumb) {
        this.thumb = thumb;
    }

    public String getHashThumb() {
        return hashThumb;
    }

    public void setHashThumb(String hashThumb) {
        this.hashThumb = hashThumb;
    }

    public String getMediaName() {
        return mediaName;
    }

    public void setMediaName(String mediaName) {
        this.mediaName = mediaName;
    }

    public void setMediaExtension(String trueExtension) {
        this.mediaExt = trueExtension;
    }

    public String getMediaExtension() {
        return this.mediaExt;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<PhotoData> getNames() {
        return names;
    }

    public void setNames(List<PhotoData> names) {
        this.names = names;
    }

    public int getMediasize() {
        return mediasize;
    }

    public void setMediasize(int mediasize) {
        this.mediasize = mediasize;
    }

    public String getMediaComment() {
        return mediaComment;
    }

    public void setMediaComment(String mediaComment) {
        this.mediaComment = mediaComment;
    }

    public long getToId() {
        return toId;
    }

    public void setToId(long toId) {
        this.toId = toId;
    }

    public boolean isPhoneCall() {
        return MapTypeMSG.PHONE_CALL_STRING.equals(type);
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public boolean isChildPorn() {
        return childPorn;
    }

    public void setChildPorn(boolean childPorn) {
        this.childPorn = childPorn;
    }
}
