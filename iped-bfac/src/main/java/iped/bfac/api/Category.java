package iped.bfac.api;

/**
 * Represents a category in the BFAC system.
 */
public class Category {

    private int id;
    private String name;
    private String description;
    private boolean ignorable;
    private boolean alert;

    public Category() {
    }

    public Category(int id, String name, String description, boolean ignorable, boolean alert) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.ignorable = ignorable;
        this.alert = alert;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isIgnorable() {
        return ignorable;
    }

    public void setIgnorable(boolean ignorable) {
        this.ignorable = ignorable;
    }

    public boolean isAlert() {
        return alert;
    }

    public void setAlert(boolean alert) {
        this.alert = alert;
    }

    @Override
    public String toString() {
        return name;
    }
}
