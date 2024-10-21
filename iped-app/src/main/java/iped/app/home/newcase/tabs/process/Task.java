package iped.app.home.newcase.tabs.process;/*
                                           * @created 13/09/2022
                                           * @project IPED
                                           * @author Thiago S. Figueiredo
                                           */

public class Task {

    private boolean isEnabled;
    private String name;
    private String information;

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInformation() {
        return information;
    }

    public void setInformation(String information) {
        this.information = information;
    }
}
