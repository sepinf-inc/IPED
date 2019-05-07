package dpf.mg.udi.gpinf.whatsappextractor;

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

    public Iterable<WAContact> contacts() {
        return contacts.values();
    }

    public void putAll(WAContactsDirectory directory) {
        this.contacts.putAll(directory.contacts);
    }
}
