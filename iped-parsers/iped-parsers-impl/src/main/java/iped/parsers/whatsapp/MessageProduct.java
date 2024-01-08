package iped.parsers.whatsapp;

public class MessageProduct {
    protected final String seller;
    protected final String title;
    protected final String currency;
    protected final int amount;

    public MessageProduct(String title, String seller, String currency, int amount) {
        this.title = title;
        this.seller = seller;
        this.currency = currency;
        this.amount = amount;
    }

    public String getSeller() {
        return seller;
    }

    public String getTitle() {
        return title;
    }

    public String getCurrency() {
        return currency;
    }

    public int getAmount() {
        return amount;
    }
}
