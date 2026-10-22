package iped.parsers.whatsapp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        Map<Long, List<Integer>> carvedCopies = indexCarvedMessages(main);
        Set<Long> recoveredSortIds = new HashSet<>();
        List<Message> recovered = new ArrayList<>();
        for (Message m : backup) {
            if (Collections.binarySearch(main, m) < 0) {
                // message was removed (only the first one with each sort key is recovered)
                if (recoveredSortIds.add(m.getSortId())) {
                    recovered.add(m);
                }
            } else if (!m.isDeleted() && carvedCopies.containsKey(m.getSortId())) {
                // present in main only as a record carved from deleted space: the backup
                // record is complete (rowid, media, quotes...), so it replaces the carved one
                for (Iterator<Integer> it = carvedCopies.get(m.getSortId()).iterator(); it.hasNext();) {
                    int pos = it.next();
                    if (sameContent(main.get(pos), m)) {
                        it.remove();
                        main.set(pos, m);
                        m.setRecoveredFrom(dbname);
                        tot_rec++;
                        break;
                    }
                }
            }
        }
        if (!recovered.isEmpty()) {
            recovered.forEach(m -> m.setRecoveredFrom(dbname));
            main.addAll(recovered);
            // TimSort merges the sorted main list with the appended messages
            Collections.sort(main);
        }
        return tot_rec + recovered.size();
    }

    /**
     * Positions of the messages carved from deleted space (usually none) by sort
     * key, except those also present as regular messages.
     */
    private static Map<Long, List<Integer>> indexCarvedMessages(List<Message> messages) {
        Map<Long, List<Integer>> carved = new HashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).isDeleted()) {
                carved.computeIfAbsent(messages.get(i).getSortId(), k -> new ArrayList<>()).add(i);
            }
        }
        for (Message m : messages) {
            List<Integer> positions = carved.get(m.getSortId());
            if (positions != null && !m.isDeleted()) {
                positions.removeIf(pos -> sameContent(messages.get(pos), m));
            }
        }
        return carved;
    }

    /**
     * Compares by content, as message keys are not available for every database
     * version. The media hash is not used, because carved records have no media.
     */
    private static boolean sameContent(Message m1, Message m2) {
        return m1.isFromMe() == m2.isFromMe() && m1.getMessageType() == m2.getMessageType()
                && Objects.equals(m1.getTimeStamp(), m2.getTimeStamp()) && Objects.equals(m1.getData(), m2.getData());
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
