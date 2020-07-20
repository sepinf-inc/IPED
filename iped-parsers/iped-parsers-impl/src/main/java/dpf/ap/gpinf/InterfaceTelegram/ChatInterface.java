/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.ap.gpinf.InterfaceTelegram;

import java.util.ArrayList;

/**
 *
 * @author ADMHauck
 */
public interface ChatInterface {
    public ArrayList<MessageInterface> getMessages();
    public void setMessages(ArrayList<MessageInterface> messages);
    public ContactInterface getC();
    public void setC(ContactInterface c);
    public String getName();
    public void setName(String name);
    public boolean isGroup();
    public void setGroup(boolean isgroup);
    public long getId();
    public void setId(long id);
}
