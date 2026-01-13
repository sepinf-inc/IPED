package iped.parsers.whatsapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatMerge {

    List<Chat> main;
    String dbname;

    public ChatMerge(List<Chat> main, String dbname) {
        this.main = main;
        this.dbname = dbname;
        for (Chat c : main) {
            // new instance to avoid threading issues
            List<Message> messages = new ArrayList<Message>(c.getMessages());
            Collections.sort(messages);
            c.setMessages(messages);
        }
    }

    public boolean isBackup(List<Chat> backup, WAContactsDirectory contacts) {
        if (backup == null) {
            return false;
        }
        int indexmain = 0;
        int totchats = 0;
        for (Chat c : backup) {
            indexmain = findChat(main, c);
            if (indexmain >= 0) {// verify if the chats are compatible
                if (!contacts.getContact(c.getRemote().getId())
                        .matches(contacts.getContact(main.get(indexmain).getRemote().getId()))) {
                    // if there is a chat incompatible the msgstore is not a backup
                    return false;
                } else if (hasCompatibleMessage(c.getMessages(), main.get(indexmain).getMessages())) {
                    totchats++;
                }
            }

        }
        return totchats > 0;
    }


    private boolean hasCompatibleMessage(List<Message> backup, List<Message> main) {
        int maxMsgsToCheck = 10;
        for (int i = 0; i < backup.size(); i += Math.max(1, backup.size() / maxMsgsToCheck)) {
            int idx = Collections.binarySearch(main, backup.get(i));
            if (idx >= 0) {
                return true;
            }
        }
        return false;
    }

    public int mergeChatList(List<Chat> backup) {
        int tot_rec = 0;

        int indexmain = 0;

        for (Chat c : backup) {
            indexmain = findChatByContact(main, c);
            if (indexmain == -1) {// chat was removed
                main.add(c);
                c.setRecoveredFrom(dbname);
                for (Message m : c.getMessages()) {
                    m.setRecoveredFrom(dbname);
                }
                tot_rec += c.getMessages().size();
            } else {
                tot_rec += mergeMessageList(main.get(indexmain).getMessages(), c.getMessages());
            }


        }
        return tot_rec;

    }

    private int mergeMessageList(List<Message> main, List<Message> backup) {
        int tot_rec = 0;
        int indexmain = 0;
        for (Message m : backup) {

            indexmain = Collections.binarySearch(main, m);
            if (indexmain < 0) {// message was removed
                main.add(-indexmain - 1, m);
                tot_rec++;
                m.setRecoveredFrom(dbname);
            } 

        }
        return tot_rec;

    }

    private int findChatByContact(List<Chat> l, Chat key) {
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).getRemote().getId().equals(key.getRemote().getId())) {
                return i;
            }
        }
        return -1;
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
