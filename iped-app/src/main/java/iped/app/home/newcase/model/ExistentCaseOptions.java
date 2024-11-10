package iped.app.home.newcase.model;/*
                                    * @created 04/01/2023
                                    * @project IPED
                                    * @author Thiago S. Figueiredo
                                    */

public enum ExistentCaseOptions {
    APPEND(0, "append"), CONTINUE(1, "continue"), RESTART(2, "restart");

    private int value;
    private String name;

    ExistentCaseOptions(Integer value, String name) {
        this.name = name;
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public String getCommand() {
        return "--".concat(name);
    }

}
