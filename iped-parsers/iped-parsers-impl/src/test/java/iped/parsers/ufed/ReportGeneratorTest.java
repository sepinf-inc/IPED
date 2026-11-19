package iped.parsers.ufed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import iped.parsers.ufed.model.Chat;

public class ReportGeneratorTest {

    private static final int LARGE_SPLIT_SIZE = 6_000_000;

    private static String generateFullHtml(Chat chat, int minChatSplitSize) throws Exception {
        ReportGenerator generator = new ReportGenerator(chat, minChatSplitSize);
        StringBuilder html = new StringBuilder();
        byte[] bytes;
        while ((bytes = generator.generateNextChatHtml()) != null) {
            html.append(new String(bytes, StandardCharsets.UTF_8));
        }
        return html.toString();
    }

    @Test
    public void testGeneratesNonNullHtmlForNonEmptyChat() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        ReportGenerator generator = new ReportGenerator(chat, LARGE_SPLIT_SIZE);
        assertNotNull("First call must return the chat HTML", generator.generateNextChatHtml());
    }

    @Test
    public void testHtmlContainsMessageSenderName() throws Exception {
        // Checks the per-message sender span specifically (not just the chat
        // header, which also happens to show the participant's name for a
        // one-on-one chat and would make this assertion pass vacuously)
        String html = generateFullHtml(UfedChatFixtures.buildSampleChat(), LARGE_SPLIT_SIZE);
        assertTrue("Rendered HTML must attribute the message to its sender",
                html.contains("<span class=\"name\">" + UfedChatFixtures.ALICE_NAME));
    }

    @Test
    public void testHtmlContainsMessageBody() throws Exception {
        String html = generateFullHtml(UfedChatFixtures.buildSampleChat(), LARGE_SPLIT_SIZE);
        assertTrue("Rendered HTML must contain the message body", html.contains(UfedChatFixtures.MSG1_BODY));
    }

    @Test
    public void testHtmlContainsSharedContactName() throws Exception {
        String html = generateFullHtml(UfedChatFixtures.buildSampleChat(), LARGE_SPLIT_SIZE);
        assertTrue("Rendered HTML must contain the shared contact's name",
                html.contains(UfedChatFixtures.SHARED_CONTACT_NAME));
    }

    @Test
    public void testHtmlContainsAttachmentUrl() throws Exception {
        String html = generateFullHtml(UfedChatFixtures.buildSampleChat(), LARGE_SPLIT_SIZE);
        assertTrue("Rendered HTML must contain the attachment URL", html.contains(UfedChatFixtures.ATTACHMENT_URL));
    }

    @Test
    public void testMixedCaseContentTypeIsDispatchedAsImage() throws Exception {
        // ContentType matching is case-insensitive; a mixed-case value must still
        // route to the image-specific rendering branch, not the generic fallback
        Chat chat = UfedChatFixtures.buildChatWithSingleAttachment("IMAGE/JPEG");
        String html = generateFullHtml(chat, LARGE_SPLIT_SIZE);
        assertTrue("A mixed-case 'IMAGE/JPEG' content type must render through the image branch",
                html.contains("class=\"imageImg\""));
    }

    @Test
    public void testEmptyChatProducesOnlyOneFragment() throws Exception {
        Chat chat = new Chat();
        chat.setId("empty-chat");
        chat.setField("Source", Chat.SOURCE_WHATSAPP);
        ReportGenerator generator = new ReportGenerator(chat, LARGE_SPLIT_SIZE);

        assertNotNull("An empty chat must still produce a header/footer fragment", generator.generateNextChatHtml());
        assertNull("A second call on an empty chat must return null", generator.generateNextChatHtml());
    }

    @Test
    public void testGetNextMsgNumReachesTotalAfterFullChat() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        ReportGenerator generator = new ReportGenerator(chat, LARGE_SPLIT_SIZE);
        generator.generateNextChatHtml();
        assertEquals("All messages must be consumed in a single large-split-size fragment",
                chat.getMessages().size(), generator.getNextMsgNum());
        assertNull("No more fragments must remain", generator.generateNextChatHtml());
    }

    @Test
    public void testSmallSplitSizeSplitsOneMessagePerFragment() throws Exception {
        Chat chat = UfedChatFixtures.buildSampleChat();
        ReportGenerator generator = new ReportGenerator(chat, 1);

        List<byte[]> fragments = new ArrayList<>();
        byte[] bytes;
        while ((bytes = generator.generateNextChatHtml()) != null) {
            fragments.add(bytes);
        }

        assertEquals("With minChatSplitSize=1, each fragment must hold exactly one message",
                chat.getMessages().size(), fragments.size());
    }
}
