package dpf.ap.gpinf.telegramextractor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;

import org.apache.commons.io.FileUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import dpf.ap.gpinf.telegram.tgnet.*;
import dpf.ap.gpinf.telegram.tgnet.TLRPC.PhotoSize;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
public class Extractor {
	Connection conn;
	public Extractor(Connection conn) {
		this.conn=conn;
	}
    File databaseFile;
	HashMap<String, Integer> logHash=new HashMap<String, Integer>();

    
    String database;
    ArrayList<Chat> chatList=null;

    HashMap<String, Contact> contacts = new HashMap<String,Contact>();
    
    
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
    	return null;
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
                         cg=new Chat(chatId,cont , chatName);
                        //println(u.first_name)

                    }
                } else if ((chatName=rs.getString("groupName")) != null) {
                    dados = rs.getBytes("dadosGrupo");
                    SerializedData s = new SerializedData(dados);
                    TLRPC.Chat c = TLRPC.Chat.TLdeserialize(s, s.readInt32(false), false);
                    Contact cont=getContact(c.id);
                    searchAvatarFileName(cont,c.photo.photo_big,c.photo.photo_small);
                    
                    cg = new ChatGroup(chatId,cont , chatName);

                }
                if(cg!=null) {
                	System.out.println("Nome do chat "+cg.getName());
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
        }

        return l;
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

    	                        message.setFromMe(rs.getInt("out")==1);

    	                        message.setRemetente(getContact(m.from_id));
    	                        
    	                        
    	                        
    	                       
    	                        message.setData(m.message);
    	                        System.out.println(message.getData());
    	                        message.setTimeStamp(new Date(m.date));
    	                        //message.timeStamp=LocalDateTime.ofInstant(Instant.ofEpochSecond(toLong(m.date)), ZoneId.systemDefault())
    	                        if(m.media!=null) {
    	                            if(m.media.document!=null) {
    	                                extractDocument(message,m.media.document);
    	                            }

    	                            if(m.media.photo!=null){
    	                            	extractPhoto(message, m.media.photo);

    	                            }
    	                            if(m.media.webpage!=null) {
    	                                message.setLink(true);
    	                                message.setMediaMime("link");
    	                                //message.data+="link compartilhado: "+m.media.webpage.display_url
    	                                if(m.media.webpage.photo!=null) {
    	                                    String img=getFileFromPhoto(m.media.webpage.photo.sizes);
    	                                    

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


    	                            if(message.getMediaFile()!=null){
    	                            	File f=new File(message.getMediaFile());
    	                                try {
    	                                	message.setMediaHash(Util.hashFile(new FileInputStream(f)));
    	                                }catch(Exception e) {
    	                                	
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
    
    protected void extractDocument(Message message,TLRPC.Document document) {
    	
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
    	for(IItemBase f:result) {
    		if(f.getFile().getAbsoluteFile().length()==size) {
        		return f.getFile().getAbsolutePath();
        	}	
    	}
    	return null;
	}
    
    private String getFileFromPhoto(ArrayList<PhotoSize> sizes) {
    	List<IItemBase> result = null;
    	
	    for(TLRPC.PhotoSize img:sizes) {
	    	String name=""+img.location.volume_id+"_"+img.location.local_id;
	    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:"+ name+".jpg",searcher);
	    	
            if(result==null || result.isEmpty()){
            	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems("name:"+ name+"*",searcher);
            }
            if(result!=null){
            	return getPathFromResult(result, img.size);
            	
            }
	    }
       
        return null;
		
	}

	protected void extractContacts() {
    	
    }
    
    
    protected void searchAvatarFileName(Contact contact,TLRPC.FileLocation big,TLRPC.FileLocation small) {
        /*
        if(big!=null){
            file=fileList.findFileByName("" + big.volume_id + "_" + big.local_id+".jpg")
            if(file==null) {
                file = fileList.findFileContainingName("" + big.volume_id + "_" + big.local_id, null)
            }
        }
        if(file==null && small!=null){
            file=fileList.findFileByName("" + small.volume_id + "_" + small.local_id+".jpg")
            if(file==null) {
                file = fileList.findFileContainingName("" + small.volume_id + "_" + small.local_id, null)
            }
        }
        if(file!=null) {
            contact.avatar = file
        }
        */
    }
    
    
    protected Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }


    private static final String CHATS_SQL ="SELECT d.did as chatId,u.name as nomeChat,u.data as dadosChat,"
    		+ "c.name as groupName, c.data as dadosGrupo "
    		+ "from dialogs d LEFT join users u on u.uid=d.did LEFT join chats c on -c.uid=d.did "
    		+ "order by d.date desc";
    
    private static final String EXTRACT_MESSAGES_SQL="SELECT m.*,md.data as mediaData FROM messages m  "
    		+ "left join media_v2 md on md.mid=m.mid where m.uid=? order by date";
        



   
    
    

}
