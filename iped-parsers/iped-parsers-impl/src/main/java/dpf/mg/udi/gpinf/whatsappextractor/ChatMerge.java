package dpf.mg.udi.gpinf.whatsappextractor;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatMerge {

    List<Chat> main;
    String dbname;

    Comparator<Message> cmpMessage = new Comparator<Message>() {
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

    public ChatMerge(List<Chat> main, String dbname) {
        this.main = main;
        this.dbname = dbname;
    }

    public boolean isBackup(List<Chat> backup) {
        int indexmain = 0;
        int totchats = 0;
        for (Chat c : backup) {
            indexmain = findChat(main, c);
            if (indexmain >= 0) {// verify if the chats are compatible
                if (!c.getRemote().getId().equals(main.get(indexmain).getRemote().getId())) {
                    // if there is a chat incompatible the msgstore is not a backup
                    System.out.println("incompatible");
                    return false;
                }
                totchats++;
            }

        }
        return totchats > 0;
    }



    public int mergeChatList(List<Chat> backup) {
        int tot_rec = 0;

        int indexmain = 0;

        for (Chat c : backup) {
            indexmain = findChat(main, c);
            if (indexmain == -1) {// chat was removed
                main.add(c);
                c.setRecoveredFrom(dbname);
                tot_rec += c.getMessages().size();
            } else {
                tot_rec += mergeMessageList(main.get(indexmain).getMessages(), c.getMessages());
            }


        }
        return tot_rec;

    }

    public int mergeMessageList(List<Message> main, List<Message> backup) {
        int tot_rec = 0;

        Collections.sort(main, cmpMessage);
        Collections.sort(backup, cmpMessage);
        int indexmain = 0;
        for (Message m : backup) {

            indexmain = Collections.binarySearch(main, m, cmpMessage);
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
            if (l.get(i).getId() == key.getId()) {
                return i;
            }
        }
        return -1;
    }

}
