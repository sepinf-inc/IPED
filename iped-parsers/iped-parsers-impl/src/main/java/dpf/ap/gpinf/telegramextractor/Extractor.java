package dpf.ap.gpinf.telegramextractor;

import java.io.File;
import java.io.IOException;
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

    public Extractor() {
    }

    public Extractor(Connection conn) {
        this.conn = conn;
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

    protected ArrayList<Chat> extractChatList() throws Exception {
        ArrayList<Chat> l = new ArrayList<>();
        logger.debug("Extracting chat list Android");
        try (PreparedStatement stmt = conn.prepareStatement(CHATS_SQL)) {
            DecoderTelegramInterface d = (DecoderTelegramInterface) Class.forName(DECODER_CLASS).newInstance();
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
                        d.setDecoderData(dados, DecoderTelegramInterface.USER);
                        d.getUserData(cont);
                        if (cont.getAvatar() == null && d.getPhotoData().size() > 0) {
                            searchAvatarFileName(cont, d.getPhotoData());
                        }
                    }
                    cg = new Chat(chatId, cont, cont.getFullname());

                } else if ((chatName = rs.getString("groupName")) != null) {
                    dados = rs.getBytes("groupData");

                    d.setDecoderData(dados, DecoderTelegramInterface.CHAT);
                    Contact cont = getContact(chatId);
                    d.getChatData(cont);

                    searchAvatarFileName(cont, d.getPhotoData());

                    cg = new ChatGroup(chatId, cont, chatName);

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
                
                Contact c=getContact(rs.getLong("chatid"));
                
                Chat cg = null;
                
                if (c.getName()!=null && c.getName().startsWith("gp_name:")) {
                   
                   
                   cg = new ChatGroup(c.getId(),c,c.getName());

                } else{
                                        
                    cg = new Chat(c.getId(), c, c.getFullname());

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
        chatList=l;
        return l;
    }

    

    protected ArrayList<Message> extractMessages(Chat chat) throws Exception {
        ArrayList<Message> msgs = new ArrayList<Message>();
        DecoderTelegramInterface d = (DecoderTelegramInterface) Class.forName(DECODER_CLASS).newInstance();
        try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_MESSAGES_SQL)) {
            stmt.setLong(1, chat.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs != null) {
                while (rs.next()) {
                    byte[] data = rs.getBytes("data");
                    long mid = rs.getLong("mid");
                    Message message = new Message(mid, chat);
                    d.setDecoderData(data, DecoderTelegramInterface.MESSAGE);
                    d.getMessageData(message);
                    message.setRemetente(getContact(d.getRemetenteId()));

                    if (message.getMediaMime() != null) {
                        if (message.getMediaMime().startsWith("image")) {
                            List<PhotoData> list = d.getPhotoData();
                            loadImage(message, list);
                        } else if (message.getMediaMime().startsWith("link")) {
                            loadLink(message, d.getPhotoData());
                        } else if (message.getMediaMime().length() > 0) {
                            loadDocument(message, d.getDocumentNames(), d.getDocumentSize());
                        }
                       
                    }
                    if(message.getType()!=null) {
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
        
        Collections.sort(msgs, new Comparator<Message>() {
      	  public int compare(Message o1, Message o2) {
      		  if(o1==null || o2==null)
      			  return 0;
      	      return o1.getTimeStamp().compareTo(o2.getTimeStamp());
      	  }
      	});
        
        return msgs;
    }
    
    protected ArrayList<Message> extractMessagesIOS(Chat chat) throws SQLException {
        ArrayList<Message> msgs = new ArrayList<Message>();
        try (PreparedStatement stmt = conn.prepareStatement(EXTRACT_MESSAGES_SQL_IOS)) {
            stmt.setLong(1, chat.getId());
            ResultSet rs = stmt.executeQuery();
            if (rs != null) {
                while (rs.next()) {
                	PostBoxCoding p=new PostBoxCoding();
                	
                	Message message = new Message(0,chat);
                	p.readMessage(rs.getBytes("key"),rs.getBytes("value"), message);
                    
                    if(message.getNames()!=null && message.getNames().size()>0) {
                    	loadDocument(message, message.getNames(), message.getMediasize());
                    	if(message.getMediaMime()==null) {
                    		message.setMediaMime("attach");
                    	}
                    }
                    
                    message.setRemetente(getContact(message.getRemetente().getId()));

                    msgs.add(message);
                }
            }
        }
        Collections.sort(msgs, new Comparator<Message>() {
      	  public int compare(Message o1, Message o2) {
      		  if(o1==null || o2==null)
      			  return 0;
      	      return o1.getTimeStamp().compareTo(o2.getTimeStamp());
      	  }
      	});
        return msgs;
    }
    
    

    private void loadDocument(Message message, List<String> names, int size) {
        List<IItemBase> result = null;
        for (String name : names) {
        	
            String query = BasicProps.NAME + ":\"" + searcher.escapeQuery(name) + "\"";
            result = dpf.sp.gpinf.indexer.parsers.util.Util.getItems(query, searcher);
            String path = getPathFromResult(result, size);
            if (path != null) {
            	if(message.getMediaMime()==null) {
            		message.setMediaMime(result.get(0).getMediaType().toString());
            	}
            	logger.debug("Document mediaType: {}", message.getMediaMime());
                message.setMediaFile(path);
                message.setMediaHash(getHash(result, size));
                message.setThumb(getThumb(result, size));
                break;
            }

        }
    }

    private void loadLink(Message message, List<PhotoData> list) {

        for (PhotoData p : list) {
            IItemBase r = getFileFrom(p.getName(), p.getSize());
            if (r != null) {
                message.setType("link/image");
                try {
                    message.setLinkImage(FileUtils.readFileToByteArray(r.getTempFile()));
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

    }

    private void loadImage(Message message, List<PhotoData> list) {
        for (PhotoData p : list) {
            IItemBase r = getFileFrom(p.getName(), p.getSize());
            if (r != null) {
                message.setThumb(r.getThumb());
                message.setMediaHash(r.getHash());
                message.setMediaFile(r.getPath());
                message.setMediaName(r.getName());
            }
        }
    }

    protected String getPathFromResult(List<IItemBase> result, int size) {
        if (result == null || result.size()==0) {
            return null;
        }
        if(size==0) {
        	IItemBase f=result.get(0);
        	try {
                if (f.getTempFile() != null) {
                    if (f.getFile() != null) {
                        return f.getFile().getAbsolutePath();
                    } else {
                        return f.getTempFile().getAbsolutePath();
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        	
        }
        for (IItemBase f : result) {
            try {
                if (f.getTempFile() != null && f.getTempFile().getAbsoluteFile().length() == size) {
                    if (f.getFile() != null) {
                        return f.getFile().getAbsolutePath();
                    } else {
                        return f.getTempFile().getAbsolutePath();
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return null;
    }

    protected String getHash(List<IItemBase> result, int size) {
        if (result == null)
            return null;
        IItemBase file=null;
        if(size==0) {
        	file=result.get(0);
        	        	
        }
        for (IItemBase f : result) {
        	try {
                if (f.getTempFile() != null) {
                    if (f.getTempFile().getAbsoluteFile().length() == size) {
                        file=f;
                    }
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if(file!=null) {
        	return file.getHash();
        }
        
        return null;
    }

    protected byte[] getThumb(List<IItemBase> result, int size) {
        if (result == null)
            return null;
        for (IItemBase f : result) {
            if (f.getLength() != null && f.getLength() == size) {
                return f.getThumb();
            }
        }
        return null;
    }

    private IItemBase getFileFrom(String name, int size) {
        List<IItemBase> result = null;
        String query = BasicProps.NAME + ":\"" + searcher.escapeQuery(name) + "\" && " + BasicProps.LENGTH + ":" + size;
        result = dpf.sp.gpinf.indexer.parsers.util.Util.getItems(query, searcher);
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
                	Contact c=Contact.getContactFromBytes(rs.getBytes("data"));
                    /*
                     * d.setDecoderData(rs.getBytes("data"), DecoderTelegramInterface.USER);
                       Contact c = new Contact(0);
                       d.getUserData(c);
                    */
                    
                    if (c!=null && c.getId() > 0) {
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
                        //List<PhotoData> photo = d.getPhotoData();
                        if (cont.getAvatar() != null && cont.getPhotos().size() > 0) {
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
                	
                	long id=rs.getLong("key");
                    
                    
                    	
                    Contact cont = getContact(id);
                    if (cont.getName() == null) {
                    	PostBoxCoding p=new PostBoxCoding();
            			p.setData(rs.getBytes("value"));
            			p.readUser(cont);
                        
                    }

                    if (cont.getPhone() != null) {
                        nphones++;
                    }
                    //List<PhotoData> photo = d.getPhotoData();
                    if (cont.getAvatar() != null && cont.getPhotos().size() > 0) {
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
    

    private static final String CHATS_SQL = "SELECT d.did as chatId,u.name as chatName,u.data as chatData,"
            + "c.name as groupName, c.data as groupData "
            + "from dialogs d LEFT join users u on u.uid=d.did LEFT join chats c on -c.uid=d.did "
            + "order by d.date desc";
    
    private static final String CHATS_SQL_IOS = "select hex(substr(t7.key,1,8)) as chatblob, t2.key as chatid from t7 " + 
    		"left join t2 on printf('%016X',t2.key)=chatblob" + 
    		" group by chatid";

    private static final String EXTRACT_MESSAGES_SQL_IOS = "SELECT t2.key as chatid,t7.* FROM t7 inner join t2 on printf('%016X',t2.key)=hex(substr(t7.key,1,8)) " + 
    		"where chatid=?";
    private static final String EXTRACT_MESSAGES_SQL = "SELECT m.*,md.data as mediaData FROM messages m  "
            + "left join media_v2 md on md.mid=m.mid where m.uid=? order by date";

    private static final String EXTRACT_CONTACTS_SQL = "SELECT * FROM users";
    private static final String EXTRACT_CONTACTS_SQL_IOS = "SELECT * FROM t2";

}
