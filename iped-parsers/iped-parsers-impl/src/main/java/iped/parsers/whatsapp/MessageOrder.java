package iped.parsers.whatsapp;

public class MessageOrder extends MessageProduct {
    private final int count;

    public MessageOrder(String title, String seller, int count, String currency, int amount) {
        super(title, seller, currency, amount);
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}
