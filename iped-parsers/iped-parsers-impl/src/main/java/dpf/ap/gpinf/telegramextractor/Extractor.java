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

import dpf.ap.gpinf.interfacetelegram.DecoderTelegramInterface;
import dpf.ap.gpinf.interfacetelegram.PhotoData;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;


public class Extractor {

    private Connection conn;
    private IItemSearcher searcher;

    public Extractor(Connection conn) {
        this.conn = conn;
    }

    private File databaseFile;

    private ArrayList<Chat> chatList = null;

    private HashMap<Long, Contact> contacts = new HashMap<>();

    void performExtraction() {
        try {

            if (conn == null) {
                conn = getConnection();
            }
            extractContacts();
            chatList = extractChatList();
        } catch (Exception e) {
            // log de erro
        }
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

    protected ArrayList<Chat> extractChatList() {
        ArrayList<Chat> l = new ArrayList<>();
        // System.out.println("parser telegram!!!!!");
        try {
            DecoderTelegramInterface d = (DecoderTelegramInterface) Class.forName(DECODER_CLASS).newInstance();
            PreparedStatement stmt = conn.prepareStatement(CHATS_SQL);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long chatId = rs.getLong("chatId");
                byte[] dados;
                Chat cg = null;
                String chatName = null;
                if ((chatName = rs.getString("nomeChat")) != null) {
                    dados = rs.getBytes("dadosChat");
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
                    dados = rs.getBytes("dadosGrupo");

                    d.setDecoderData(dados, DecoderTelegramInterface.CHAT);
                    Contact cont = getContact(chatId);
                    d.getChatData(cont);

                    searchAvatarFileName(cont, d.getPhotoData());

                    cg = new ChatGroup(chatId, cont, chatName);

                }
                if (cg != null) {
                    // System.out.println("Nome do chat "+cg.getId());
                    /*
                     * ArrayList<Message> messages=extractMessages(conn, cg); if(messages == null ||
                     * messages.isEmpty()) continue;
                     */
                    // cg.messages.addAll(messages);
                    l.add(cg);
                }

            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        return l;
    }
    
    
    protected ArrayList<Chat> extractChatListIOS() {
        ArrayList<Chat> l = new ArrayList<>();
        // System.out.println("parser telegram!!!!!");
        try {
        	//System.out.println(CHATS_SQL_IOS);
            PreparedStatement stmt = conn.prepareStatement(CHATS_SQL_IOS);
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
                    // System.out.println("Nome do chat "+cg.getId());
                    /*
                     * ArrayList<Message> messages=extractMessages(conn, cg); if(messages == null ||
                     * messages.isEmpty()) continue;
                     */
                    // cg.messages.addAll(messages);
                    l.add(cg);
                }

            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
        chatList=l;
        return l;
    }

    

    protected ArrayList<Message> extractMessages(Chat chat) throws Exception {
        ArrayList<Message> msgs = new ArrayList<Message>();
        PreparedStatement stmt = conn.prepareStatement(EXTRACT_MESSAGES_SQL);
        DecoderTelegramInterface d = (DecoderTelegramInterface) Class.forName(DECODER_CLASS).newInstance();
        if (stmt != null) {
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
    
    protected ArrayList<Message> extractMessagesIOS(Chat chat) throws Exception {
        ArrayList<Message> msgs = new ArrayList<Message>();
        PreparedStatement stmt = conn.prepareStatement(EXTRACT_MESSAGES_SQL_IOS);
        
        if (stmt != null) {
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
            	//System.out.println("tipo "+message.getMediaMime());
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

    public void setSearcher(IItemSearcher s) {
        searcher = s;
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
            if (f.getLength() == size) {
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

    protected void extractContacts() throws SQLException {
       
        if (conn != null) {
            PreparedStatement stmt = conn.prepareStatement(EXTRACT_CONTACTS_SQL);
            if (stmt != null) {
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
        	//System.out.println(EXTRACT_CONTACTS_SQL_IOS);
            PreparedStatement stmt = conn.prepareStatement(EXTRACT_CONTACTS_SQL_IOS);
            if (stmt != null) {
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
    

    private static final String CHATS_SQL = "SELECT d.did as chatId,u.name as nomeChat,u.data as dadosChat,"
            + "c.name as groupName, c.data as dadosGrupo "
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

    protected static final String DECODER_CLASS = "telegramdecoder.DecoderTelegram";

}
