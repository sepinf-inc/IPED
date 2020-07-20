/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.ap.gpinf.InterfaceTelegram;

import java.util.Date;

/**
 *
 * @author ADMHauck
 */
public interface MessageInterface {
    public long getId();
	public void setId(long id);
	public String getMediaHash();
	public void setMediaHash(String mediaHash);
	public String getMediaFile();
	public void setMediaFile(String mediaFile);
	public boolean isFromMe();
	public void setFromMe(boolean fromMe);
	
	
	public ContactInterface getRemetente();
	public void setRemetente(ContactInterface remetente);
	public ChatInterface getChat();
	public void setChat(ChatInterface chat);
	public String getData();
	public void setData(String data);
	public Date getTimeStamp();
	public void setTimeStamp(Date timeStamp);
	public String getMediaMime() ;
	public void setMediaMime(String mediaMime) ;
	public boolean isLink() ;
	public void setLink(boolean link) ;
	public byte[] getLinkImage();
	public void setLinkImage(byte[] linkImage);
	public byte[] getThumb();
	public void setThumb(byte[] thumb);
	public String getHashThumb();
	public void setHashThumb(String hashThumb);
	public String getMediaName();
	public void setMediaName(String mediaName);
	public String getType() ;
	public void setType(String type);
}
