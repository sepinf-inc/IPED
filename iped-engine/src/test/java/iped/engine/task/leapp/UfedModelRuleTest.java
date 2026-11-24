package iped.engine.task.leapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.Test;

import iped.data.IItemReader;
import iped.search.IItemSearcher;

public class UfedModelRuleTest {

    private static final String UFDR = "case/phone.ufdr";
    private static final String DECODED = UFDR + "/_DecodedData";

    private static IItemReader item(String type, String name, String source) {
        return itemAt("application/x-ufed-" + type, DECODED + "/" + name, source);
    }

    private static IItemReader itemAt(String mediaType, String path, String source) {
        Metadata metadata = new Metadata();
        if (source != null) {
            metadata.set("ufed:Source", source);
        }
        return (IItemReader) Proxy.newProxyInstance(IItemReader.class.getClassLoader(), new Class<?>[] { IItemReader.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                    case "getMediaType":
                        return MediaType.parse(mediaType);
                    case "getPath":
                        return path;
                    case "getMetadata":
                        return metadata;
                    default:
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    /** A searcher returning the given items for any query, recording the queries it gets. */
    private static IItemSearcher searcher(List<String> queries, IItemReader... items) {
        return (IItemSearcher) Proxy.newProxyInstance(IItemSearcher.class.getClassLoader(), new Class<?>[] { IItemSearcher.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                    case "searchIterable":
                        queries.add((String) args[0]);
                        return Arrays.asList(items);
                    case "escapeQuery":
                        return ((String) args[0]).replace(":", "\\:");
                    default:
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    @Test
    public void testParse() {
        UfedModelRule anySource = UfedModelRule.parse(" Call ");
        assertEquals("Call", anySource.getType());
        assertNull(anySource.getSource());
        assertEquals("Call", anySource.toString());

        UfedModelRule withSource = UfedModelRule.parse("Chat : WhatsApp (Dual App) ");
        assertEquals("Chat", withSource.getType());
        assertEquals("WhatsApp (Dual App)", withSource.getSource());
        assertEquals("Chat:WhatsApp (Dual App)", withSource.toString());

        UfedModelRule noSource = UfedModelRule.parse("Contact:-");
        assertEquals("", noSource.getSource());
        assertEquals("Contact:-", noSource.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseWithoutType() {
        UfedModelRule.parse(":Telegram");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseWithEmptySource() {
        UfedModelRule.parse("Call:");
    }

    @Test
    public void testParseList() {
        assertEquals(Arrays.asList(new UfedModelRule("Chat", "Native Messages"), new UfedModelRule("InstantMessage", null)),
                UfedModelRule.parseList("Chat:Native Messages, InstantMessage,"));
        assertTrue(UfedModelRule.parseList(" ").isEmpty());
        assertTrue(UfedModelRule.parseList(null).isEmpty());
    }

    @Test
    public void testEqualsIgnoresCase() {
        assertEquals(UfedModelRule.parse("chat:telegram"), UfedModelRule.parse("Chat:Telegram"));
        assertEquals(UfedModelRule.parse("chat:telegram").hashCode(), UfedModelRule.parse("Chat:Telegram").hashCode());
        assertFalse(UfedModelRule.parse("Call").equals(UfedModelRule.parse("Call:-")));
    }

    @Test
    public void testQuery() {
        List<String> queries = new ArrayList<>();
        IItemSearcher searcher = searcher(queries);

        UfedModelRule.parse("Chat:Native Messages").existsIn(searcher, UFDR);
        UfedModelRule.parse("Call:-").existsIn(searcher, UFDR);

        assertEquals("contentType:\"application/x-ufed-chat\" && path:\"case/phone.ufdr/_DecodedData\""
                + " && ufed\\:Source:\"Native Messages\"", queries.get(0));
        // the absence of a source is not in the query: it is checked in each hit
        assertEquals("contentType:\"application/x-ufed-call\" && path:\"case/phone.ufdr/_DecodedData\"", queries.get(1));
    }

    @Test
    public void testAnySource() {
        UfedModelRule rule = UfedModelRule.parse("Call");
        assertTrue(rule.existsIn(searcher(new ArrayList<>(), item("call", "Call/Call_1", "WhatsApp")), UFDR));
        assertTrue(rule.existsIn(searcher(new ArrayList<>(), item("call", "Call/Call_1", null)), UFDR));
        assertFalse(rule.existsIn(searcher(new ArrayList<>()), UFDR));
    }

    @Test
    public void testSource() {
        UfedModelRule rule = UfedModelRule.parse("Chat:WhatsApp");
        assertTrue(rule.existsIn(searcher(new ArrayList<>(), item("chat", "Chat/Chat_1", "whatsapp")), UFDR));
        // the query matches the terms of the source, so it also hits sources that just contain them
        assertFalse(rule.existsIn(searcher(new ArrayList<>(), item("chat", "Chat/Chat_1", "WhatsApp Business")), UFDR));
        assertFalse(rule.existsIn(searcher(new ArrayList<>(), item("chat", "Chat/Chat_1", null)), UFDR));
    }

    @Test
    public void testNoSource() {
        UfedModelRule rule = UfedModelRule.parse("Call:-");
        assertFalse(rule.existsIn(searcher(new ArrayList<>(), item("call", "Call/Call_1", "WhatsApp")), UFDR));
        assertTrue(rule.existsIn(
                searcher(new ArrayList<>(), item("call", "Call/Call_1", "WhatsApp"), item("call", "Call/Call_2", null)), UFDR));
    }

    @Test
    public void testOtherTypeOrUfdrIsNotAMatch() {
        UfedModelRule rule = UfedModelRule.parse("Call");
        assertFalse(rule.existsIn(searcher(new ArrayList<>(), item("contact", "Contact/Contact_1", null)), UFDR));
        // the path query matches whole terms in any position: an item of another ufdr can be hit
        assertFalse(rule.existsIn(searcher(new ArrayList<>(),
                itemAt("application/x-ufed-call", "other/case/phone.ufdr/_DecodedData/Call/Call_1", null)), UFDR));
    }
}
