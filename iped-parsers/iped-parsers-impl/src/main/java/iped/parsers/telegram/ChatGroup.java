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

import java.util.HashSet;
import java.util.Set;

public class ChatGroup extends Chat {

    private final Set<Long> members = new HashSet<Long>();
    private final Set<Long> admins = new HashSet<Long>();
    private int participantsCount;

    public ChatGroup(long id, Contact c, String name) {
        super(id, c, name);
        setGroup(true);
    }

    public Set<Long> getMembers() {
        return members;
    }

    public void addMember(long id) {
        members.add(id);
    }

    public Set<Long> getAdmins() {
        return admins;
    }

    public void addAdmin(long id) {
        admins.add(id);
    }

    public int getParticipantsCount() {
        return participantsCount;
    }

    public void setParticipantsCount(int participantsCount) {
        this.participantsCount = participantsCount;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getName() != null) {
            sb.append(getName());
        } else if (getC() != null && getC().getName() != null) {
            sb.append(getC().getName());
        }
        if (sb.length() > 0) {
            sb.append(" (ID: ").append(getId()).append(")");
        } else {
            sb.append("ID: ").append(getId());
        }
        return sb.toString();
    }
}
