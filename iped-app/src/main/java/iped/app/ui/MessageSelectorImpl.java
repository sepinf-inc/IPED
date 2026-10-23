package iped.app.ui;

import iped.data.IItemId;
import iped.viewers.api.MessageSelector;

public class MessageSelectorImpl implements MessageSelector {
    @Override
    public void selectMessage(String messageId, boolean selected) {
        IItemId itemId = getItemId();
        if (itemId != null) {
            App.get().appCase.getMultiBookmarks().setChatMessageSelected(selected, itemId, messageId);
            App.get().appCase.getMultiBookmarks().saveState();
        }
    }

    @Override
    public boolean isMessageSelected(String messageId) {
        IItemId itemId = getItemId();
        if (itemId != null) {
            return App.get().appCase.getMultiBookmarks().isChatMessageSelected(itemId, messageId);
        }
        return false;
    }

    private IItemId getItemId() {
        int docId = App.get().getLastValidSelectedDoc();
        if (docId >= 0) {
            return App.get().appCase.getItemId(docId);
        }
        return null;
    }
}
