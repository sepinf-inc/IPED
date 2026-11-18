/**
 * Paraben Data Parser Integration for IPED
 *
 * Developed by: Ariel E Aburo
 * Contact: infmpfch@gmail.com
 *
 * Description:
 * Implements parsing of data extracted via Paraben tools,
 * converting XML and associated database content into
 * IPED-compatible structures.
 *
 * This module supports multiple artifacts such as:
 * - Conversations
 * - Messages
 * - Attachments
 * - Device and user data and more.
 *
 * Notes:
 * - Uses XML as primary source
 * - Designed to be extensible for additional Paraben artifacts
 */
package iped.engine.datasource.paraben.sections.facebookmess;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.engine.datasource.paraben.utils.ParabenDateUtil;
import iped.engine.task.ExportFileTask;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.*;
import iped.engine.datasource.paraben.util.HtmlTemplate;

public class FacebookMessengerConversationsSection implements ParabenSection {
    Set<String> createdAccounts = new HashSet<>();

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        for (File xml : xmlChain) {

            Map<String, List<HtmlTemplate.MessageBubble>> chatMap = new HashMap<>();

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String title = get(node, "Title");

                if (!"Conversations".equalsIgnoreCase(title)
                        || !isUnderApp(node, "Facebook Messenger")) {
                    continue;
                }

                String ownerId = extractOwnerId(index);

                if (ownerId != null && !createdAccounts.contains(ownerId)) {

                    createdAccounts.add(ownerId);

                    Item account = new Item();

                    account.setName("Facebook Account - " + ownerId);
                    account.setPath(rootItem.getPath() + "/Applications/Facebook/Account");
                    account.setParent(rootItem);
                    account.setHasChildren(false);

                    account.setMediaType(
                            org.apache.tika.mime.MediaType.parse("application/x-paraben-facebook-account"));

                    account.setIdInDataSource("paraben-fb-account-" + ownerId);

                    account.getMetadata().set(BasicProps.CATEGORY, "Applications");
                    account.getMetadata().set(BasicProps.NAME, ownerId);
                    account.getMetadata().add(BasicProps.ID, ownerId);

                    account.getMetadata().add(ExtraProperties.USER_ACCOUNT, ownerId);

                    account.getMetadata().set("facebook:user_id", ownerId);
                    account.getMetadata().set("facebook:account_owner", "true");

                    caseData.incDiscoveredEvidences(1);
                    Manager.getInstance().addItemToQueue(account);
                }

                Item rootConv = new Item();

                rootConv.setName("Facebook Messenger Conversations");
                rootConv.setPath(rootItem.getPath() + "/Applications/Facebook Messenger/Conversations");
                rootConv.setParent(rootItem);
                rootConv.setHasChildren(true);

                rootConv.setMediaType(
                        org.apache.tika.mime.MediaType.parse("application/x-paraben-facebook-messenger-conversations"));

                rootConv.setIdInDataSource("paraben-fb-messenger-conversations");

                rootConv.getMetadata().set(BasicProps.TYPE, "Facebook Messenger");

                caseData.incDiscoveredEvidences(1);
                Manager.getInstance().addItemToQueue(rootConv);

                NodeList chats = node.getElementsByTagName("Node");

                for (int j = 0; j < chats.getLength(); j++) {

                    Element chatNode = (Element) chats.item(j);

                    String type = getAttr(chatNode, "xsi:type");
                    if (!"GridValue".equals(type))
                        continue;

                    String chatId = get(chatNode, "Title");

                    Item chatItem = new Item();

                    chatItem.setName("Chat - " + chatId);
                    chatItem.setPath(rootConv.getPath() + "/" + chatId);
                    chatItem.setParent(rootConv);
                    chatItem.setHasChildren(true);

                    chatItem.setMediaType(
                            org.apache.tika.mime.MediaType.parse("application/x-paraben-facebook-messenger-chat"));

                    chatItem.setIdInDataSource("paraben-fb-chat-" + chatId);

                    chatItem.getMetadata().set(BasicProps.NAME, chatId);
                    chatItem.getMetadata().set(BasicProps.TYPE, "Chat");

                    chatItem.getMetadata().add("facebook:chat_id", chatId);
                    chatItem.getMetadata().add(ExtraProperties.CONVERSATION_ID, chatId);

                    caseData.incDiscoveredEvidences(1);
                    Manager.getInstance().addItemToQueue(chatItem);

                    NodeList rows = chatNode.getElementsByTagName("Row");

                    for (int k = 0; k < rows.getLength(); k++) {

                        Element row = (Element) rows.item(k);

                        Map<String, String> data = extractRow(row);
                        System.out.println("DEBUG FB MSG: " + data);
                        String time = data.get("Time");
                        String epoch = data.get("Time_tag");
                        String sender = data.get("Sender Name");
                        String text = data.get("Full Text");

                        if (isEmpty(text))
                            text = data.get("Text Preview");
                        if (isEmpty(text))
                            text = data.get("Message");
                        if (isEmpty(text))
                            text = data.get("Body");
                        if (isEmpty(text))
                            text = data.get("Snippet");
                        if (isEmpty(text))
                            text = data.get("Attachment Title");
                        String fullLink = data.get("Full Text_link");
                        HtmlTemplate.MessageBubble bubble = new HtmlTemplate.MessageBubble();
                        bubble.sender = sender;
                        bubble.senderId = data.get("Sender ID");
                        bubble.text = (text != null && !text.trim().isEmpty())
                                ? text
                                : "[No Text]";
                        bubble.time = time;
                        bubble.isSystem = "Yes".equalsIgnoreCase(data.get("Is Admin Message"));

                        chatMap.computeIfAbsent(chatId, key -> new ArrayList<>()).add(bubble);

                        Item msg = new Item();
                        String senderId = data.get("Sender ID");

                        boolean isSystem = "Yes".equalsIgnoreCase(data.get("Is Admin Message"));

                        String role;

                        if (isSystem) {
                            role = "System";
                        } else if (ownerId != null && ownerId.equals(senderId)) {
                            role = "Me";
                        } else {
                            role = "Other";
                        }

                        String displaySender = sender;

                        if (isSystem) {
                            displaySender = "System Message";
                        }

                        msg.setName(
                                "[" + role + "] " + (displaySender != null ? displaySender : "Unknown") + " @ " + time);
                        msg.setPath(chatItem.getPath() + "/" + msg.getName());
                        msg.setParent(chatItem);
                        msg.setHasChildren(false);

                        msg.setMediaType(
                                org.apache.tika.mime.MediaType
                                        .parse("application/x-paraben-facebook-messenger-message"));

                        msg.setIdInDataSource("paraben-fb-msg-" + data.get("Message ID"));

                        Map<String, String> htmlData = new LinkedHashMap<>(data);

                        if (text != null && !text.trim().isEmpty()) {
                            htmlData.put("Message", text);
                        }

                        String html = HtmlTemplate.buildTable("Messenger Message", htmlData);
                        byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                        ExportFileTask.getLastInstance()
                                .insertIntoStorage(msg, bytes, bytes.length);

                        msg.setLength((long) bytes.length);

                        msg.getMetadata().set(BasicProps.CATEGORY, "Chat");

                        msg.getMetadata().set(BasicProps.NAME,
                                sender != null ? sender : "Unknown");

                        msg.getMetadata().set(BasicProps.TYPE, "Message");

                        msg.getMetadata().set(BasicProps.CONTENT,
                                text != null ? text : "");

                        msg.getMetadata().add("facebook:sender", sender);
                        msg.getMetadata().add("facebook:sender_id", data.get("Sender ID"));

                        if (sender != null) {
                            msg.getMetadata().add(ExtraProperties.COMMUNICATION_FROM, sender);
                        }

                        if (ownerId != null) {
                            msg.getMetadata().add(ExtraProperties.COMMUNICATION_TO, ownerId);
                        }

                        String epochMsg = data.get("Time_tag");
                        if (epochMsg != null) {
                            Date d = ParabenDateUtil.fromEpoch(epochMsg);
                            if (d != null) {
                                msg.setCreationDate(d);
                                msg.getMetadata().set(ExtraProperties.COMMUNICATION_DATE, d);
                            }
                        }
                        if (isSystem) {
                            msg.getMetadata().add("message:type", "system");
                            msg.getMetadata().add("facebook:role", "system");
                        } else if (ownerId != null && ownerId.equals(senderId)) {
                            msg.getMetadata().add("facebook:role", "owner");
                        } else {
                            msg.getMetadata().add("facebook:role", "contact");
                        }
                        msg.getMetadata().add("facebook:message_id", data.get("Message ID"));
                        msg.getMetadata().add("entity:type", "message");
                        if ("Yes".equalsIgnoreCase(data.get("Is Unsent"))) {
                            msg.getMetadata().add("facebook:unsent", "true");
                        }

                        caseData.incDiscoveredEvidences(1);
                        Manager.getInstance().addItemToQueue(msg);

                        if (fullLink != null && !fullLink.isEmpty()) {

                            File f = new File(root, fullLink);

                            if (f.exists()) {

                                Item attach = new Item();

                                attach.setName("Attachment - " + f.getName());
                                attach.setPath(msg.getPath() + "/" + f.getName());
                                attach.setParent(msg);

                                attach.setMediaType(
                                        org.apache.tika.mime.MediaType
                                                .parse("application/x-paraben-facebook-messenger-attachment"));

                                attach.setIdInDataSource(f.getAbsolutePath());

                                attach.setInputStreamFactory(
                                        new iped.utils.FileInputStreamFactory(f.toPath()));

                                attach.setLength(f.length());
                                attach.getMetadata().set(BasicProps.LENGTH, String.valueOf(f.length()));
                                attach.getMetadata().set(BasicProps.TYPE, "Attachment");

                                attach.getMetadata().add("entity:type", "attachment");
                                attach.getMetadata().add("facebook:attachment", "true");
                                caseData.incDiscoveredEvidences(1);
                                Manager.getInstance().addItemToQueue(attach);
                            }
                        }
                    }

                    List<HtmlTemplate.MessageBubble> bubbles = chatMap.get(chatId);

                    if (bubbles != null && !bubbles.isEmpty()) {

                        String chatHtml = HtmlTemplate.buildFacebookChat(
                                "Chat " + chatId,
                                bubbles,
                                ownerId);

                        Item chatHtmlItem = new Item();

                        chatHtmlItem.setName("Chat View - " + chatId);
                        chatHtmlItem.setPath(chatItem.getPath() + "/Chat View");
                        chatHtmlItem.setParent(chatItem);

                        chatHtmlItem.setMediaType(
                                org.apache.tika.mime.MediaType
                                        .parse("application/x-paraben-facebook-messenger-chat-html"));

                        byte[] bytes = chatHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                        ExportFileTask.getLastInstance()
                                .insertIntoStorage(chatHtmlItem, bytes, bytes.length);

                        chatHtmlItem.setLength((long) bytes.length);
                        chatHtmlItem.getMetadata().set(BasicProps.LENGTH, String.valueOf(bytes.length));
                        chatHtmlItem.getMetadata().set(BasicProps.TYPE, "ChatView");
                        chatHtmlItem.getMetadata().add("entity:type", "chat_html");
                        caseData.incDiscoveredEvidences(1);
                        Manager.getInstance().addItemToQueue(chatHtmlItem);
                    }
                }
            }
        }
    }

    private String extractOwnerId(Map<String, List<Element>> index) {

        List<Element> nodes = index.get("Recovered Settings");

        if (nodes == null)
            return null;

        for (Element node : nodes) {

            NodeList rows = node.getElementsByTagName("Row");

            for (int i = 0; i < rows.getLength(); i++) {

                Element row = (Element) rows.item(i);
                Element values = getFirst(row, "Values");

                if (values == null)
                    continue;

                NodeList cells = values.getElementsByTagName("Cell");

                for (int j = 0; j < cells.getLength(); j++) {

                    Element cell = (Element) cells.item(j);

                    String column = get(cell, "Column");
                    String value = get(cell, "Value");

                    if ("Key".equals(column) && value.contains("logged_in_user_scoped")) {

                        String[] parts = value.split("/");

                        for (String p : parts) {
                            if (p.matches("\\d{5,}")) {
                                return p;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private Map<String, String> extractRow(Element row) {

        Map<String, String> map = new HashMap<>();

        NodeList cells = row.getElementsByTagName("Cell");

        for (int i = 0; i < cells.getLength(); i++) {

            Element cell = (Element) cells.item(i);

            String col = get(cell, "Column");
            String val = get(cell, "Value");
            String tag = get(cell, "Tag");
            String link = getOptional(cell, "Link");

            map.put(col, val);

            if (tag != null && !tag.isEmpty()) {
                map.put(col + "_tag", tag);
            }

            if (link != null) {
                map.put(col + "_link", link);
            }
        }

        return map;
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty() || "None".equalsIgnoreCase(s);
    }

    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }

    private String getOptional(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return (n != null && n.getLength() > 0) ? n.item(0).getTextContent() : null;
    }

    private Element getFirst(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? (Element) n.item(0) : null;
    }

    private String getAttr(Element el, String attr) {
        return el.hasAttribute(attr) ? el.getAttribute(attr) : "";
    }

    private boolean isUnderApp(Element node, String appName) {

        Node current = node;

        while (current != null && current instanceof Element) {

            Element el = (Element) current;
            String title = get(el, "Title");

            if (appName.equalsIgnoreCase(title)) {
                return true;
            }

            current = current.getParentNode();
        }

        return false;
    }
}