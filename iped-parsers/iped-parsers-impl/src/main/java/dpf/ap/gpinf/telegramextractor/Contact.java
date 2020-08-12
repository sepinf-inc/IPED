package dpf.ap.gpinf.telegramextractor;

import dpf.ap.gpinf.interfacetelegram.ContactInterface;

public class Contact implements ContactInterface {
    private long id;
    private String name = null;
    private String lastName = null;
    private String username = null;
    private String phone = null;
    private byte[] avatar = null;

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

}
