/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dpf.ap.gpinf.InterfaceTelegram;

/**
 *
 * @author ADMHauck
 */
public interface ContactInterface {
    public long getId() ;
	public void setId(long id);
	public String getName();
	public void setName(String name);
	public String getPhone();
	public void setPhone(String telefone);
	public String getUsername();
	public void setUsername(String username);
	public byte[] getAvatar();
	public void setAvatar(byte[] avatar);
	public String getLastName();
	public void setLastName(String lastName);
        public void setBigName(String bigname);
        public void setSmallName(String smallname);
}
