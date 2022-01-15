package dpf.mg.udi.gpinf.whatsappextractor;

import java.util.List;

import iped3.io.IItemBase;

public class WhatsAppContext {

    private boolean isMainDB;
    private boolean isBackup;

    private IItemBase item;
    private List<Chat> chalist = null;


    public WhatsAppContext(boolean isMainDB, IItemBase item) {
        this.setMainDB(isMainDB);
        this.setItem(item);
        
    }

    public boolean isMainDB() {
        return isMainDB;
    }

    public void setMainDB(boolean isMainDB) {
        this.isMainDB = isMainDB;
    }

    public IItemBase getItem() {
        return item;
    }

    public void setItem(IItemBase item) {
        this.item = item;
    }

    public List<Chat> getChalist() {
        return chalist;
    }

    public void setChalist(List<Chat> chalist) {
        this.chalist = chalist;
    }

    public boolean isBackup() {
        return isBackup;
    }

    public void setBackup(boolean isBackup) {
        this.isBackup = isBackup;
    }
}