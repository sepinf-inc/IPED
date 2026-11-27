package iped.parsers.chat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WhatsAppPartyStringBuilderTest {

    private static String build(String name, String userId, String phone, String username) {
        return new WhatsAppPartyStringBuilder().withName(name).withUserId(userId).withPhoneNumber(phone)
                .withUsername(username).build();
    }

    @Test
    public void testNameAndPhone() {
        assertEquals("Maria (+5561999990000)", build("Maria", "5561999990000@s.whatsapp.net", null, null));
    }

    @Test
    public void testNameUsernameAndPhone() {
        assertEquals("Maria (@maria.s | +5561999990000)",
                build("Maria", "5561999990000@s.whatsapp.net", "+5561999990000", "maria.s"));
    }

    @Test
    public void testNameIsThePhone() {
        assertEquals("+5561999990000", build("5561999990000", "5561999990000@s.whatsapp.net", null, null));
        assertEquals("+5561999990000 (@maria.s)",
                build("5561999990000", "5561999990000@s.whatsapp.net", null, "@maria.s"));
    }

    @Test
    public void testLidWithUsername() {
        // LIDs are not phone numbers
        assertEquals("Maria (@maria.s)", build("Maria", "123456789012345@lid", null, "maria.s"));
        assertEquals("@maria.s", build(null, "123456789012345@lid", null, "maria.s"));
    }

    @Test
    public void testOnlyPhone() {
        assertEquals("+5561999990000", build(null, "5561999990000@s.whatsapp.net", null, null));
    }
}
