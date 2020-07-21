package dpf.ap.gpinf.telegramextractor;
import java.io.File;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

import dpf.ap.gpinf.interfacetelegram.DecoderTelegramInterface;
import dpf.ap.gpinf.interfacetelegram.PhotoData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
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
    protected  ArrayList<Chat> extractChatList(){
    	 ArrayList<Chat> l=new ArrayList<>();
    	 System.out.println("parser telegram!!!!!");
     	try {
     		DecoderTelegramInterface d=(DecoderTelegramInterface)Class.forName(DECODER_CLASS).newInstance();
             PreparedStatement stmt = conn.prepareStatement(CHATS_SQL);
             ResultSet rs = stmt.executeQuery();
             while (rs.next()) {
            	 long chatId = rs.getLong("chatId");
            	 byte[] dados;
                 Chat cg=null;
                 String chatName = null;
                 if ((chatName=rs.getString("nomeChat")) != null) {
                	 dados = rs.getBytes("dadosChat");
                	 Contact user=new Contact(0);
                	 d.setDecoderData(dados, DecoderTelegramInterface.USER);
                	 d.getUserData(user);
                     
                     
                     
                     if (user.getId()>0) {
                     	Contact cont=getContact(user.getId());
                     	if(cont.getAvatar()==null && d.getPhotoData().size()>0) {
                     		searchAvatarFileName(cont,d.getPhotoData());
                     	}
                          cg=new Chat(chatId,cont, cont.getName()+" "+cont.getLastName());
                         

                     }
                 } else if ((chatName=rs.getString("groupName")) != null) {
                     dados = rs.getBytes("dadosGrupo");
                     
                	 d.setDecoderData(dados, DecoderTelegramInterface.CHAT);
                     Contact cont=getContact(chatId);
                     d.getChatData(cont);
                     
                     
                     searchAvatarFileName(cont,d.getPhotoData());
                     
                     cg = new ChatGroup(chatId,cont , chatName);
                     

                 }
                 if(cg!=null) {
                 	System.out.println("Nome do chat "+cg.getId());
                     /*
                 	ArrayList<Message> messages=extractMessages(conn, cg);
                     if(messages == null || messages.isEmpty())
                         continue;
                    */
                     //cg.messages.addAll(messages);
                     l.add(cg);
                 }
            	 
             }
     	}catch (Exception e) {
			// TODO: handle exception
     		e.printStackTrace();
		}
     	return l;
    }
    
    protected ArrayList<Message> extractMessages(Chat chat) throws Exception{
    	ArrayList<Message> msgs=new ArrayList<Message>();
        PreparedStatement stmt=conn.prepareStatement(EXTRACT_MESSAGES_SQL);
        DecoderTelegramInterface d=(DecoderTelegramInterface)Class.forName(DECODER_CLASS).newInstance();
        if(stmt!=null) {
            stmt.setLong(1,chat.getId());
            ResultSet rs=stmt.executeQuery();
            if(rs!=null) {
                while (rs.next()) {
                	 byte[] data = rs.getBytes ("data");
                	 long mid=rs.getLong("mid");
                	 Message message= new Message(mid,chat);
                	 d.setDecoderData(data, DecoderTelegramInterface.MESSAGE);
                	 d.getMessageData(message);
                	 message.setRemetente(getContact(d.getRemetenteId()));
                	 
                	 if(message.getMediaMime()!=null) {
	                	 if(message.getMediaMime().startsWith("image")) {
	                		 List<PhotoData> list=d.getPhotoData();
	                		 loadImage(message, list);
	                	 }else if(message.getMediaMime().startsWith("link")) {
	                		 loadLink(message, d.getPhotoData());
	                	 }else if(message.getMediaMime().length()>0) {
	                		 loadDocument(message,d.getDocumentNames(),d.getDocumentSize());
	                	 }
                	 }
                	 msgs.add(message);
                }
            }
        }
        return msgs;
    }
    private void loadDocument(Message message,List<String> names,int size) {
    	List<IItemBase> result = null;
    	for(String name:names) {
        	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":"+ "\""+name+"\"",searcher);
            String path=getPathFromResult(result, size);
            if(path!=null){
            	message.setMediaFile(path);
            	message.setMediaHash(getHash(result, size));
            	message.setThumb(getThumb(result, size));
                break;
            }
            
    	}
    }
    private void loadLink(Message message,List<PhotoData> list) {
    	
    	for(PhotoData p:list) {
    		IItemBase r=getFileFrom(p.getName(), p.getSize());
    		if(r!=null) {
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
    private void loadImage(Message message,List<PhotoData> list) {
    	for(PhotoData p:list) {
    		IItemBase r=getFileFrom(p.getName(), p.getSize());
    		if(r!=null) {
    			message.setThumb(r.getThumb());
    			message.setMediaHash(r.getHash());
    			message.setMediaFile(r.getPath());
    			message.setMediaName(r.getName());
    		}
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
    
    protected byte[] getThumb(List<IItemBase> result,int size) {
    	if(result==null)
    		return null;
    	for(IItemBase f:result) {
    		if(f.getLength()==size) {
					return f.getThumb();
    		}
    	}
    	return null;
	}
    
    private IItemBase  getFileFrom(String name,int size) {
    	List<IItemBase> result = null;
    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+"\" && size:"+size,searcher);
    	if(result!=null && !result.isEmpty()) {
    		return result.get(0);
    	}
    	return null;
    }
   
	protected void extractContacts() throws SQLException {
		DecoderTelegramInterface d=null;
		try {
			Object o=Class.forName(DECODER_CLASS).newInstance();
			d=(DecoderTelegramInterface)o;
			System.out.println(ReflectionToStringBuilder.toString(o));
		}catch (Exception e) {
			System.out.println("erro ao carregar");
			// TODO: handle exception
			return;
			
		}
		if(conn!=null) {
            PreparedStatement stmt = conn.prepareStatement(EXTRACT_CONTACTS_SQL);
            if(stmt!=null){
                ResultSet rs= stmt.executeQuery();
                if(rs==null)
                	return;
                int nphones=0;
                while (rs.next()){
                	d.setDecoderData(rs.getBytes("data"), DecoderTelegramInterface.USER);
                	Contact c=new Contact(0);
                	d.getUserData(c);
                	//SerializedData s= new SerializedData();
                	//TLRPC.User user=TLRPC.User.TLdeserialize(s,s.readInt32(false),false);
                    //Contact cont=
                	if(c.getId()>0){
                        Contact cont=getContact(c.getId());
                        if(cont.getName()==null) {
                        	cont.setName(c.getName());
                        	cont.setLastName(c.getLastName());
                        	cont.setUsername(c.getUsername());
                        	cont.setPhone(c.getPhone());
                        }
                        
                        if(cont.getPhone()!=null) {
                        	nphones++;
                        }
                        List<PhotoData> photo=d.getPhotoData();
                        if(cont.getAvatar()!=null &&  photo.size()>0){
                        	try {
                        		if(cont.getPhone()!=null)
                        			searchAvatarFileName(cont,photo);
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
    
	protected void searchAvatarFileName(Contact contact,List<PhotoData> photos) throws IOException {
		 List<IItemBase> result=null;
		 String name=null;
		for(PhotoData photo:photos ) {
			if(photo.getName()!=null) {
				name=photo.getName()+".jpg";
		    	result=dpf.sp.gpinf.indexer.parsers.util.Util.getItems(BasicProps.NAME+":\""+ name+"\"  - "+BasicProps.LENGTH+":0",searcher);
		    	if(result!=null && !result.isEmpty()) {
		    		break;
		    	}
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
   
    private static final String DECODER_CLASS="telegramdecoder.DecoderTelegram";
    
    

}
