package dpf.ap.gpinf.telegramextractor;

import java.util.HashMap;

public class ChatGroup extends Chat {
    public class dadosMembros {

    }

    HashMap<String, dadosMembros> members;

    public ChatGroup(long id, Contact c, String name) {
        super(id, c, name);
        this.setGroup(true);
        members = new HashMap<>();

    }
}
