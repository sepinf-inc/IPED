package dpf.mg.udi.gpinf.whatsappextractor;

import static dpf.mg.udi.gpinf.whatsappextractor.Util.nullToEmpty;

import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class implements the methods hashCode and equals for message,
 * but compares only a few fields, in order to allow removal of
 * recovered deleted messages that are already present in active
 * messages.
 *
 */
public class ChatWrapperForDuplicateRemoval {
    private Chat chat;
    
    public ChatWrapperForDuplicateRemoval(Chat chat) {
        this.chat = chat;
    }
    
    private static boolean equalsWithNull(Object o1, Object o2) {
        if (o1 == null && o2 != null)
            return false;
        if (o1 != null && o2 == null)
            return false;
        if (o1 == null && o2 == null)
            return true;
        return o1.equals(o2);
    }
    
    /**
     * Used to compare messages. 
     * The field 'deleted' is not included in the calculation of the hashCode, so a deleted
     * message and an active message with the same content will be seen as equals in this
     * implementation.
     * 
     * @return hashCode
     */
    @Override
    public int hashCode() {
        var builder = new HashCodeBuilder(19, 53);
        
        builder.append(nullToEmpty(chat.getSubject()))
               .append(chat.getRemote().getId());
        
        return builder.toHashCode();
    }
    
    @Override
    public boolean equals(Object otherObj) {
        ChatWrapperForDuplicateRemoval c2 = (ChatWrapperForDuplicateRemoval) otherObj;
        
        if (!equalsWithNull(chat.getSubject(), c2.chat.getSubject())) 
            return false;
        
        if (!chat.getRemote().getId().equals(c2.chat.getRemote().getId()))
            return false;
        
        return true;
    }
}
