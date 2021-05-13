/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.ap.gpinf.telegramextractor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.ap.gpinf.interfacetelegram.DecoderTelegramInterface;
import dpf.ap.gpinf.interfacetelegram.PhotoData;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;

public class Extractor {

    private static final Logger logger = LoggerFactory.getLogger(Extractor.class);

    protected static final String DECODER_CLASS = "telegramdecoder.DecoderTelegram";

    private Connection conn;
    private IItemSearcher searcher;

    private File databaseFile;

    private ArrayList<Chat> chatList = null;

    private HashMap<Long, Contact> contacts = new HashMap<>();
    private HashMap<String, byte[]> mediakey = new HashMap<>();

    private DecoderTelegramInterface android_decoder = null;

    private Contact userAccount = null;

    public Extractor() {
    }

    public Extractor(Connection conn) {
        this.conn = conn;
    }

    public Extractor(Connection conn, DecoderTelegramInterface d) {
        this.conn = conn;
        this.android_decoder = d;
    }

    public Extractor(File databaseFile) throws SQLException {
        this.databaseFile = databaseFile;
        this.conn = getConnection();
    }

    public void setSearcher(IItemSearcher s) {
        searcher = s;
    }

    protected Contact getContact(long id) {
        if (contacts.get(id) != null) {
            return contacts.get(id);
        } else {
            Contact c = new Contact(id);
            contacts.put(id, c);
            return c;
        }

    }

    protected Contact extractUserAccountIOS() throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_USERACCOUNT_SQL_IOS)) {
            ResultSet rs = stmt.executeQuery();
            if (rs != null) {
                PostBoxCoding p = new PostBoxCoding();
                p.setData(rs.getBytes("value"));
                long id = p.getAccountId();
                if (id != 0) {
                    this.userAccount = getContact(id);
                }

            }

        }
        return this.userAccount;
    }

    private List<Long> getParticipants(Connection conn, ChatGroup cg) throws SQLException {
        List<Long> l = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(MEMBERS_CHATS_SQL)) {
            stmt.setLong(1, cg.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                l.add(rs.getLong("uid"));
            }
        }
        return l;
    }

    protected ArrayList<Chat> extractChatList() throws Exception {
        ArrayList<Chat> l = new ArrayList<>();
        logger.debug("Extracting chat list Android");
        try (PreparedStatement stmt = conn.prepareStatement(CHATS_SQL)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long chatId = rs.getLong("chatId");
                byte[] dados;
                Chat cg = null;
                String chatName = null;
                if ((chatName = rs.getString("chatName")) != null) {
                    dados = rs.getBytes("chatData");
                    Contact cont = getContact(chatId);
                    if (cont.getName() == null) {
                        android_decoder.setDecoderData(dados, DecoderTelegramInterface.USER);
                        android_decoder.getUserData(cont);
                        if (cont.getAvatar() == null && !android_decoder.getPhotoData().isEmpty()) {
                            searchAvatarFileName(cont, android_decoder.getPhotoData());
                        }
                    }
                    cg = new Chat(chatId, cont, cont.getFullname());

                } else if ((chatName = rs.getString("groupName")) != null) {
                    dados = rs.getBytes("groupData");

                    android_decoder.setDecoderData(dados, DecoderTelegramInterface.CHAT);
                    Contact cont = getContact(chatId);
                    android_decoder.getChatData(cont);

                    searchAvatarFileName(cont, android_decoder.getPhotoData());

                    ChatGroup group = new ChatGroup(chatId, cont, chatName);
                    cg = group;
                    List<Long> members = getParticipants(conn, group);
                    if (members != null) {
                        group.getMembers().addAll(members);
                    }

                }
                if (cg != null) {
                    logger.debug("Telegram chat id ", cg.getId());
                    /*
                     * ArrayList<Message> messages=extractMessages(conn, cg); if(messages == null ||
                     * messages.isEmpty()) continue;
                     */
                    // cg.messages.addAll(messages);
                    l.add(cg);
                }

            }
        }
        chatList = l;
        return l;
    }

    protected ArrayList<Chat> extractChatListIOS() throws SQLException {
        ArrayList<Chat> l = new ArrayList<>();
        logger.debug("Extracting chat list iOS");
        try (PreparedStatement stmt = conn.prepareStatement(CHATS_SQL_IOS)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {

                PostBoxCoding key = new PostBoxCoding();
                key.setData(rs.getBytes("chatid"));
                long chatid = key.readInt64(0, false);

                Contact c = getContact(chatid);

                Chat cg = null;

                if (c.getName() != null && c.getName().startsWith("gp_name:")) {

                    cg = new ChatGroup(c.getId(), c, c.getName());

                } else {

                    cg = new Chat(c.getId(), c, c.getFullname());

                }
                if (cg != null) {
                    cg.setDeleted(rs.getBoolean("deleted"));

                    logger.debug("Telegram chat id ", cg.getId());
                    /*
                     * ArrayList<Message> messages=extractMessages(conn, cg); if(messages == null ||
                     * messages.isEmpty()) continue;
                     */
                    // cg.messages.addAll(messages);
                    l.add(cg);
                }

            }
        }
        chatList = l;
        return l;
    }

    protected ArrayList<Message> extractMessages(Chat chat) throws Exception {
        ArrayList<Message> msgs = new ArrayList<Message>();
        try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_MESSAGES_SQL)) {
            stmt.setLong(1, chat.getId());
            ResultSet rs = stmt.executeQuery();
            ChatGroup cg = null;
            if (chat.isGroup()) {
                cg = (ChatGroup) chat;
            }
            if (rs != null) {
                while (rs.next()) {
                    byte[] data = rs.getBytes("data");
                    long mid = rs.getLong("mid");
                    Message message = new Message(mid, chat);
                    android_decoder.setDecoderData(data, DecoderTelegramInterface.MESSAGE);
                    android_decoder.getMessageData(message);
                    long fromid = android_decoder.getRemetenteId();
                    if (fromid != 0) {
                        message.setFrom(getContact(fromid));
                    }
                    setFrom(message, chat);

                    if (cg != null && message.getFrom().getId() != 0) {
                        cg.addMember(message.getFrom().getId());
                    }

                    if (message.getMediaMime() != null) {
                        if (message.getMediaMime().startsWith("image")) {
                            List<PhotoData> list = android_decoder.getPhotoData();
                            loadImage(message, list);
                        } else if (message.getMediaMime().startsWith("link")) {
                            loadLink(message, android_decoder.getPhotoData());
                        } else if (message.getMediaMime().length() > 0) {
                            loadDocument(message, android_decoder.getDocumentNames(), android_decoder.getDocumentSize());
                        }

                    }
                    if (message.getType() != null) {
                        String type = message.getType();
                        String msg_decoded;

                        if (type.contains(":")) {
                            String[] aux = type.split(":");
                            msg_decoded = MapTypeMSG.decodeMsg(aux[0]) + ":" + aux[1];
                        } else {
                            msg_decoded = MapTypeMSG.decodeMsg(type);
                        }
                        message.setType(msg_decoded);
                    }

                    msgs.add(message);
                }
            }
        }

        Collections.sort(msgs, MSG_TIME_COMPARATOR);

        return msgs;
    }

    public void extractMediaIOS() throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_MEDIAS_SQL_IOS)) {
            ResultSet rs = stmt.executeQuery();
            if (rs != null) {
                while (rs.next()) {
                    mediakey.put(Util.byteArrayToHex(rs.getBytes("key")), rs.getBytes("value"));
                }
            }
        }
    }

    protected void setFrom(Message message, Chat chat) {

        if (message.getFrom() == null) {
            if (userAccount != null && message.isFromMe()) {
                message.setFrom(userAccount);
            } else if (!message.isFromMe() && !chat.isGroup()) {
                message.setFrom(getContact(chat.getId()));
            } else {
                // impossible to determine from
                message.setFrom(getContact(0));
            }
        } else {
            if (message.getFrom().getId() != 0) {
                // use the contact already saved in the contact database
                message.setFrom(getContact(message.getFrom().getId()));
            }
            if (userAccount == null && message.isFromMe() && message.getFrom().getId() != 0) {
                // set the userAccount based on the author info
                userAccount = message.getFrom();
            }

        }

    }

    protected ArrayList<Message> extractMessagesIOS(Chat chat) throws SQLException {
        ArrayList<Message> msgs = new ArrayList<Message>();
        try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_MESSAGES_SQL_IOS)) {
            ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
            buffer.putLong(chat.getId());
            stmt.setBytes(1, buffer.array());
            ResultSet rs = stmt.executeQuery();
            if (rs != null) {
                ChatGroup cg = null;
                if (chat.isGroup()) {
                    cg = (ChatGroup) chat;
                }
                while (rs.next()) {
                    PostBoxCoding p = new PostBoxCoding();

                    Message message = new Message(0, chat);

                    p.readMessage(rs.getBytes("key"), rs.getBytes("value"), message, mediakey);

                    setFrom(message, chat);

                    if (!chat.isGroup()) {
                        if (message.isFromMe()) {
                            message.setToId(chat.getId());
                        } else if (this.userAccount != null) {
                            message.setToId(this.userAccount.getId());
                        }
                    }

                    if (cg != null && message.getFrom().getId() != 0) {
                        cg.addMember(message.getFrom().getId());
                    }

                    if (message.getNames() != null && !message.getNames().isEmpty()) {
                        for (PhotoData f : message.getNames()) {
                            ArrayList<String> name = new ArrayList<>();
                            name.add(f.getName());
                            loadDocument(message, name, f.getSize());
                        }
                        if (message.getMediaMime() == null && message.getType() == null) {
                            message.setMediaMime("attach");
                        }
                    }

                    message.setFrom(getContact(message.getFrom().getId()));
                    msgs.add(message);
                }
            }
        }
        Collections.sort(msgs, MSG_TIME_COMPARATOR);
        return msgs;
    }

    private void loadDocument(Message message, List<String> names, int size) {
        for (String name : names) {
            String query = getQuery(name, size);
            IItemBase item = getFileFromQuery(query);
            if (item != null) {
                if (message.getMediaMime() == null) {
                    message.setMediaMime(item.getMediaType().toString());
                }
                logger.debug("Document mediaType: {}", message.getMediaMime());
                message.setMediaHash(item.getHash());
                message.setThumb(item.getThumb());
                message.setMediaExtension(item.getTypeExt());
                if (item.hasFile()) {
                    message.setMediaFile(item.getFile().getAbsolutePath());
                }
                message.setMediaComment(query);
                break;
            }

        }
    }

    private void loadLink(Message message, List<PhotoData> list) {

        for (PhotoData p : list) {
            String query = getQuery(p.getName(), p.getSize());
            IItemBase r = getFileFromQuery(query);
            if (r != null) {
                message.setLinkImage(r.getThumb());
                message.setMediaHash(r.getHash());
                message.setMediaName(r.getName());
                message.setMediaExtension(r.getTypeExt());
                if (r.hasFile()) {
                    message.setMediaFile(r.getFile().getAbsolutePath());
                }
                message.setMediaComment(query);
            }
        }

    }

    private void loadImage(Message message, List<PhotoData> list) {
        for (PhotoData p : list) {
            String query = getQuery(p.getName(), p.getSize());
            IItemBase r = getFileFromQuery(query);
            if (r != null) {
                message.setThumb(r.getThumb());
                message.setMediaHash(r.getHash());
                message.setMediaName(r.getName());
                message.setMediaExtension(r.getTypeExt());
                if (r.hasFile()) {
                    message.setMediaFile(r.getFile().getAbsolutePath());
                }
                message.setMediaComment(query);
            }
        }
    }

    private String getQuery(String name, int size) {
        String query = BasicProps.NAME + ":\"" + searcher.escapeQuery(name) + "\"";
        query += size > 0 ? " && " + BasicProps.LENGTH + ":" + size : "";
        return query;
    }

    private IItemBase getFileFromQuery(String query) {
        List<IItemBase> result = dpf.sp.gpinf.indexer.parsers.util.Util.getItems(query, searcher);
        if (result != null && !result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    protected void extractContacts() throws Exception {

        if (conn != null) {
            try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_CONTACTS_SQL)) {
                ResultSet rs = stmt.executeQuery();
                if (rs == null)
                    return;
                int nphones = 0;
                while (rs.next()) {
                    Contact c = Contact.getContactFromBytes(rs.getBytes("data"), android_decoder);
                    /*
                     * d.setDecoderData(rs.getBytes("data"), DecoderTelegramInterface.USER); Contact
                     * c = new Contact(0); d.getUserData(c);
                     */

                    if (c != null && c.getId() > 0) {
                        Contact cont = getContact(c.getId());
                        if (cont.getName() == null) {
                            cont.setName(c.getName());
                            cont.setLastName(c.getLastName());
                            cont.setUsername(c.getUsername());
                            cont.setPhone(c.getPhone());
                        }

                        if (cont.getPhone() != null) {
                            nphones++;
                        }
                        // List<PhotoData> photo = d.getPhotoData();
                        if (cont.getAvatar() != null && !cont.getPhotos().isEmpty()) {
                            try {
                                if (cont.getPhone() != null)
                                    searchAvatarFileName(cont, cont.getPhotos());
                            } catch (IOException e) {
                                // TODO: handle exception
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

    }

    protected void extractContactsIOS() throws SQLException {
        if (conn != null) {
            try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_CONTACTS_SQL_IOS)) {
                ResultSet rs = stmt.executeQuery();
                if (rs == null)
                    return;
                int nphones = 0;
                while (rs.next()) {

                    long id = rs.getLong("key");

                    Contact cont = getContact(id);
                    if (cont.getName() == null) {
                        PostBoxCoding p = new PostBoxCoding();
                        p.setData(rs.getBytes("value"));
                        p.readUser(cont);

                    }

                    if (cont.getPhone() != null) {
                        nphones++;
                    }
                    // List<PhotoData> photo = d.getPhotoData();
                    if (cont.getAvatar() != null && !cont.getPhotos().isEmpty()) {
                        try {
                            if (cont.getPhone() != null)
                                searchAvatarFileName(cont, cont.getPhotos());
                        } catch (IOException e) {
                            // TODO: handle exception
                            e.printStackTrace();
                        }
                    }

                }
            }
        }

    }

    protected void searchAvatarFileName(Contact contact, List<PhotoData> photos) throws IOException {
        if (photos == null)
            return;
        List<IItemBase> result = null;
        String name = null;
        for (PhotoData photo : photos) {
            if (photo.getName() != null) {
                name = photo.getName() + ".jpg";
                String query = BasicProps.NAME + ":\"" + searcher.escapeQuery(name) + "\"  -" + BasicProps.LENGTH
                        + ":0";
                result = dpf.sp.gpinf.indexer.parsers.util.Util.getItems(query, searcher);
                if (result != null && !result.isEmpty()) {
                    break;
                }
            }
        }
        if (result != null && !result.isEmpty()) {
            File f = result.get(0).getTempFile().getAbsoluteFile();
            contact.setAvatar(FileUtils.readFileToByteArray(f));
        }
    }

    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

    public ArrayList<Chat> getChatList() {
        return chatList;
    }

    public HashMap<Long, Contact> getContacts() {
        return contacts;
    }

    public Contact getUserAccount() {
        return userAccount;
    }

    private static final Comparator<Message> MSG_TIME_COMPARATOR = new Comparator<Message>() {
        public int compare(Message o1, Message o2) {
            boolean o1Empty = o1 == null || o1.getTimeStamp() == null;
            boolean o2Empty = o2 == null || o2.getTimeStamp() == null;
            if (o1Empty && o2Empty) {
                return 0;
            } else if (o1Empty && !o2Empty) {
                return -1;
            } else if (!o1Empty && o2Empty) {
                return 1;
            } else {
                return o1.getTimeStamp().compareTo(o2.getTimeStamp());
            }
        }
    };

    private static final String EXTRACT_USERACCOUNT_SQL_IOS = "SELECT t0.value FROM T0 where key=2";

    private static final String CHATS_SQL = "SELECT d.did as chatId,u.name as chatName,u.data as chatData,"
            + "c.name as groupName, c.data as groupData "
            + "from dialogs d LEFT join users u on u.uid=d.did LEFT join chats c on -c.uid=d.did "
            + "order by d.date desc";

    private static final String MEMBERS_CHATS_SQL = "SELECT * from channel_users_v2 where did=?";

    private static final String CHATS_SQL_IOS = "select substr(key,16,8) as chatid, false as deleted from t9 "
            + "UNION SELECT  substr(t7.key,1,8) as chatid, true as deleted from t7  "
            + "where substr(t7.value,1,1)=x'00' and chatid not in (select substr(key,16,8) as chatid from t9) "
            + "group by chatid";

    private static final String EXTRACT_MEDIAS_SQL_IOS = "SELECT key,value from t6 ";

    private static final String EXTRACT_MESSAGES_SQL_IOS = "SELECT t7.key,t7.value FROM t7 where substr(t7.key,1,8)=? and "
            + "substr(t7.value,1,1)=x'00'";

    private static final String EXTRACT_MESSAGES_SQL = "SELECT m.*,md.data as mediaData FROM messages m  "
            + "left join media_v2 md on md.mid=m.mid where m.uid=? order by date";

    private static final String EXTRACT_CONTACTS_SQL = "SELECT * FROM users";
    private static final String EXTRACT_CONTACTS_SQL_IOS = "SELECT * FROM t2";

}
