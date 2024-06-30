package iped.app.home.newcase.model;/*
                                    * @created 12/09/2022
                                    * @project IPED
                                    * @author Thiago S. Figueiredo
                                    */

public class Evidence implements Cloneable {

    private String fileName;
    private String alias;
    private String path;
    private String timezone;
    private String password;
    private Integer blocksize;
    private String material;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getBlocksize() {
        return blocksize;
    }

    public void setBlocksize(Integer blocksize) {
        this.blocksize = blocksize;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }
}
