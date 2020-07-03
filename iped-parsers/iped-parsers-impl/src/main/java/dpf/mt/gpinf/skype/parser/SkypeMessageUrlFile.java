package dpf.mt.gpinf.skype.parser;

import iped3.io.IItemBase;

/**
 * Classe que representa um arquivo enviado via URL registrado no arquivo
 * main.db.
 *
 * @author Patrick Dalla Bernardina patrick.pdb@dpf.gov.br
 */

public class SkypeMessageUrlFile {

    private int id;
    private String localFile;
    private String filename;
    private int size;
    private IItemBase cacheFile;
    private IItemBase thumbFile;
    private String uri;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLocalFile() {
        return localFile;
    }

    public void setLocalFile(String localFile) {
        this.localFile = localFile;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public IItemBase getCacheFile() {
        if (cacheFile != null)
            return cacheFile;
        else
            return thumbFile;
    }

    public void setCacheFile(IItemBase cacheFile) {
        this.cacheFile = cacheFile;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public IItemBase getThumbFile() {
        if (thumbFile != null)
            return thumbFile;
        else
            return cacheFile;
    }

    public void setThumbFile(IItemBase thumbFile) {
        this.thumbFile = thumbFile;
    }

}
