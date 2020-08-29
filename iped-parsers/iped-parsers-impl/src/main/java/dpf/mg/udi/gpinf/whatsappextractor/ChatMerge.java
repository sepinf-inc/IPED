package dpf.mg.udi.gpinf.whatsappextractor;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatMerge {

    List<Chat> main;
    String dbname;

    public ChatMerge(List<Chat> main, String dbname) {
        this.main = main;
        this.dbname = dbname;
    }

    public void mergeChatList(List<Chat> backup) {

        Comparator<Chat> cmp = new Comparator<Chat>() {
            @Override
            public int compare(Chat u1, Chat u2) {
                if (u1.getId() == u2.getId())
                    return 0;
                else if (u1.getId() < u2.getId())
                    return -1;
                else
                    return 1;
            }
        };
        Collections.sort(main, cmp);
        Collections.sort(backup, cmp);
        int indexmain = 0;
        for (Chat c : backup) {
            indexmain = Collections.binarySearch(main, c, cmp);
            if (indexmain == -1) {// chat was removed
                main.add(c);
            } else {
                mergeMessageList(main.get(indexmain).getMessages(), c.getMessages());
            }

        }

    }

    public void mergeMessageList(List<Message> main, List<Message> backup) {

        Comparator<Message> cmp = new Comparator<Message>() {
            @Override
            public int compare(Message u1, Message u2) {
                if (u1.getId() == u2.getId())
                    return 0;
                else if (u1.getId() < u2.getId())
                    return -1;
                else
                    return 1;
            }
        };
        Collections.sort(main, cmp);
        Collections.sort(backup, cmp);
        int indexmain = 0;
        for (Message m : backup) {
            indexmain = Collections.binarySearch(main, m, cmp);
            if (indexmain == -1) {// message was removed
                main.add(m);
            }

        }

    }

}
