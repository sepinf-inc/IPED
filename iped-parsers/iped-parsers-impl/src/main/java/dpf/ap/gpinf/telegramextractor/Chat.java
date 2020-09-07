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

import java.util.ArrayList;

import dpf.ap.gpinf.interfacetelegram.ChatInterface;
import dpf.ap.gpinf.interfacetelegram.ContactInterface;

public class Chat implements ChatInterface {
    private ArrayList<Message> messages = new ArrayList<>();
    private Contact c;
    private String name;
    private boolean group;
    private long id;

    public ArrayList<Message> getMessages() {
        return messages;
    }

    public void setMessages(ArrayList<Message> messages) {
        this.messages = messages;
    }

    public Contact getC() {
        return c;
    }

    public void setC(Contact c) {
        this.c = c;
    }

    public void setC(ContactInterface c) {
        this.c = (Contact) c;
    }

    public String getName() {
        if (name != null && !name.trim().isEmpty())
            return name;
        else if (c != null)
            return c.toString();
        else
            return null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(boolean isgroup) {
        this.group = isgroup;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Chat(long id, Contact c, String name) {
        this.id = id;
        this.c = c;
        this.name = name;
        this.group = false;
    }

}
