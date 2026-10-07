package iped.parsers.whatsapp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import iped.parsers.util.ChatUtil;
import iped.parsers.util.Messages;

public class WAContactsDirectory {
    private final Map<String, WAContact> contacts = new ConcurrentHashMap<>();

    public WAContactsDirectory() {
        // Add special contact "0", used as Official WhatsApp Account
        WAContact c = getContact("0@s.whatsapp.net");
        c.setDisplayName(Messages.getString("WhatsAppReport.OfficialAccount"));
        byte[] bytes = ChatUtil.readResourceAsBytes("img/whatsapp-official.png");
        c.setAvatar(bytes);
    }

    public WAContact getContact(String id) {
        String nameId = ChatUtil.getNameFromId(id);
        WAContact contact = contacts.get(nameId);
        if (contact == null) {
            contact = new WAContact(id);
            contacts.put(nameId, contact);
        }
        return contact;
    }

    public boolean addContactMapping(String lid, String jid) {
        String nameJid = ChatUtil.getNameFromId(jid);
        WAContact contact = contacts.get(nameJid);
        String nameLid = ChatUtil.getNameFromId(lid);
        if (contact != null) {
            contacts.put(nameLid, contact);
            return true;
        }
        contact = new WAContact(nameJid);
        contacts.put(nameJid, contact);
        contacts.put(nameLid, contact);
        return false;
    }

    public Iterable<WAContact> contacts() {
        return contacts.values();
    }

    public void putAll(WAContactsDirectory directory) {
        this.contacts.putAll(directory.contacts);
    }
    
    public boolean hasContact(String id) {
        return contacts.containsKey(id);
    }
}
