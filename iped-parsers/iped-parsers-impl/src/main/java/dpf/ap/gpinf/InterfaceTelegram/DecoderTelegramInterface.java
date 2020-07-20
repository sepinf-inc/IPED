/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.ap.gpinf.InterfaceTelegram;

import java.util.HashMap;
import java.util.List;

/**
 *
 * @author ADMHauck
 */
public interface DecoderTelegramInterface {
    static final int MESSAGE=1;
    static final int USER=2;
    static final int CHAT=3;
    public void setDecoderData(byte[] data,int TYPE);
    public void getUserData(ContactInterface user);
    public void getMessageData(MessageInterface message);
    public void getChatData(ContactInterface chat);
    
    public List<String> getDocumentNames();
    public List<PhotoData> getPhotoData();
    public int getDocumentSize();
}
