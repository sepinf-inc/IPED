package iped.parsers.whatsapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class WAAccountTest {

    private static WAAccount parse(String xml) throws Exception {
        try (InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            return WAAccount.getFromAndroidXml(is);
        }
    }

    @Test
    public void testRegistrationJidFromTestFile() throws Exception {
        try (InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("test-files/test_whatsAppPreferences.xml")) {
            WAAccount account = WAAccount.getFromAndroidXml(is);
            assertNotNull(account);
            // registration_jid holds only the number, the suffix must be added
            assertEquals("554499887766@s.whatsapp.net", account.getFullId());
            assertEquals("554499887766", account.getId());
        }
    }

    @Test
    public void testRegistrationJidWithSuffix() throws Exception {
        WAAccount account = parse("<map><string name=\"registration_jid\">554499887766@s.whatsapp.net</string></map>");
        assertEquals("554499887766@s.whatsapp.net", account.getFullId());
    }

    @Test
    public void testCountryCodeAndPhone() throws Exception {
        WAAccount account = parse(
                "<map><string name=\"cc\">55</string><string name=\"ph\">4499887766</string></map>");
        assertEquals("554499887766@s.whatsapp.net", account.getFullId());
    }

    @Test
    public void testSelfUsername() throws Exception {
        WAAccount account = parse("<map><string name=\"registration_jid\">55AA</string>"
                + "<string name=\"self_user_name\">example.user</string></map>");
        assertEquals("example.user", account.getUsername());

        // only the username is enough to return the account info
        account = parse("<map><string name=\"self_user_name\">@example.user</string></map>");
        assertEquals("example.user", account.getUsername());
    }

    @Test
    public void testOwnLid() throws Exception {
        WAAccount account = parse("<map><string name=\"registration_jid\">55AA</string>"
                + "<string name=\"self_lid\">123456789@lid</string></map>");
        assertEquals("123456789@lid", account.getLid());

        // registration_lid has only the number
        account = parse("<map><string name=\"registration_jid\">55AA</string>"
                + "<string name=\"registration_lid\">123456789</string></map>");
        assertEquals("123456789@lid", account.getLid());
    }
}
