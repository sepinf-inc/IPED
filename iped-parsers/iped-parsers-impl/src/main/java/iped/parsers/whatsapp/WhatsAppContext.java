package iped.parsers.whatsapp;

import java.util.List;

import iped.data.IItemReader;

public class WhatsAppContext {

    private boolean isMainDB;
    private boolean isBackup;

    private IItemReader item;
    private List<Chat> chalist = null;

    private IItemReader mainDBItem;

    private boolean parsingError = false;


    public WhatsAppContext(boolean isMainDB, IItemReader item) {
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

    public IItemReader getItem() {
        return item;
    }

    public void setItem(IItemReader item) {
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

    public IItemReader getMainDBItem() {
        return mainDBItem;
    }

    public void setMainDBItem(IItemReader mainDBItem) {
        this.mainDBItem = mainDBItem;
    }

    public boolean getParsingError() {
        return parsingError;
    }

    public void setParsingError(boolean parsingError) {
        this.parsingError = parsingError;
    }
}