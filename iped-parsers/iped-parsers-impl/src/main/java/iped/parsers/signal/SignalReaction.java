package iped.parsers.signal;

import java.util.Date;

/** A row of Signal's reaction table: one emoji per person on a message. */
public class SignalReaction {

    private SignalContact author;
    private String emoji;
    private Date dateSent;

    public SignalContact getAuthor() { return author; }
    public void setAuthor(SignalContact author) { this.author = author; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public Date getDateSent() { return dateSent; }
    public void setDateSent(Date dateSent) { this.dateSent = dateSent; }
}
