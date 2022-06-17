package iped.parsers.whatsapp;

import java.util.List;

import iped.IItemBase;

public class WhatsAppContext {

    private boolean isMainDB;
    private boolean isBackup;

    private IItemBase item;
    private List<Chat> chalist = null;

    private IItemBase mainDBItem;

    private boolean parsingError = false;


    public WhatsAppContext(boolean isMainDB, IItemBase item) {
        this.setMainDB(isMainDB);
        this.setItem(item);
        this.setBackup(false);
        this.setMainDBItem(null);
        
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

    public Boolean isBackup() {
        return isBackup;
    }

    public void setBackup(Boolean isBackup) {
        this.isBackup = isBackup;
    }

    public IItemBase getMainDBItem() {
        return mainDBItem;
    }

    public void setMainDBItem(IItemBase mainDBItem) {
        this.mainDBItem = mainDBItem;
    }

    public boolean getParsingError() {
        return parsingError;
    }

    public void setParsingError(boolean parsingError) {
        this.parsingError = parsingError;
    }
}