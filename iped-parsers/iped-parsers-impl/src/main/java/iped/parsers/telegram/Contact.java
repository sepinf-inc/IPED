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
package iped.parsers.telegram;

import java.util.List;

import dpf.ap.gpinf.interfacetelegram.ContactInterface;
import dpf.ap.gpinf.interfacetelegram.DecoderTelegramInterface;
import dpf.ap.gpinf.interfacetelegram.PhotoData;

public class Contact implements ContactInterface {
    private long id;
    private int groupId;
    private String name = null;
    private String lastName = null;
    private String username = null;
    private String phone = null;
    private byte[] avatar = null;
    private List<PhotoData> photos = null;
    private boolean isGroup;

    public Contact(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return isGroup ? "Group - " + name : name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String telefone) {
        this.phone = telefone;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public byte[] getAvatar() {
        return avatar;
    }

    public void setAvatar(byte[] avatar) {
        this.avatar = avatar;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullname() {
        String fn = "";
        if (name != null) {
            fn += name;
        }
        if (lastName != null) {
            if (!fn.equals("")) {
                fn += " ";
            }
            fn += lastName;
        }
        if (fn.equals("") && username != null) {
            fn = username;
        }

        return fn;
    }

    public static Contact getContactFromBytes(byte[] bytes, DecoderTelegramInterface d) {

        d.setDecoderData(bytes, DecoderTelegramInterface.USER);
        Contact c = new Contact(0);
        d.getUserData(c);
        c.setPhotos(d.getPhotoData());
        return c;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public void setBigName(String bigname) {
    }

    @Override
    public void setSmallName(String smallname) {
    }

    public List<PhotoData> getPhotos() {
        return photos;
    }

    public void setPhotos(List<PhotoData> photos) {
        this.photos = photos;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public void setGroup(boolean isGroup) {
        this.isGroup = isGroup;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String name = getFullname();
        if (name != null) {
            sb.append(name.trim());
        }
        String number = getPhone();
        if (number != null && number.length() > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("(phone: ").append(number).append(')');
        } else if (getId() > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append("(ID:").append(getId()).append(')');
        }
        if (sb.length() == 0) {
            sb.append("[unknown]");
        }
        return sb.toString();
    }

}
