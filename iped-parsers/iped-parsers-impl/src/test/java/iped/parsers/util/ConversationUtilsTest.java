package iped.parsers.util;

import static iped.parsers.util.ConversationUtils.buidPartyString;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConversationUtilsTest {

    @Test
    public void testBuidPartyString() {
        assertEquals("Bob (ID:1234|tel:5511999998888|@bobbye)", buidPartyString("Bob", "1234", "5511999998888", "bobbye", null));

        assertEquals("Bob (ID:1234|@bobbye)", buidPartyString("Bob", "1234", null, "bobbye", null));
        assertEquals("Bob (ID:1234|tel:5511999998888)", buidPartyString("Bob", "1234", "5511999998888", null, null));
        assertEquals("Bob (tel:5511999998888|@bobbye)", buidPartyString("Bob", null, "5511999998888", "bobbye", null));
        
        assertEquals("Bob (ID:1234)", buidPartyString("Bob", "1234", null, null, null));
        assertEquals("Bob (tel:5511999998888)", buidPartyString("Bob", null, "5511999998888", null, null));
        assertEquals("Bob (@bobbye)", buidPartyString("Bob", null, null, "bobbye", null));
        
        assertEquals("Bob", buidPartyString("Bob", null, null, null, null));

        assertEquals("(ID:1234|tel:5511999998888|@bobbye)", buidPartyString(null, "1234", "5511999998888", "bobbye", null));

        // for WhatsApp
        assertEquals("Bob (5511999998888@s.whatsapp.net)", buidPartyString("Bob", "5511999998888@s.whatsapp.net", null, null, "WhatsApp"));
        assertEquals("Bob (5511999998888)", buidPartyString("Bob", null, "5511999998888", null, "WhatsApp"));
        assertEquals("Bob (5511999998888@s.whatsapp.net)", buidPartyString("Bob", "5511999998888@s.whatsapp.net", "5511999998888", null, "WhatsApp"));
        assertEquals("(5511999998888)", buidPartyString("5511999998888", null, "5511999998888", null, "WhatsApp"));
    }
}
