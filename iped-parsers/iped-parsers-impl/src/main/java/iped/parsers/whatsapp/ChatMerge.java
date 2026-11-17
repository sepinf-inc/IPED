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

import iped.parsers.whatsapp.Message.MessageType;

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
        // Merge strategy (main is sorted by sort key, see compareTo()):
        //
        // 1. If the sort key of a backup message is not found in main, the message was
        // deleted from the main DB and is recovered. If the backup has more than one
        // message with that sort key, only the first one is recovered.
        //
        // 2. If the sort key is found in main, the message is already there and the
        // backup message is ignored. The exception is when main has that message only
        // as a record carved from deleted space by SQLiteUndelete (see sameMessage()).
        // The backup record is complete (rowid, media, quotes...), so it replaces the
        // carved record at the same position.
        //
        // 3. Carved records often lose their sort key (the column comes after the text)
        // and are then sorted by time, so they are not found by step 2. They are
        // looked up by message key instead and removed when the backup message is
        // recovered by step 1.
        //
        // 4. Recovered messages are not inserted one by one, as each insertion shifts
        // the rest of the list. They are appended to main, which is then sorted once.
        // As main and the appended messages are normally already sorted, TimSort just
        // merges them, in about linear time.

        int tot_rec = 0;
        Map<Long, List<Integer>> carvedCopies = indexCarvedMessages(main);
        Map<String, List<Integer>> carvedByKey = indexCarvedMessagesByKey(main);
        Set<Integer> replacedCarved = new HashSet<>();
        Set<Long> recoveredSortIds = new HashSet<>();
        List<Message> recovered = new ArrayList<>();
        for (Message m : backup) {
            if (Collections.binarySearch(main, m) < 0) {
                // step 1
                if (recoveredSortIds.add(m.getSortId())) {
                    recovered.add(m);
                    if (!m.isDeleted() && hasUuid(m) && carvedByKey.containsKey(m.getUuid())) {
                        // step 3
                        for (int pos : carvedByKey.get(m.getUuid())) {
                            if (replaces(m, main.get(pos))) {
                                replacedCarved.add(pos);
                            }
                        }
                    }
                }
            } else if (!m.isDeleted() && carvedCopies.containsKey(m.getSortId())) {
                // step 2: look for a carved record of the same message to replace
                for (Iterator<Integer> it = carvedCopies.get(m.getSortId()).iterator(); it.hasNext();) {
                    int pos = it.next();
                    if (!replacedCarved.contains(pos) && replaces(m, main.get(pos))) {
                        it.remove();
                        main.set(pos, m);
                        m.setRecoveredFrom(dbname);
                        tot_rec++;
                        break;
                    }
                }
            }
        }
        if (!replacedCarved.isEmpty()) {
            List<Integer> positions = new ArrayList<>(replacedCarved);
            positions.sort(Collections.reverseOrder());
            for (int pos : positions) {
                main.remove(pos);
            }
        }
        if (!recovered.isEmpty()) {
            // step 4
            recovered.forEach(m -> m.setRecoveredFrom(dbname));
            main.addAll(recovered);
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
                positions.removeIf(pos -> sameMessage(messages.get(pos), m));
            }
        }
        return carved;
    }

    /**
     * Positions of the messages carved from deleted space by message key.
     */
    private static Map<String, List<Integer>> indexCarvedMessagesByKey(List<Message> messages) {
        Map<String, List<Integer>> carved = new HashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m.isDeleted() && hasUuid(m)) {
                carved.computeIfAbsent(m.getUuid(), k -> new ArrayList<>()).add(i);
            }
        }
        return carved;
    }

    /**
     * Tells if the backup message should take the place of the carved record: both
     * are the same message and the backup one is not just a placeholder without
     * the content of the message, i.e. a message deleted for everyone or a system
     * message. Current WhatsApp versions give the placeholder of a message deleted
     * for everyone a new message key, but if the key is kept, as seen when a
     * message becomes a system message, the placeholder must not replace the
     * content recovered from deleted space.
     */
    private static boolean replaces(Message fromBackup, Message carved) {
        return sameMessage(carved, fromBackup) && !(isPlaceholder(fromBackup) && !isPlaceholder(carved));
    }

    private static boolean isPlaceholder(Message m) {
        MessageType type = m.getMessageType();
        return type == MessageType.DELETED_MESSAGE || type == MessageType.DELETED_BY_SENDER
                || type == MessageType.DELETED_BY_ADMIN || m.isSystemMessage();
    }

    /**
     * Tells if both are the same version of the same message. The text is not
     * compared, because carved records may be truncated, nor the media hash,
     * because they have no media.
     * <p>
     * An edited or revoked message keeps its sort key but gets a new message key,
     * so the previous version carved from the main DB has the same sort key as the
     * current one and a different message key. When both messages have a key, it
     * identifies the version. Otherwise (key missing in a carved record or in older
     * database versions) sender, type and times are compared.
     */
    private static boolean sameMessage(Message m1, Message m2) {
        if (m1.isFromMe() != m2.isFromMe()) {
            return false;
        }
        if (hasUuid(m1) && hasUuid(m2)) {
            return m1.getUuid().equals(m2.getUuid());
        }
        return m1.getMessageType() == m2.getMessageType() && Objects.equals(m1.getTimeStamp(), m2.getTimeStamp())
                && Objects.equals(m1.getEditTimeStamp(), m2.getEditTimeStamp());
    }

    private static boolean hasUuid(Message m) {
        return m.getUuid() != null && !m.getUuid().isEmpty();
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
