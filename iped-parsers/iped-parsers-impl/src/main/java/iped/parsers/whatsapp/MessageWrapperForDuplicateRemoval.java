package iped.parsers.whatsapp;

import static iped.parsers.whatsapp.Util.nullToEmpty;

import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class implements the methods hashCode and equals for message,
 * but compares only a few fields, in order to allow removal of
 * recovered deleted messages that are already present in active
 * messages.
 *
 */
public class MessageWrapperForDuplicateRemoval {
    private Message message;
    
    public MessageWrapperForDuplicateRemoval(Message message) {
        this.message = message;
    }
    
    private static boolean equalsWithNull(Object o1, Object o2) {
        if (o1 == null && o2 != null)
            return false;
        if (o1 != null && o2 == null)
            return false;
        if (o1 == o2)
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
        
        builder.append(nullToEmpty(message.getData()))
               .append(message.getTimeStamp())
               .append(nullToEmpty(message.getMediaHash()))
               .append(message.getMessageStatus());
        
        return builder.toHashCode();
    }
    
    @Override
    public boolean equals(Object otherObj) {
        MessageWrapperForDuplicateRemoval m2 = (MessageWrapperForDuplicateRemoval) otherObj;
        
        if (!equalsWithNull(message.getData(), m2.message.getData())) 
            return false;
        
        if (!message.getTimeStamp().equals(m2.message.getTimeStamp()))
            return false;
        
        if (!equalsWithNull(message.getMediaHash(), m2.message.getMediaHash()))
            return false;
        
        if (!equalsWithNull(message.getMessageType(), m2.message.getMessageType()))
            return false;
        
        return true;
    }
}