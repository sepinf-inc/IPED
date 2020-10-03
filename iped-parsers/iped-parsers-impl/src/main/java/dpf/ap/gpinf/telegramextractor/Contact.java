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

import java.util.List;

import dpf.ap.gpinf.interfacetelegram.ContactInterface;
import dpf.ap.gpinf.interfacetelegram.DecoderTelegramInterface;
import dpf.ap.gpinf.interfacetelegram.PhotoData;

public class Contact implements ContactInterface {
    private long id;
    private int groupid;
    private String name = null;
    private String lastName = null;
    private String username = null;
    private String phone = null;
    private byte[] avatar = null;
    private List<PhotoData> photos = null;

    public Contact(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    private static DecoderTelegramInterface d = null;

    public static Contact getContactFromBytes(byte[] bytes)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (d == null) {
            Object o = Class.forName(Extractor.DECODER_CLASS).newInstance();
            d = (DecoderTelegramInterface) o;
        }
        d.setDecoderData(bytes, d.USER);
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
        // TODO Auto-generated method stub

    }

    @Override
    public void setSmallName(String smallname) {
        // TODO Auto-generated method stub

    }

    public List<PhotoData> getPhotos() {
        return photos;
    }

    public void setPhotos(List<PhotoData> photos) {
        this.photos = photos;
    }

    public int getGroupid() {
        return groupid;
    }

    public void setGroupid(int groupid) {
        this.groupid = groupid;
    }

    @Override
    public String toString() {
        String number = getPhone();
        String name = getFullname();
        if (name == null)
            name = "";
        if (number != null && number.length() > 0) {
            name += " (phone: " + number + ")";
        } else if (getId() > 0) {
            name += " (ID:" + getId() + " )";
        }
        if (name == null || name.trim().isEmpty())
            name = "unknown";
        return name;
    }

}
