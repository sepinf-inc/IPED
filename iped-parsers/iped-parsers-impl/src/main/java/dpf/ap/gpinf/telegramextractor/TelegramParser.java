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
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

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
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties; 

public class TelegramParser extends SQLite3DBParser {

    /**
     * 
     */
    private static final long serialVersionUID = -2981296547291818873L;
    private static final int MAXMSGS = 5000;
    public static final MediaType TELEGRAM_ACCOUNT = MediaType.parse("application/x-telegram-account");
    public static final MediaType TELEGRAM_USER_CONF = MediaType.parse("application/x-telegram-user-conf");
    public static final MediaType TELEGRAM_DB = MediaType.parse("application/x-telegram-db");
    public static final MediaType TELEGRAM_DB_IOS = MediaType.parse("application/x-telegram-db-ios");
    public static final MediaType TELEGRAM_CHAT = MediaType.parse("application/x-telegram-chat");
    public static final MediaType TELEGRAM_CONTACT = MediaType.parse("contact/x-telegram-contact");

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set(TELEGRAM_DB,TELEGRAM_USER_CONF,TELEGRAM_DB_IOS);
    }
    
    private void storeLinkedHashes(List<Message> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMediaHash() != null) {
                metadata.add(ExtraProperties.LINKED_ITEMS, "hash:" + m.getMediaHash()); //$NON-NLS-1$
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
            ReportGenerator r = new ReportGenerator();
            r.setSearcher(searcher);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (e.getContacts() != null) {
                for (Contact c : e.getContacts().values()) {
                    if (c.getPhone() == null) {
                        continue;
                    }
                    byte[] bytes = r.genarateContactHtml(c);
                    Metadata cMetadata = new Metadata();
                    cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CONTACT.toString());
                    cMetadata.set(TikaCoreProperties.TITLE, c.getName());
                    cMetadata.set(ExtraProperties.USER_NAME, c.getName());
                    cMetadata.set(ExtraProperties.USER_PHONE, c.getPhone());
                    cMetadata.set(ExtraProperties.USER_ACCOUNT, c.getId() + "");
                    cMetadata.set(ExtraProperties.USER_ACCOUNT_TYPE, "Telegram");
                    cMetadata.set(ExtraProperties.USER_NOTES, c.getUsername());
                    if (c.getAvatar() != null) {
                        cMetadata.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(c.getAvatar()));
                    }
                    ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(contactStream, handler, cMetadata, false);
                }
            }
            e.performExtraction();

            for (Chat c : e.getChatList()) {
                try {
                    c.getMessages().addAll(e.extractMessages(c));

                    for (int i = 0; i * MAXMSGS < c.getMessages().size(); i++) {
                    	int start=i * MAXMSGS;
                    	int end=start+MAXMSGS;
                    	if(end>=c.getMessages().size()) {
                    		end=c.getMessages().size();
                    	}
                        byte[] bytes = r.generateChatHtml(c, start, end);
                        Metadata chatMetadata = new Metadata();
                        String title = "Telegram_";
                        if (c.isGroup()) {
                            title += "Group";
                        } else {
                            title += "Chat";
                        }
                        title += "_" + c.getName();
                        if (c.getMessages().size() > MAXMSGS) {
                            title += "_" + i;
                        }
                        chatMetadata.set(TikaCoreProperties.TITLE, title);
                        chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CHAT.toString());
                        chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Long.toString(c.getId()));
                        storeLinkedHashes(c.getMessages().subList(start, end),chatMetadata);

                        ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                        extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                        
                        

                    }

                } catch (Exception ex) {
                    // TODO: handle exception
                    ex.printStackTrace();
                }

            }
        } catch (SQLException e1) {
            e1.printStackTrace();
            throw new TikaException("Error parsing telegram database", e1);
        }
    	
    }
    
    public void parseTelegramDBIOS(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
    	try (Connection conn = getConnection(stream, metadata, context)) {
    		System.out.print("ola telegram");
            IItemSearcher searcher = context.get(IItemSearcher.class);
            System.out.println("seacher "+searcher);
            Extractor e = new Extractor(conn);
            e.setSearcher(searcher);
            e.extractContactsIOS();
            ReportGenerator r = new ReportGenerator();
            r.setSearcher(searcher);
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            if (e.getContacts() != null) {
                for (Contact c : e.getContacts().values()) {
                    if (c.getPhone() == null) {
                        continue;
                    }
                    byte[] bytes = r.genarateContactHtml(c);
                    Metadata cMetadata = new Metadata();
                    cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CONTACT.toString());
                    cMetadata.set(TikaCoreProperties.TITLE, c.getName());
                    cMetadata.set(ExtraProperties.USER_NAME, c.getName());
                    cMetadata.set(ExtraProperties.USER_PHONE, c.getPhone());
                    cMetadata.set(ExtraProperties.USER_ACCOUNT, c.getId() + "");
                    cMetadata.set(ExtraProperties.USER_ACCOUNT_TYPE, "Telegram");
                    cMetadata.set(ExtraProperties.USER_NOTES, c.getUsername());
                    if (c.getAvatar() != null) {
                        cMetadata.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(c.getAvatar()));
                    }
                    ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
                    extractor.parseEmbedded(contactStream, handler, cMetadata, false);
                }
                System.out.println("teste 22");
                e.extractChatListIOS();
                System.out.println("total de chats "+e.getChatList().size());
                for (Chat c : e.getChatList()) {
                    try {
                        c.getMessages().addAll(e.extractMessagesIOS(c));

                        for (int i = 0; i * MAXMSGS < c.getMessages().size(); i++) {
                        	int start=i * MAXMSGS;
                        	int end=start+MAXMSGS;
                        	if(end>=c.getMessages().size()) {
                        		end=c.getMessages().size();
                        	}
                            byte[] bytes = r.generateChatHtml(c, start, end);
                            Metadata chatMetadata = new Metadata();
                            String title = "Telegram_";
                            if (c.isGroup()) {
                                title += "Group";
                            } else {
                                title += "Chat";
                            }
                            title += "_" + c.getName();
                            if (c.getMessages().size() > MAXMSGS) {
                                title += "_" + i;
                            }
                            chatMetadata.set(TikaCoreProperties.TITLE, title);
                            chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CHAT.toString());
                            chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Long.toString(c.getId()));
                            storeLinkedHashes(c.getMessages().subList(start, end),chatMetadata);
                            ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                            extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                            

                        }

                    } catch (Exception ex) {
                        // TODO: handle exception
                        ex.printStackTrace();
                    }
                }
            }
    	}catch (Exception e) {
    		e.printStackTrace();
			// TODO: handle exception
		}
    }
    
    public void parseTelegramAccount(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)throws SAXException, IOException {
    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	DocumentBuilder builder;
    	
		try {
			builder = factory.newDocumentBuilder();
		
	    	Document doc = builder.parse(stream);
	    	XPathFactory xPathfactory = XPathFactory.newInstance();
	    	XPath xpath = xPathfactory.newXPath();
	    	XPathExpression expr = xpath.compile("//string[@name=\"user\"]");
	    	NodeList nl = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
	    	if(nl.getLength()>0) {
	    		Element e=(Element) nl.item(0);
	    		byte[] b=DatatypeConverter.parseBase64Binary(e.getTextContent());
	    		Contact user=Contact.getContactFromBytes(b);
	    		
	    		
	    		Metadata meta = new Metadata();
    	        meta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_ACCOUNT.toString());
    	        meta.set(TikaCoreProperties.TITLE,"Telegram - "+ user.getFullname());
    	        meta.set(ExtraProperties.USER_NAME, user.getName());
    	        meta.set(ExtraProperties.USER_PHONE, user.getPhone());
    	        meta.set(ExtraProperties.USER_ACCOUNT, user.getUsername());
    	        meta.set(ExtraProperties.USER_ACCOUNT_TYPE, "Telegram");
    	        Extractor ex = new Extractor(null);
    	        IItemSearcher searcher = context.get(IItemSearcher.class);
                ex.setSearcher(searcher);
                ex.searchAvatarFileName(user, user.getPhotos());
    	        if (user.getAvatar() != null) {
    	            meta.set(ExtraProperties.USER_THUMB, Base64.getEncoder().encodeToString(user.getAvatar()));
    	        }

    	       
    	        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
    	                new ParsingEmbeddedDocumentExtractor(context));
    	        ReportGenerator reportGenerator = new ReportGenerator();
    	        byte[] bytes=reportGenerator.genarateContactHtml(user);
    	        ByteArrayInputStream contactStream = new ByteArrayInputStream(bytes);
                extractor.parseEmbedded(contactStream, handler, meta, false);
                
	  
    	        
	    		
	    	}
	    	
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
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
    		parseTelegramAccount(stream, handler, metadata, context);
    	}
        
    }

}
