package iped.parsers.ufed.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a <multiModelField type="InstantMessageExtraData">.
 * This is a generic container model in the XML.
 */
public class InstantMessageExtraData implements Serializable {

    private static final long serialVersionUID = 7156735745956175538L;

    private final List<MessageLabel> messageLabels = new ArrayList<>();
    private ForwardedMessageData forwardedMessage;
    private QuotedMessageData quotedMessage;
    private ReplyMessageData replyMessage;

    public List<MessageLabel> getMessageLabels() {
        return messageLabels;
    }

    public QuotedMessageData getQuotedMessages() {
        return quotedMessage;
    }

    public void setQuotedMessage(QuotedMessageData quotedMessage) {
        this.quotedMessage = quotedMessage;
    }

    public ForwardedMessageData getForwardedMessages() {
        return forwardedMessage;
    }

    public void setForwardedMessage(ForwardedMessageData forwardedMessage) {
        this.forwardedMessage = forwardedMessage;
    }

    public ReplyMessageData getReplyMessage() {
        return replyMessage;
    }

    public void setReplyMessage(ReplyMessageData replyMessage) {
        this.replyMessage = replyMessage;
    }
}