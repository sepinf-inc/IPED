package dpf.ap.gpinf.telegramextractor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.bouncycastle.crypto.engines.ISAACEngine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import dpf.ap.gpinf.telegram.tgnet.*;
import dpf.ap.gpinf.telegram.tgnet.TLRPC.DocumentAttribute;
import dpf.ap.gpinf.telegram.tgnet.TLRPC.PhotoSize;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
public class Extractor {
	Connection conn;
	public Extractor(Connection conn) {
		this.conn=conn;
	}
    private File databaseFile;
	

    private ArrayList<Chat> chatList=null;

    private HashMap<Long, Contact> contacts = new HashMap<>();
    
    
    void performExtraction() {
        try {
        	
            if(conn==null) {
            	conn=getConnection();
            }
            extractContacts();
            chatList = extractChatList();
        } catch (Exception e ) {
            //log de erro
        }
    }
   
    protected Contact getContact(long id) {
    	if(contacts.get(id)!=null) {
    		return contacts.get(id); 
    	}else {
    		Contact c=new Contact(id);
    		contacts.put(id, c);
    		return c;
    	}
    	
    }
    
    protected ArrayList<Chat> extractChatList(){
    	ArrayList<Chat> l =new ArrayList<>();
    	System.out.println("parser telegram!!!!!");
    	try {
            PreparedStatement stmt = conn.prepareStatement(CHATS_SQL);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long chatId = rs.getLong("chatId");
                String chatName = null;
                byte[] dados;
                Chat cg=null;
                if ((chatName=rs.getString("nomeChat")) != null) {
                    dados = rs.getBytes("dadosChat");
                    SerializedData s = new SerializedData(dados);
                    TLRPC.User u = TLRPC.User.TLdeserialize(s, s.readInt32(false), false);
                    
                    if (u!=null) {
                    	Contact cont=getContact(u.id);
                    	if(cont.getAvatar()==null && u.photo!=null) {
                    		searchAvatarFileName(cont,u.photo.photo_big,u.photo.photo_small);
                    	}
                         cg=new Chat(chatId,cont , chatName);
                        //println(u.first_name)

                    }
                } else if ((chatName=rs.getString("groupName")) != null) {
                    dados = rs.getBytes("dadosGrupo");
                    SerializedData s = new SerializedData(dados);
                    TLRPC.Chat c = TLRPC.Chat.TLdeserialize(s, s.readInt32(false), false);
                    Contact cont=getContact(c.id);
                    cont.setName(chatName);
                    
                    searchAvatarFileName(cont,c.photo.photo_big,c.photo.photo_small);
                    
                    cg = new ChatGroup(chatId,cont , chatName);

                }
                if(cg!=null) {
                	System.out.println("Nome do chat "+cg.getId());
                    ArrayList<Message> messages=extractMessages(conn, cg);
                    if(messages == null || messages.isEmpty())
                        continue;
                    if(cg.isGroup()){
                        ChatGroup group = (ChatGroup)cg; 
                        //group.members.putAll(getGroupMembers(conn ,cg.id,messages))
                    }
                    cg.messages.addAll(messages);
                    l.add(cg);
                }
            }
        } catch (Exception e ) {
            //log error
        	e.printStackTrace();
        	
        }

        return l;
    }
    protected void extractLink(Message message,TLRPC.WebPage webpage) {
    	message.setLink(true);
        message.setMediaMime("link");
        //message.data+="link compartilhado: "+m.media.webpage.display_url
        if(webpage.photo!=null) {
            String img=getFileFromPhoto(webpage.photo.sizes);
            

            if(img!=null){
            	try {
                    message.setLinkImage(FileUtils.readFileToByteArray(new File(img)));
                    message.setMediaMime("link/Image");
            	}catch (Exception e) {
					// TODO: handle exception
				}
            }
           
            
        }
    }
    
    protected ArrayList<Message> extractMessages(Connection conn ,Chat chat) throws Exception{
    	ArrayList<Message> msgs=new ArrayList<Message>();
    	        PreparedStatement stmt=conn.prepareStatement(EXTRACT_MESSAGES_SQL);
    	        if(stmt!=null) {
    	            stmt.setLong(1,chat.getId());
    	            ResultSet rs=stmt.executeQuery();
    	            if(rs!=null) {
    	                while (rs.next()) {
    	                    byte[] data = rs.getBytes ("data");
    	                    SerializedData sd = new SerializedData(data);
    	                    int aux = sd.readInt32 (false);
    	                    long mid=rs.getLong("mid");

    	                    TLRPC.Message m = TLRPC.Message.TLdeserialize (sd, aux, false);  	                    

    	                    if (m!=null ) {
    	                    	
    	                        Message message= new Message(mid,chat);
    	                        if(m.action!=null) {
    	                        	if(m.action.call!=null) {
    	                        		message.setType("call duration:"+m.action.duration);
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
    	                        		message.setType("User Join chat by link");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatAddUser) {
    	                        		message.setType("Chat Add User");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionUserJoined) {
    	                        		message.setType("User Join");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionHistoryClear) {
    	                        		message.setType("History Clear");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
    	                        		message.setType("User deleted");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChannelCreate) {
    	                        		message.setType("Channel created");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
    	                        		message.setType("User update photo");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
    	                        		message.setType("Chat update photo");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
    	                        		message.setType("Chat delete photo");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatEditTitle) {   	                        		
    	                        		message.setType("Change title to "+m.action.title);
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionContactSignUp) {
    	                        		message.setType("Contact sign up");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
    	                        		message.setType("Chat migrate");
    	                        	}
    	                        	if(m.action instanceof TLRPC.TL_messageActionPinMessage) {
    	                        		message.setType("Message pinned");
    	                        	}
    	                        	
    	                        	if(message.getType()==null) {
    	                        		System.out.println("tipo "+ReflectionToStringBuilder.toString(m.action));
    	                        	}
    	                        	
    	                        }
    	                        
    	                        
    	                        

    	                        message.setFromMe(rs.getInt("out")==1);

    	                        message.setRemetente(getContact(m.from_id));
    	                        
    	                        
    	                        
    	                       
    	                        message.setData(m.message);
    	                       
    	                        
    	                        message.setTimeStamp(Date.from(Instant.ofEpochSecond(m.date)));
    	                        //message.timeStamp=LocalDateTime.ofInstant(Instant.ofEpochSecond(), ZoneId.systemDefault())
    	                        if(m.media!=null) {
    	                            if(m.media.document!=null) {
    	                                extractDocument(message,m.media.document);
    	                            }

    	                            if(m.media.photo!=null){
    	                            	extractPhoto(message, m.media.photo);

    	                            }
    	                            if(m.media.webpage!=null) {
    	                            	extractLink(message, m.media.webpage);
    	                            	    	                                
    	                            }


    	                            if(message.getMediaFile()!=null){
    	                            	if(message.getMediaHash()==null) {
	    	                            	File f=new File(message.getMediaFile());
	    	                                try {
	    	                                	message.setMediaHash(Util.hashFile(new FileInputStream(f)));
	    	                                }catch(Exception e) {
	    	                                	
	    	                                }
    	                            	}
    	                                
    	                            }else{
    	                                message.setMediaHash(null);
    	                            }

    	                        }
    	                        if(message.getThumb()!=null){
    	                        	String hash=Util.hashFile(new ByteArrayInputStream(message.getThumb()));
    	                            message.setHashThumb(hash);
    	                        }
    	                        msgs.add(message);


    	                    }
    	                    //System.out.println(m.message);

    	                }
    	            }
    	        }

    	        return msgs;
    }
    
    protected void extractDocument(Message message,TLRPC.Document document) throws IOException {
    	message.setMediaMime(document.mime_type);
    	message.setMediaName(document.id+"");
    	List<IItemBase> result = null;
    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:\""+ message.getMediaName()+"\"",searcher);
        message.setMediaFile(getPathFromResult(result, document.size));
        message.setMediaHash(getHash(result, document.size));
        
    	if(message.getMediaName().contains("5332534912568264569")) {
    		
    		System.out.println("olha o arquivo "+message.getMediaName()) ;
    		
    		System.out.println("hash "+message.getMediaHash());
    		
    	}
    	       
        if(message.getMediaFile()==null){
            for( DocumentAttribute at :document.attributes){
                //tentar achar pelo nome do arquivo original
                if(at.file_name!=null){
                	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:"+ "\""+at.file_name+"\"",searcher);
                    String path=getPathFromResult(result, document.size);
                    if(path!=null){
                    	message.setThumb(FileUtils.readFileToByteArray(new File(path)));
                        break;
                    }
                }
            }
        }


        if(message.getMediaFile()==null && document.thumbs.size()>0){
            String file=getFileFromPhoto(document.thumbs);
            if(file!=null) {
                message.setThumb(FileUtils.readFileToByteArray(new File(file)));
            }
        }
    	
    }
    
    protected void extractPhoto(Message message, TLRPC.Photo photo) {
    	message.setMediaMime("image/jpeg");
        if(photo.sizes.size()>0) {
        	message.setMediaFile(getFileFromPhoto(photo.sizes));
            
        }
    }
    private IItemSearcher searcher;
    public void setSearcher(IItemSearcher s) {
    	searcher=s;
    }
    
    protected String getPathFromResult(List<IItemBase> result,int size) {
    	if(result==null) {
    		return null;
    	}
    	for(IItemBase f:result) {
    		try {
				if(f.getTempFile()!=null && f.getTempFile().getAbsoluteFile().length()==size) {
					if(f.getFile()!=null) {						
						return f.getFile().getAbsolutePath();				    	
					}else {
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
    protected String getHash(List<IItemBase> result,int size) {
    	if(result==null)
    		return null;
    	for(IItemBase f:result) {
    		try {
				if(f.getTempFile()!=null) {
					if(f.getTempFile().getAbsoluteFile().length()==size) {
						return f.getHash();
					}	
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	return null;
	}
    
    
    private String getFileFromPhoto(ArrayList<PhotoSize> sizes) {
    	List<IItemBase> result = null;
    	
	    for(TLRPC.PhotoSize img:sizes) {
	    	if(img.location==null) {
	    		continue;
	    	}
	    	String name=""+img.location.volume_id+"_"+img.location.local_id;
	    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:\""+ name+".jpg\"",searcher);
	    	
            if(result==null || result.isEmpty()){
            	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:\""+ name+"\"",searcher);
            }
            if(result!=null){
            	return getPathFromResult(result, img.size);
            	
            }
	    }
       
        return null;
		
	}

	protected void extractContacts() throws SQLException {
		if(conn!=null) {
            PreparedStatement stmt = conn.prepareStatement(EXTRACT_CONTACTS_SQL);
            if(stmt!=null){
                ResultSet rs= stmt.executeQuery();
                if(rs==null)
                	return;
                int nphones=0;
                while (rs.next()){
                	SerializedData s= new SerializedData(rs.getBytes("data"));
                	TLRPC.User user=TLRPC.User.TLdeserialize(s,s.readInt32(false),false);
                    if(user!=null){
                        Contact cont=getContact(user.id);
                        cont.setName(user.first_name);
                        cont.setUsername(user.username);
                        cont.setPhone(user.phone);
                        if(user.phone!=null) {
                        	nphones++;
                        }
                        if(user.photo!=null){
                        	try {
                        		searchAvatarFileName(cont,user.photo.photo_big,user.photo.photo_small);
                        	}catch (IOException e) {
                        		// TODO: handle exception
                        		e.printStackTrace();
							}
                        }
                    }
                }
                System.out.println("tot_phones "+nphones);
                
            }
        }
    	
    }
    
    
    protected void searchAvatarFileName(Contact contact,TLRPC.FileLocation big,TLRPC.FileLocation small) throws IOException {
        List<IItemBase> result=null;
        int size=0;
        String name=null;
        if(big!=null){
        	name=""+ big.volume_id + "_" + big.local_id+".jpg";
	    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:\""+ name+"\"",searcher);
            
            if(result==null || result.isEmpty()) {
            	name="" + big.volume_id + "_" + big.local_id;
            	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:\""+ name+"\"",searcher);
            }
        }
        if((result==null || result.isEmpty()) && small!=null){
        	name=""+ small.volume_id + "_" + small.local_id+".jpg";
        	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:\""+ name+"\"",searcher);
            
            if(result==null || result.isEmpty()) {
            	name="" + small.volume_id + "_" + small.local_id;
            	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:\""+ name+"\"",searcher);;
            }
        }
        if(result!=null && !result.isEmpty()) {
        	File f=result.get(0).getTempFile().getAbsoluteFile();
        	System.out.println("avatar " +name);
        	System.out.println("arq "+f.getName());
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
	private static final String CHATS_SQL ="SELECT d.did as chatId,u.name as nomeChat,u.data as dadosChat,"
    		+ "c.name as groupName, c.data as dadosGrupo "
    		+ "from dialogs d LEFT join users u on u.uid=d.did LEFT join chats c on -c.uid=d.did "
    		+ "order by d.date desc";
    
    private static final String EXTRACT_MESSAGES_SQL="SELECT m.*,md.data as mediaData FROM messages m  "
    		+ "left join media_v2 md on md.mid=m.mid where m.uid=? order by date";
        


    private static final String EXTRACT_CONTACTS_SQL="SELECT * FROM users";
   
    
    

}
