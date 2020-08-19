package dpf.ap.gpinf.telegramextractor;

import java.util.List;

import dpf.ap.gpinf.interfacetelegram.ContactInterface;
import dpf.ap.gpinf.interfacetelegram.DecoderTelegramInterface;
import dpf.ap.gpinf.interfacetelegram.PhotoData;

public class Contact implements ContactInterface {
    private long id;
    private int groupid;
    private String name = null;
    private String lastName = null;
    private String username = null;
    private String phone = null;
    private byte[] avatar = null;
    private List<PhotoData> photos=null;

    public Contact(long id) {
        this.id = id;
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

    public String getLastName() {
        return lastName;
    }

    public String getFullname() {
        String fn = "";
        if (name != null) {
            fn += name;
        }
        if (lastName != null) {
            if (!fn.equals("")) {
                fn += " ";
            }
            fn += lastName;
        }
        if (fn.equals("") && username != null) {
            fn = username;
        }

        return fn;
    }
    private static DecoderTelegramInterface d=null;
    public static Contact getContactFromBytes(byte[] bytes) {
    	  if(d==null) {
	          try {
	              Object o = Class.forName(Extractor.DECODER_CLASS).newInstance();
	              d = (DecoderTelegramInterface) o;
	              // System.out.println(ReflectionToStringBuilder.toString(o));
	          } catch (Exception e) {
	              System.out.println("erro ao carregar o jar do decoder");
	              // TODO: handle exception
	              return null;
	
	          }
    	  }
    	  if(d!=null) {
    		  d.setDecoderData(bytes, d.USER);
    		  Contact c=new Contact(0);
    		  d.getUserData(c);
    		  c.setPhotos(d.getPhotoData());
    		  return c;
    	  }
    	  return null;
    	  
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public void setBigName(String bigname) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setSmallName(String smallname) {
        // TODO Auto-generated method stub

    }

	public List<PhotoData> getPhotos() {
		return photos;
	}

	public void setPhotos(List<PhotoData> photos) {
		this.photos = photos;
	}

	public int getGroupid() {
		return groupid;
	}

	public void setGroupid(int groupid) {
		this.groupid = groupid;
	}

}
