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

    public int mergeChatList(List<Chat> backup) {
        int tot_rec = 0;
        Comparator<Chat> cmp = new Comparator<Chat>() {
            @Override
            public int compare(Chat u1, Chat u2) {
                return u1.getRemote().getId().compareTo(u2.getRemote().getId());
            }
        };
        // Collections.sort(main, cmp);
        // Collections.sort(backup, cmp);
        int indexmain = 0;
        System.out.println("chats main " + main.size());
        for (Chat c : backup) {
            indexmain = findChat(main, c);
            if (indexmain == -1) {// chat was removed
                main.add(c);
                tot_rec += c.getMessages().size();
            } else {
                tot_rec += mergeMessageList(main.get(indexmain).getMessages(), c.getMessages());
            }


        }
        return tot_rec;

    }

    public int mergeMessageList(List<Message> main, List<Message> backup) {
        int tot_rec = 0;
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
            if (indexmain < 0) {// message was removed
                main.add(m);
                tot_rec++;
                m.setRecoveredFrom(dbname);
            } 

        }
        return tot_rec;

    }

    
    private int findChat(List<Chat> l, Chat key) {
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).getRemote().getId().equals(key.getRemote().getId())) {
                return i;
            }
        }
        return -1;
    }

}
