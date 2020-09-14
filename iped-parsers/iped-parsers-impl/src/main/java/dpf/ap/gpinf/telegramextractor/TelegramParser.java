/*
 * Copyright 2020-2020, João Vitor de Sá Hauck
 * 
 * This file is part of Indexador e Processador de Evidencias Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.ap.gpinf.telegramextractor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties; 

public class TelegramParser extends SQLite3DBParser {

    /**
     * 
     */
    private static final long serialVersionUID = -2981296547291818873L;

    public static final String TELEGRAM = "Telegram";
    public static final MediaType TELEGRAM_ACCOUNT = MediaType.parse("application/x-telegram-account");
    public static final MediaType TELEGRAM_USER_CONF = MediaType.parse("application/x-telegram-user-conf");
    public static final MediaType TELEGRAM_DB = MediaType.parse("application/x-telegram-db");
    public static final MediaType TELEGRAM_DB_IOS = MediaType.parse("application/x-telegram-db-ios");
    public static final MediaType TELEGRAM_CHAT = MediaType.parse("application/x-telegram-chat");
    public static final MediaType TELEGRAM_CONTACT = MediaType.parse("contact/x-telegram-contact");
    public static final MediaType TELEGRAM_MESSAGE = MediaType.parse("message/x-telegram-message");
    public static final MediaType TELEGRAM_ATTACHMENT = MediaType.parse("message/x-telegram-attachment");
    public static final MediaType TELEGRAM_CALL = MediaType.parse("call/x-telegram-call");

    // TODO improve this: prefix to show 'attachment' before body text (values
    // are sorted)
    private static final String ATTACHMENT_PREFIX = "! ";

    // TODO externalize to locale properties
    private static final String ATTACHMENT_MESSAGE = ATTACHMENT_PREFIX + "Attachment: ";

    private boolean extractMessages = true;

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set(TELEGRAM_DB,TELEGRAM_USER_CONF,TELEGRAM_DB_IOS);
    }
    
    @Field
    public void setExtractMessages(boolean extractMessages) {
        this.extractMessages = extractMessages;
    }

    private void storeLinkedHashes(List<Message> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMediaHash() != null) {
                metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + m.getMediaHash()); //$NON-NLS-1$
                if (m.isFromMe())
                    metadata.add(ExtraProperties.SHARED_HASHES, m.getMediaHash());

            }
        }
    }
    
    public void parseTelegramDBAndroid(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
    	try (Connection conn = getConnection(stream, metadata, context)) {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            Extractor e = new Extractor(conn);
            e.setSearcher(searcher);
            e.extractContacts();
            ReportGenerator r = new ReportGenerator(searcher);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (e.getContacts() != null) {
                for (Contact c : e.getContacts().values()) {
                    byte[] bytes = r.genarateContactHtml(c);
                    Metadata cMetadata = new Metadata();
                    cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CONTACT.toString());
                    cMetadata.set(TikaCoreProperties.TITLE, c.getName());
                    cMetadata.set(ExtraProperties.USER_NAME, c.getName());
                    cMetadata.set(ExtraProperties.USER_PHONE, c.getPhone());
                    cMetadata.set(ExtraProperties.USER_ACCOUNT, c.getId() + "");
                    cMetadata.set(ExtraProperties.USER_ACCOUNT_TYPE, TELEGRAM);
                    cMetadata.set(ExtraProperties.USER_NOTES, c.getUsername());
                    if (c.getAvatar() != null) {
                        cMetadata.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(c.getAvatar()));
                    }
                    ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(contactStream, handler, cMetadata, false);
                }
            }

            String dbPath = ((ItemInfo) context.get(ItemInfo.class)).getPath();
            Contact account = searchAndroidAccount(searcher, dbPath);

            e.extractChatList();
            for (Chat c : e.getChatList()) {
                c.getMessages().addAll(e.extractMessages(c));
                if (e.getUserAccount() != null) {
                    account = e.getUserAccount();
                }
                generateChat(c, account, e.getContacts(), searcher, handler, extractor);
            }

        } catch (Exception e1) {
            e1.printStackTrace();
            throw new TikaException("Error parsing telegram database", e1);
        }
    	
    }
    
    private void generateChat(Chat c, Contact account, Map<Long, Contact> contacts, IItemSearcher searcher,
            ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {
        int frag = 0;
        int firstMsg = 0;
        byte[] bytes;
        ReportGenerator r = new ReportGenerator(searcher);
        while ((bytes = r.generateNextChatHtml(c)) != null) {
            int nextMsg = r.getNextMsgNum();

            String chatName = getChatNamePrefix(c);
            if (frag > 0 || nextMsg < c.getMessages().size())
                chatName += "_" + frag++; //$NON-NLS-1$

            Metadata chatMetadata = new Metadata();
            chatMetadata.set(TikaCoreProperties.TITLE, chatName);
            chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CHAT.toString());
            chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Long.toString(c.getId()));

            if (c.isGroup()) {
                ChatGroup cg = (ChatGroup) c;
                if (cg.getMembers() != null && !cg.getMembers().isEmpty()) {
                    for (Long id : cg.getMembers()) {
                        chatMetadata.add(ExtraProperties.PARTICIPANTS, id.toString());

                    }
                }
            }

            List<Message> msgSubset = c.getMessages().subList(firstMsg, nextMsg);

            if (extractMessages && msgSubset.size() > 0) {
                chatMetadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());
            }
            storeLinkedHashes(msgSubset, chatMetadata);

            ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
            extractor.parseEmbedded(chatStream, handler, chatMetadata, false);

            if (extractMessages) {
                extractMessages(chatName, msgSubset, account, contacts, c.getId(), handler, extractor);
            }

            firstMsg = nextMsg;
        }
    }

    private String getChatNamePrefix(Chat c) {
        String title = "Telegram_";
        if (c.isGroup()) {
            title += "Group";
        } else {
            title += "Chat";
        }
        title += "_" + c.getName();
        return title;
    }

    private void extractMessages(String chatName, List<Message> messages, Contact account,
            Map<Long, Contact> contacts, long parentId, ContentHandler handler,
            EmbeddedDocumentExtractor extractor) throws SAXException, IOException {
        int msgCount = 0;
        for (Message m : messages) {
            Metadata meta = new Metadata();
            meta.set(TikaCoreProperties.TITLE, chatName + "_message_" + msgCount++); //$NON-NLS-1$
            meta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_MESSAGE.toString());
            meta.set(ExtraProperties.PARENT_VIRTUAL_ID, Long.toString(parentId));
            meta.set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(m.getId()));
            meta.set(ExtraProperties.USER_ACCOUNT_TYPE, TELEGRAM);
            meta.set(ExtraProperties.MESSAGE_DATE, m.getTimeStamp());
            meta.set(TikaCoreProperties.CREATED, m.getTimeStamp());

            // TODO handle group messages
            meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, m.getFrom().toString());
            if (m.getToId() != 0){
                meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, contacts.get(m.getToId()).toString());
            } else if (m.isFromMe()) {
                meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, m.getChat().getC().toString());
            } else if (account != null) {
                meta.set(org.apache.tika.metadata.Message.MESSAGE_TO, account.toString());
            }
            meta.set(ExtraProperties.MESSAGE_BODY, m.getData());

            // system messages
            if (m.getType() != null && !m.getType().isEmpty()) {
                meta.add(ExtraProperties.MESSAGE_BODY, m.getType().toUpperCase());
            }

            meta.set("mediaName", m.getMediaName());

            if (m.getMediaMime() != null) {
                meta.add(ExtraProperties.MESSAGE_BODY, ATTACHMENT_MESSAGE + m.getMediaMime());
            }
            if (m.getMediasize() != 0) {
                meta.set("mediaSize", Long.toString(m.getMediasize()));
            }
            if (m.getMediaHash() != null) {
                meta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_ATTACHMENT.toString());
                meta.set(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + m.getMediaHash()); //$NON-NLS-1$
                // TODO store thumb in metadata?
            }
            if (m.isPhoneCall()) {
                meta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CALL.toString());
            }

            meta.set(BasicProps.HASH, "");
            extractor.parseEmbedded(new EmptyInputStream(), handler, meta, false);
        }
    }

    public void parseTelegramDBIOS(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
    	try (Connection conn = getConnection(stream, metadata, context)) {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            Extractor e = new Extractor(conn);
            e.setSearcher(searcher);

            e.extractContactsIOS();

            // extract user account after contacts to link the account id with the contact
            Contact useraccount = e.extractUserAccountIOS();
            if (useraccount != null) {
                createAccountHTML(useraccount, handler, context);
            }
            ReportGenerator r = new ReportGenerator(searcher);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (e.getContacts() != null) {
                for (Contact c : e.getContacts().values()) {
                    byte[] bytes = r.genarateContactHtml(c);
                    Metadata cMetadata = new Metadata();
                    cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CONTACT.toString());
                    cMetadata.set(TikaCoreProperties.TITLE, c.getName());
                    cMetadata.set(ExtraProperties.USER_NAME, c.getName());
                    cMetadata.set(ExtraProperties.USER_PHONE, c.getPhone());
                    cMetadata.set(ExtraProperties.USER_ACCOUNT, c.getId() + "");
                    cMetadata.set(ExtraProperties.USER_ACCOUNT_TYPE, TELEGRAM);
                    cMetadata.set(ExtraProperties.USER_NOTES, c.getUsername());
                    if (c.getAvatar() != null) {
                        cMetadata.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(c.getAvatar()));
                    }
                    ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(contactStream, handler, cMetadata, false);
                }
            }

            e.extractChatListIOS();
            for (Chat c : e.getChatList()) {
                c.getMessages().addAll(e.extractMessagesIOS(c));
                generateChat(c, useraccount, e.getContacts(), searcher, handler, extractor);
            }

        } catch (SQLException e) {
    		e.printStackTrace();
            throw new TikaException("Error parsing telegram ios database", e);
		}
    }

    private Contact searchAndroidAccount(IItemSearcher searcher, String dbPath) {
        String query = BasicProps.CONTENTTYPE + ":\"" + TELEGRAM_USER_CONF.toString() + "\"";
        List<IItemBase> result = searcher.search(query);
        IItemBase item = getBestItem(result, dbPath);
        if (item != null) {
            try (InputStream is = item.getBufferedStream()) {
                Contact account = decodeAndroidAccount(is);
                if (account != null)
                    return account;

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        Contact account = new Contact(0);
        // TODO externalize to locale properties
        account.setName("unknown account");
        return account;
    }

    private IItemBase getBestItem(List<IItemBase> result, String path) {
        if (result.size() == 1) {
            return result.get(0);
        } else if (result.size() > 1) {
            while ((path = new File(path).getParent()) != null) {
                for (IItemBase item : result) {
                    if (item.getPath().startsWith(path)) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    private Contact decodeAndroidAccount(InputStream stream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(stream);
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile("//string[@name=\"user\"]");
        NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        if (nl.getLength() > 0) {
            Element e = (Element) nl.item(0);
            byte[] b = DatatypeConverter.parseBase64Binary(e.getTextContent());
            Contact user = Contact.getContactFromBytes(b);
            return user;
        }
        return null;
    }

    public void parseAndroidAccount(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context) throws SAXException, IOException, TikaException {

		try {
            Contact user = decodeAndroidAccount(stream);
            if (user != null) {
                createAccountHTML(user, handler, context);
	    	}
	    	
        } catch (Exception e) {
            throw new TikaException("Error parsing telegram account", e);
		}
    	
    }
    
    private void createAccountHTML(Contact user, ContentHandler handler, ParseContext context)
            throws IOException, SAXException {
        Metadata meta = new Metadata();
        meta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_ACCOUNT.toString());
        meta.set(TikaCoreProperties.TITLE, "Telegram - " + user.getFullname());
        meta.set(ExtraProperties.USER_NAME, user.getName());
        meta.set(ExtraProperties.USER_PHONE, user.getPhone());
        meta.set(ExtraProperties.USER_ACCOUNT, user.getUsername());
        meta.set(ExtraProperties.USER_ACCOUNT_TYPE, TELEGRAM);
        Extractor ex = new Extractor();
        IItemSearcher searcher = context.get(IItemSearcher.class);
        ex.setSearcher(searcher);
        ex.searchAvatarFileName(user, user.getPhotos());
        if (user.getAvatar() != null) {
            meta.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(user.getAvatar()));
        }

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        ReportGenerator reportGenerator = new ReportGenerator(searcher);
        byte[] bytes = reportGenerator.genarateContactHtml(user);
        ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
        extractor.parseEmbedded(contactStream, handler, meta, false);
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
         throws IOException, SAXException, TikaException {
    	
    	String mimetype = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
    	if(mimetype.equals(TELEGRAM_DB.toString())) {
    		parseTelegramDBAndroid(stream, handler, metadata, context);
    	}
    	if(mimetype.equals(TELEGRAM_DB_IOS.toString())) {
    		parseTelegramDBIOS(stream, handler, metadata, context);
    	}
    	if(mimetype.equals(TELEGRAM_USER_CONF.toString())) {
    		parseAndroidAccount(stream, handler, metadata, context);
    	}
        
    }

}
