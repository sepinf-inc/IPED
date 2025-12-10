package iped.parsers.whatsapp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WAContactsDirectory {
    private Map<String, WAContact> contacts = new ConcurrentHashMap<>();

    public WAContact getContact(String id) {
        String nameId = Util.getNameFromId(id);
        WAContact contact = contacts.get(nameId);
        if (contact == null) {
            contact = new WAContact(id);
            contacts.put(nameId, contact);
        }
        return contact;
    }

    public boolean addContactMapping(String lid, String jid) {
        String nameJid = Util.getNameFromId(jid);
        WAContact contact = contacts.get(nameJid);
        String nameLid = Util.getNameFromId(lid);
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
