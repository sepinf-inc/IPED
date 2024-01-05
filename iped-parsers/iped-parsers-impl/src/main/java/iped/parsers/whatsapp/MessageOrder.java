package iped.parsers.whatsapp;

public class MessageOrder {
    private final String seller;
    private final String title;
    private final int count;
    private final String currency;
    private final int amount;

    public MessageOrder(String title, String seller, int count, String currency, int amount) {
        this.title = title;
        this.seller = seller;
        this.count = count;
        this.currency = currency;
        this.amount = amount;
    }

    public String getSeller() {
        return seller;
    }

    public String getTitle() {
        return title;
    }

    public int getCount() {
        return count;
    }

    public String getCurrency() {
        return currency;
    }

    public int getAmount() {
        return amount;
    }
}
