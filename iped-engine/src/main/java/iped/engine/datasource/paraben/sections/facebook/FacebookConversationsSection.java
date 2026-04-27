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
package iped.engine.datasource.paraben.sections.facebook;

import iped.data.ICaseData;
import iped.engine.core.Manager;
import iped.engine.data.Item;
import iped.engine.datasource.paraben.sections.ParabenSection;
import iped.properties.BasicProps;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.*;

public class FacebookConversationsSection implements ParabenSection {

    @Override
    public void process(File root, Item rootItem, List<File> xmlChain,
            ICaseData caseData,
            Map<String, List<Element>> index) throws Exception {

        for (File xml : xmlChain) {

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml);

            NodeList nodes = doc.getElementsByTagName("Node");

            for (int i = 0; i < nodes.getLength(); i++) {

                Element node = (Element) nodes.item(i);

                String title = get(node, "Title");

                // 🔥 FIX CLAVE → FILTRAR SOLO FACEBOOK
                if (!"Conversations".equalsIgnoreCase(title)
                        || !isUnderApp(node, "Facebook")) {
                    continue;
                }

                // =====================================================
                // 🔍 VALIDAR QUE HAYA CHATS REALES
                // =====================================================
                NodeList children = node.getElementsByTagName("Node");

                List<Element> validChats = new ArrayList<>();

                for (int j = 0; j < children.getLength(); j++) {

                    Element childNode = (Element) children.item(j);

                    String childTitle = get(childNode, "Title");

                    if (childTitle != null && !childTitle.trim().isEmpty() && !"0".equals(childTitle)) {
                        validChats.add(childNode);
                    }
                }

                // ❌ si no hay chats válidos → no crear nada
                if (validChats.isEmpty())
                    continue;

                // =====================================================
                // 📦 ITEM CONTENEDOR
                // =====================================================
                Item parentItem = new Item();

                parentItem.setName("Facebook Conversations");
                parentItem.setPath(rootItem.getPath() + "/Applications/Facebook/Conversations");
                parentItem.setParent(rootItem);
                parentItem.setHasChildren(true);

                parentItem.setMediaType(
                        org.apache.tika.mime.MediaType.parse("application/x-paraben-facebook-conversations"));

                parentItem.setIdInDataSource("paraben-facebook-conversations");

                parentItem.getMetadata().set(BasicProps.CATEGORY, "Applications");

                caseData.incDiscoveredEvidences(1);
                Manager.getInstance().addItemToQueue(parentItem);

                // =====================================================
                // 💬 HIJOS (chats)
                // =====================================================
                for (Element childNode : validChats) {

                    String childTitle = get(childNode, "Title");
                    String itemId = get(childNode, "ItemID");

                    Item child = new Item();

                    child.setName("Chat - " + childTitle);
                    child.setPath(parentItem.getPath() + "/" + childTitle);
                    child.setParent(parentItem);
                    child.setHasChildren(false);

                    child.setMediaType(
                            org.apache.tika.mime.MediaType.parse("application/x-facebook-chat"));

                    child.setIdInDataSource("paraben-facebook-chat-" + itemId);

                    child.getMetadata().add("facebook:chat_title", childTitle);
                    child.getMetadata().add("facebook:source", "Conversations");

                    caseData.incDiscoveredEvidences(1);
                    Manager.getInstance().addItemToQueue(child);
                }
            }
        }
    }

    // =====================================================
    // 🔥 MÉTODO NUEVO (CLAVE)
    // =====================================================
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

    // =====================================================
    private String get(Element el, String tag) {
        NodeList n = el.getElementsByTagName(tag);
        return n.getLength() > 0 ? n.item(0).getTextContent() : "";
    }
}