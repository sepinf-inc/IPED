package iped.viewers.api;

public interface MessageSelector {
    void selectMessage(String messageId, boolean selected);

    boolean isMessageSelected(String messageId);
}