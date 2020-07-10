package dpf.ap.gpinf.telegramextractor;

public class Contact {
	private long id;
	private String name=null;
	private String username=null;
	private String phone=null;
	private byte[] avatar=null;
	public Contact(long id) {
		this.id=id;
	}
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getPhone() {
		return phone;
	}
	public void setPhone(String telefone) {
		this.phone = telefone;
	}
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public byte[] getAvatar() {
		return avatar;
	}
	public void setAvatar(byte[] avatar) {
		this.avatar = avatar;
	}

}
