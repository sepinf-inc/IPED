package dpf.ap.gpinf.telegramextractor;

import java.util.ArrayList;

public class Chat {
	ArrayList<Message> messages  =new ArrayList<>();
	private  Contact c;
	private String name;
	private boolean group;
	private long id;
	
	public ArrayList<Message> getMessages() {
		return messages;
	}
	public void setMessages(ArrayList<Message> messages) {
		this.messages = messages;
	}
	public Contact getC() {
		return c;
	}
	public void setC(Contact c) {
		this.c = c;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean isGroup() {
		return group;
	}
	public void setGroup(boolean isgroup) {
		this.group = isgroup;
	}
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	
	
	public Chat(long id,Contact c,String name) {
		this.id=id;
		this.c=c;
		this.name=name;
		this.group=false;
	}
	
}
