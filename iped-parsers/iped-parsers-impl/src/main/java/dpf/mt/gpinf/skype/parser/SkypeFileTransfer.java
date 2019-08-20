package dpf.mt.gpinf.skype.parser;

import java.util.Date;

import dpf.sp.gpinf.indexer.parsers.util.Messages;
import iped3.io.IItemBase;

/**
 * Representa uma troca de arquivos pelo Skype registrada no main.db
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */

public class SkypeFileTransfer {
    int id;
    short type;
    short status;
    Date start;
    Date accept;
    Date finish;
    String from;
    String to;
    String filePath;
    String filename;
    int fileSize;
    int bytesTransferred;
    SkypeConversation conversation = null;
    private String itemQuery;
    private IItemBase item;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getStart() {
        return start;
    }

    public void setStart(Date start) {
        this.start = start;
    }

    public Date getAccept() {
        return accept;
    }

    public void setAccept(Date accept) {
        this.accept = accept;
    }

    public Date getFinish() {
        return finish;
    }

    public void setFinish(Date end) {
        this.finish = end;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getFileSize() {
        return fileSize;
    }

    public void setFileSize(int fileSize) {
        this.fileSize = fileSize;
    }

    public int getBytesTransferred() {
        return bytesTransferred;
    }

    public void setBytesTransferred(int bytesTransferred) {
        this.bytesTransferred = bytesTransferred;
    }

    public SkypeConversation getConversation() {
        return conversation;
    }

    public void setConversation(SkypeConversation conversation) {
        this.conversation = conversation;
    }

    public short getType() {
        return type;
    }

    public void setType(short type) {
        this.type = type;
    }

    public String getTypeDescr() {
        if (type == 3) {
            return Messages.getString("SkypeFileTransfer.FileOffer"); //$NON-NLS-1$
        } else {
            return (type == 1) ? Messages.getString("SkypeFileTransfer.Download") //$NON-NLS-1$
                    : Messages.getString("SkypeFileTransfer.FileSend"); //$NON-NLS-1$
        }
    }

    public short getStatus() {
        return status;
    }

    public void setStatus(short status) {
        this.status = status;
    }

    public String getItemQuery() {
        return itemQuery;
    }

    public void setItemQuery(String itemQuery) {
        this.itemQuery = itemQuery;
    }

    public IItemBase getItem() {
        return item;
    }

    public void setItem(IItemBase item) {
        this.item = item;
    }

}
