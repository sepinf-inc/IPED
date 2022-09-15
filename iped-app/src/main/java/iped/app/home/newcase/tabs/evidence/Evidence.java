package iped.app.home.newcase.tabs.evidence;/*
 * @created 12/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

public class Evidence implements Cloneable {

    private String fileName;
    private String alias;
    private String path;
    private String timezone;
    private String senha;
    private String password;
    private String aditionalComands;
    private String evidenceDescription;

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

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAditionalComands() {
        return aditionalComands;
    }

    public void setAditionalComands(String aditionalComands) {
        this.aditionalComands = aditionalComands;
    }

    public String getEvidenceDescription() {
        return evidenceDescription;
    }

    public void setEvidenceDescription(String evidenceDescription) {
        this.evidenceDescription = evidenceDescription;
    }

    public Object clone()
    {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
