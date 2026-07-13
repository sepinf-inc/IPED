package iped.engine.datasource.ufed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.ContactEntry;
import iped.parsers.ufed.model.KeyValueModel;
import iped.parsers.ufed.model.UserAccount;

public class UfedModelHandlerUserAccountTest {

    private static List<UserAccount> parsedAccounts;

    @BeforeClass
    public static void setUp() throws Exception {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        final List<BaseModel> models = new ArrayList<>();
        UfedModelHandler.UfedModelListener listener = new UfedModelHandler.UfedModelListener() {
            @Override
            public void onModelStarted(BaseModel model, Attributes attr) {
                // Not used in this test
            }
            @Override
            public void onModelCompleted(BaseModel model) {
                models.add(model);
            }
        };

        // We use a dummy parent handler for this standalone test
        UfedModelHandler handler = new UfedModelHandler(saxParser.getXMLReader(), new DefaultHandler(), listener);

        InputStream xmlFile = UfedModelHandlerChatTest.class.getResourceAsStream("/ufed-model-useraccount.xml");

        saxParser.parse(xmlFile, handler);

        parsedAccounts = models.stream()
                .filter(UserAccount.class::isInstance)
                .map(UserAccount.class::cast)
                .collect(Collectors.toList());
    }

    @Test
    public void testUserAccountCount() {
        assertNotNull("Parsed accounts list should not be null", parsedAccounts);
        assertEquals("Should parse 2 user accounts", 2, parsedAccounts.size());
    }

    @Test
    public void testWhatsAppBusinessAccount() {
        UserAccount whatsAppAccount = parsedAccounts.stream()
                .filter(acc -> "df377551-0743-4035-8dc8-fbc915839527".equals(acc.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull("WhatsApp Business account should be found", whatsAppAccount);
        assertEquals("Source should be WhatsApp Business", "WhatsApp Business", whatsAppAccount.getSource());
        assertEquals("Name should be Rattata", "Rattata", whatsAppAccount.getName());

        // Test Photos
        assertEquals("Should have 1 photo", 1, whatsAppAccount.getPhotos().size());
        assertEquals("Photo name should be correct", "Photo.jpg", whatsAppAccount.getPhotos().get(0).getName());

        // Test Entries
        assertEquals("Should have 1 contact entries", 1, whatsAppAccount.getContactEntries().size());
        assertEquals("Should have 3 UserID", 3, whatsAppAccount.getContactEntries().get("UserID").size());
        Optional<ContactEntry> userIdEntry = whatsAppAccount.getUserID().map(list -> list.get(0));
        assertTrue("UserID entry should exist", userIdEntry.isPresent());
        assertEquals("UserID value should be correct", "5511999997777@s.whatsapp.net", userIdEntry.get().getValue());

        // Test Additional Info
        List<KeyValueModel> additionalInfo = whatsAppAccount.getAdditionalInfo();
        assertFalse("AdditionalInfo should not be empty", additionalInfo.isEmpty());
        assertEquals("Should have 2 additional info items", 2, additionalInfo.size());

        Optional<KeyValueModel> aboutInfo = additionalInfo.stream().filter(kv -> "About".equals(kv.getKey())).findFirst();
        assertTrue("About info should exist", aboutInfo.isPresent());
        assertEquals("About value should be correct", "Olá! Eu estou usando o WhatsApp.", aboutInfo.get().getValue());
    }

    @Test
    public void testInstagramAccount() {
        UserAccount instagramAccount = parsedAccounts.stream()
                .filter(acc -> "9628c161-85c6-45e5-9f1f-775bc36996b3".equals(acc.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull("Instagram account should be found", instagramAccount);
        assertEquals("Source should be Instagram", "Instagram", instagramAccount.getSource());
        assertEquals("Name should be Pidgeotto", "Pidgeotto", instagramAccount.getName());

        // Test Photos
        assertEquals("Should have 1 photo model", 1, instagramAccount.getPhotos().size());

        // Test Entries
        assertEquals("Should have 2 contact entries", 2, instagramAccount.getContactEntries().size());
        assertEquals("Should have 2 ProfilePicture", 2, instagramAccount.getContactEntries().get("ProfilePicture").size());
        assertEquals("Should have 1 UserID", 1, instagramAccount.getContactEntries().get("UserID").size());

        Optional<ContactEntry> facebookIdEntry = instagramAccount.getContactEntries().values().stream()
                .flatMap(List::stream)
                .filter(e -> "Facebook Id".equals(e.getCategory()))
                .findFirst();
        assertTrue("Facebook Id entry should exist", facebookIdEntry.isPresent());
        assertEquals("Facebook Id value should be correct", "1212121212121212", facebookIdEntry.get().getValue());

        long profilePictureCount = instagramAccount.getContactEntries().values().stream()
                .flatMap(List::stream)
                .filter(e -> "Profile Picture".equals(e.getDomain()))
                .count();
        assertEquals("Should have 2 Profile Picture entries", 2, profilePictureCount);
    }

}
