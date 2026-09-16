package iped.engine.datasource.ufed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Arrays;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.ufed.model.BaseModel;
import iped.parsers.ufed.model.Chat;
import iped.parsers.ufed.model.InstantMessage;

/**
 * PA 10.10+ moved message labels (Forwarded, Reply, Edited...) to a
 * &lt;multiField name="Labels"&gt; in InstantMessage.
 */
public class UfedModelHandlerLabelsTest {

    private static Chat chat;

    @BeforeClass
    public static void setUp() throws Exception {
        SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        UfedModelHandler handler = new UfedModelHandler(xmlReader, new DefaultHandler(), new UfedModelHandler.UfedModelListener() {
            @Override
            public void onModelStarted(BaseModel model, Attributes attr) {
            }

            @Override
            public void onModelCompleted(BaseModel model) {
                chat = (Chat) model;
            }
        });
        try (InputStream is = UfedModelHandlerLabelsTest.class.getResourceAsStream("/ufed-model-chat-pa1010-labels.xml")) {
            saxParser.parse(is, handler);
        }
    }

    @Test
    public void testForwardedMessageLabels() {
        InstantMessage fwd = chat.findMessageByIdentifier("FWD-1");
        assertNotNull(fwd);
        assertEquals(Arrays.asList("Forwarded", "Edited"), fwd.getLabels());
        assertTrue(fwd.isForwardedMessage());
        assertTrue(fwd.isEdited());
        assertFalse(fwd.isReplyMessage());
        assertEquals("Default", fwd.getLabel());
        // multiField values must not leak into the parent field ("Messages")
        assertNull(fwd.getField("Messages"));
    }

    @Test
    public void testEmbeddedMessageHasNoLabels() {
        InstantMessage embedded = chat.findMessageByIdentifier("FWD-1").getEmbeddedMessage().orElse(null);
        assertNotNull(embedded);
        assertTrue(embedded.getLabels().isEmpty());
        assertFalse(embedded.isForwardedMessage());
    }

    @Test
    public void testForwardedMessageFoundByPaId() {
        InstantMessage fwd = chat.findMessageByIdentifier("FWD-1");
        InstantMessage embedded = fwd.getEmbeddedMessage().orElse(null);
        assertNotNull(embedded);
        // QuotedMessageData.ReferenceId points to the embedded message "pa_id", not to its "id"
        assertEquals("pa-embedded", embedded.getPaId());
        assertSame(embedded, fwd.findForwardedMessage(chat));
    }

    @Test
    public void testChatMessageFoundByIdAndPaId() {
        InstantMessage fwd = chat.findMessageByIdentifier("FWD-1");
        assertSame(fwd, chat.findMessageByUfedId("msg-fwd"));
        assertSame(fwd, chat.findMessageByUfedId("pa-msg-fwd"));
    }

    @Test
    public void testReplyMessage() {
        InstantMessage reply = chat.findMessageByIdentifier("REPLY-1");
        assertNotNull(reply);
        assertTrue(reply.isReplyMessage());
        assertFalse(reply.isForwardedMessage());
        assertEquals("FWD-1", reply.findReplyMessage(chat).getIdentifier());
    }
}
