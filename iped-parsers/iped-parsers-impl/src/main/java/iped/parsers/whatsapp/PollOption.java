package iped.parsers.whatsapp;

public class PollOption {
    private final String name;
    private final int total;

    public PollOption(String name, int total) {
        this.name = name;
        this.total = total;
    }

    public String getName() {
        return name;
    }

    public int getTotal() {
        return total;
    }
}
