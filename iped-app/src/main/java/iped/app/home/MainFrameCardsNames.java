package iped.app.home;

/*
 * @created 07/09/2022
 * @project IPED
 * @author Thiago S. Figueiredo
 */

/**
 *A List of Cards Name to be used on MainFrame "CardLayout" Layout manager
 */
public enum MainFrameCardsNames {

    HOME(0, "HOME"),
    CONFIG(1, "CONFIG"),
    NEW_CASE(2, "NEW_CASE"),
    OPEN_CASE(3, "OPEN_CASE"),
    PROCESS_MANAGER(4, "PROCESS_MANAGER"),
    TASK_CONFIG(5, "TASK_CONFIG");

    private final int value;
    private final String name;

    MainFrameCardsNames(final int value, final String name){
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

}
