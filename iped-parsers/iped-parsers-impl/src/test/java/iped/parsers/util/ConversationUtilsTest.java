package iped.parsers.util;

import static iped.parsers.util.ConversationUtils.buidPartyString;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConversationUtilsTest {

    @Test
    public void test() {
        assertEquals("Bob (ID:1234|tel:5511999998888|@bobbye)", buidPartyString("Bob", "1234", "5511999998888", "bobbye"));

        assertEquals("Bob (ID:1234|@bobbye)", buidPartyString("Bob", "1234", null, "bobbye"));
        assertEquals("Bob (ID:1234|tel:5511999998888)", buidPartyString("Bob", "1234", "5511999998888", null));
        assertEquals("Bob (tel:5511999998888|@bobbye)", buidPartyString("Bob", null, "5511999998888", "bobbye"));
        
        assertEquals("Bob (ID:1234)", buidPartyString("Bob", "1234", null, null));
        assertEquals("Bob (tel:5511999998888)", buidPartyString("Bob", null, "5511999998888", null));
        assertEquals("Bob (@bobbye)", buidPartyString("Bob", null, null, "bobbye"));
        
        assertEquals("Bob", buidPartyString("Bob", null, null, null));
        
        assertEquals("(ID:1234|tel:5511999998888|@bobbye)", buidPartyString(null, "1234", "5511999998888", "bobbye"));
    }

}
