/*
 * Copyright 2015-2015, Fabio Melo Pfeifer
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
package dpf.mg.udi.gpinf.whatsappextractor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.mg.udi.gpinf.whatsappextractor.Message.MessageType;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;
import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para banco de dados do WhatsApp
 *
 * @author Fabio Melo Pfeifer <pfeifer.fmp@dpf.gov.br>
 */
public class WhatsAppParser extends SQLite3DBParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final MediaType MSG_STORE = MediaType.application("x-whatsapp-db"); //$NON-NLS-1$

    public static final MediaType WA_DB = MediaType.application("x-whatsapp-wadb"); //$NON-NLS-1$

    public static final MediaType CHAT_STORAGE = MediaType.application("x-whatsapp-chatstorage"); //$NON-NLS-1$

    public static final MediaType CONTACTS_V2 = MediaType.application("x-whatsapp-contactsv2"); //$NON-NLS-1$

    public static final MediaType WHATSAPP_CHAT = MediaType.parse("message/x-whatsapp-msg"); //$NON-NLS-1$

    public static final MediaType WHATSAPP_CONTACT = MediaType.parse("contact/x-whatsapp-contact"); //$NON-NLS-1$

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(MSG_STORE, WA_DB, CHAT_STORAGE, CONTACTS_V2);

    private static final Map<String, WAContactsDirectory> contactsDirectoriesMap = new HashMap<>();

    private SQLite3Parser sqliteParser = new SQLite3Parser();

    public static void setSupportedTypes(Set<MediaType> supportedTypes) {
        SUPPORTED_TYPES = supportedTypes;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        String mimetype = metadata.get(IndexerDefaultParser.INDEXER_CONTENT_TYPE);
        if (mimetype == null)
            mimetype = metadata.get(Metadata.CONTENT_TYPE);

        if (mimetype.equals(MSG_STORE.toString())) {
            parseWhatsappMessages(stream, handler, metadata, context, new ExtractorAndroidFactory());
        } else if (mimetype.equals(WA_DB.toString())) {
            parseWhatsAppContacts(stream, handler, metadata, context, new ExtractorAndroidFactory());
        } else if (mimetype.equals(CHAT_STORAGE.toString())) {
            parseWhatsappMessages(stream, handler, metadata, context, new ExtractorIOSFactory());
        } else if (mimetype.equals(CONTACTS_V2.toString())) {
            parseWhatsAppContacts(stream, handler, metadata, context, new ExtractorIOSFactory());
        }
    }

    private void parseWhatsappMessages(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, ExtractorFactory extFactory) throws IOException, SAXException, TikaException {

        extFactory.setConnectionParams(stream, metadata, context);
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        IItemSearcher searcher = context.get(IItemSearcher.class);
        TemporaryResources tmp = new TemporaryResources();

        if (extractor.shouldParseEmbedded(metadata)) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            try {
                ItemInfo itemInfo = context.get(ItemInfo.class);
                String filePath = null;
                if (itemInfo != null) {
                    filePath = itemInfo.getPath();
                }
                WAContactsDirectory contacts = getWAContactsDirectoryForPath(filePath);

                Extractor waExtractor = extFactory.createMessageExtractor(tis.getFile(), contacts);
                List<Chat> chatList = waExtractor.getChatList();
                ReportGenerator reportGenerator = new ReportGenerator(searcher);

                for (Chat c : chatList) {
                    getAvatar(searcher, c.getRemote());
                    int frag = 0;
                    int firstMsg = 0;
                    byte[] bytes = reportGenerator.generateNextChatHtml(c, contacts);
                    while (bytes != null) {
                        Metadata chatMetadata = new Metadata();
                        int nextMsg = reportGenerator.getNextMsgNum();
                        storeLinkedHashes(c.getMessages().subList(firstMsg, nextMsg), chatMetadata, searcher);
                        storeLocations(c.getMessages().subList(firstMsg, nextMsg), chatMetadata);

                        firstMsg = nextMsg;
                        byte[] nextBytes = reportGenerator.generateNextChatHtml(c, contacts);

                        String chatName = c.getTitle();
                        if (frag > 0 || nextBytes != null)
                            chatName += "_" + frag++; //$NON-NLS-1$
                        chatMetadata.set(TikaCoreProperties.TITLE, chatName);
                        chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, WHATSAPP_CHAT.toString());

                        ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
                        extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
                        bytes = nextBytes;
                    }
                }

            } catch (Exception e) {
                sqliteParser.parse(tis, handler, metadata, context);
                throw new TikaException("WAExtractorException Exception", e); //$NON-NLS-1$

            } finally {
                tmp.dispose();
            }
        }

    }

    private void storeLinkedHashes(List<Message> messages, Metadata metadata, IItemSearcher searcher) {
        for (Message m : messages) {
            if (m.getMediaHash() != null) {
                metadata.add(ExtraProperties.LINKED_ITEMS, "sha-256:" + m.getMediaHash()); //$NON-NLS-1$
                if (m.isFromMe())
                    metadata.add(ExtraProperties.SHARED_HASHES, m.getMediaHash());

            } else if (m.getMediaName() != null && !m.getMediaName().isEmpty()) {
                String mediaName = m.getMediaName();
                if (mediaName.contains("/")) //$NON-NLS-1$
                    mediaName = mediaName.substring(mediaName.lastIndexOf('/') + 1); // $NON-NLS-1$
                mediaName = escape(searcher, mediaName);
                String query = BasicProps.NAME + ":\"" + mediaName + "\" AND " + BasicProps.LENGTH + ":" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + m.getMediaSize();
                metadata.add(ExtraProperties.LINKED_ITEMS, query);
                if (m.isFromMe())
                    metadata.add(ExtraProperties.SHARED_ITEMS, query);
            }
        }
    }

    private void storeLocations(List<Message> messages, Metadata metadata) {
        for (Message m : messages) {
            if (m.getMessageType() == MessageType.LOCATION_MESSAGE
                    || m.getMessageType() == MessageType.SHARE_LOCATION_MESSAGE) {
                if (m.getLatitude() != 0.0 && m.getLongitude() != 0.0) {
                    metadata.add(ExtraProperties.LOCATIONS, m.getLatitude() + ";" + m.getLongitude());
                }
            }
        }
    }

    private void getAvatar(IItemSearcher searcher, WAContact contact) {
        if (searcher != null && contact.getAvatar() == null) {
            List<IItemBase> result = searcher
                    .search(BasicProps.NAME + ":\"" + escape(searcher, contact.getFullId()) + ".j\""); //$NON-NLS-1$ //$NON-NLS-2$
            if (result.isEmpty()) {
                if (contact.getAvatarPath() != null) {
                    String avatarFileBase = contact.getAvatarPath();
                    if (avatarFileBase.contains("/")) { //$NON-NLS-1$
                        avatarFileBase = avatarFileBase.substring(avatarFileBase.lastIndexOf('/') + 1);
                    }
                    avatarFileBase = escape(searcher, avatarFileBase);
                    // Try file .jpg
                    result = searcher.search(BasicProps.NAME + ":\"" + avatarFileBase + ".jpg\""); //$NON-NLS-1$ //$NON-NLS-2$
                    if (result.isEmpty()) {
                        // Try file .thumb
                        result = searcher.search(BasicProps.NAME + ":\"" + avatarFileBase + ".thumb\""); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            }
            if (result.isEmpty()) {
                // WhatsApp initial release 2009-01-01
                long startTime = 1230768000;
                long endTime = System.currentTimeMillis() / 1000;
                if (contact.getId() != null && !contact.getId().isEmpty()) {
                    result = searcher
                            .search(BasicProps.NAME + ":(+" + escape(searcher, contact.getId()) + " +(jpg thumb)" //$NON-NLS-1$ //$NON-NLS-2$
                                    + " +[" + startTime + " TO " + endTime + "])"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    result = filterGroupAvatars(result);
                    Collections.sort(result, new AvatarComparator());
                }
            }

            if (!result.isEmpty()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];

                try (InputStream is = result.get(0).getBufferedStream()) {
                    int len = 0;
                    while ((len = is.read(buf)) != -1)
                        bos.write(buf, 0, len);
                    contact.setAvatar(bos.toByteArray());

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String escape(IItemSearcher searcher, String string) {
        if (searcher != null)
            return searcher.escapeQuery(string);
        else
            return string;
    }

    private List<IItemBase> filterGroupAvatars(List<IItemBase> avatars) {
        ArrayList<IItemBase> result = new ArrayList<IItemBase>();
        for (IItemBase item : avatars)
            if (item.getName().split("-").length < 3) //$NON-NLS-1$
                result.add(item);
        return result;
    }

    private class AvatarComparator implements Comparator<IItemBase> {
        @Override
        public int compare(IItemBase o1, IItemBase o2) {
            return o2.getName().compareTo(o1.getName());
        }
    }

    private void parseWhatsAppContacts(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, ExtractorFactory extFactory) throws IOException, SAXException, TikaException {

        extFactory.setConnectionParams(stream, metadata, context);
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        IItemSearcher searcher = context.get(IItemSearcher.class);
        TemporaryResources tmp = new TemporaryResources();

        if (extractor.shouldParseEmbedded(metadata)) {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);
            try {
                WAContactsExtractor waExtractor = extFactory.createContactsExtractor(tis.getFile());
                waExtractor.extractContactList();

                ItemInfo itemInfo = context.get(ItemInfo.class);
                String path = null;
                if (itemInfo != null) {
                    path = itemInfo.getPath();
                }
                WAContactsDirectory contacts = getWAContactsDirectoryForPath(path);

                contacts.putAll(waExtractor.getContactsDirectory());

                ReportGenerator reportGenerator = new ReportGenerator(searcher);
                for (WAContact c : waExtractor.getContactsDirectory().contacts()) {
                    Metadata cMetadata = new Metadata();
                    cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, WHATSAPP_CONTACT.toString());
                    cMetadata.set(TikaCoreProperties.TITLE, c.getTitle());

                    if (extractor.shouldParseEmbedded(cMetadata)) {
                        getAvatar(searcher, c);
                        ByteArrayInputStream chatStream = new ByteArrayInputStream(
                                reportGenerator.genarateContactHtml(c));
                        extractor.parseEmbedded(chatStream, handler, cMetadata, false);
                    }
                }

            } catch (Exception e) {
                sqliteParser.parse(tis, handler, metadata, context);
                throw new TikaException("WAExtractorException Exception", e); //$NON-NLS-1$

            } finally {
                tmp.dispose();
            }
        }
    }

    private static synchronized WAContactsDirectory getWAContactsDirectoryForPath(String path) {
        if (path == null) {
            path = ""; //$NON-NLS-1$
        } else if (path.contains("/")) { //$NON-NLS-1$
            path = path.substring(0, path.lastIndexOf('/')); // $NON-NLS-1$
        } else if (path.contains("\\")) { //$NON-NLS-1$
            path = path.substring(0, path.lastIndexOf('\\')); // $NON-NLS-1$
        }

        WAContactsDirectory cd = contactsDirectoriesMap.get(path);
        if (cd == null) {
            cd = new WAContactsDirectory();
            contactsDirectoriesMap.put(path, cd);
        }
        return cd;
    }

    private abstract class ExtractorFactory {
        
        InputStream is;
        Metadata metadata;
        ParseContext context;
        
        abstract Extractor createMessageExtractor(File file, WAContactsDirectory directory);

        abstract WAContactsExtractor createContactsExtractor(File file);
        
        void setConnectionParams(InputStream is, Metadata metadata, ParseContext context) {
            this.is = is;
            this.metadata = metadata;
            this.context = context;
        }
        
        protected Connection getConnection() throws SQLException{
            try {
                return WhatsAppParser.this.getConnection(is, metadata, context);
            } catch (IOException e) {
                throw new SQLException(e);
            }
        }
    }

    private class ExtractorAndroidFactory extends ExtractorFactory {
        
        @Override
        public Extractor createMessageExtractor(File file, WAContactsDirectory directory) {
            return new ExtractorAndroid(file, directory) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorAndroidFactory.this.getConnection();
                }
            };
        }

        @Override
        public WAContactsExtractor createContactsExtractor(File file) {
            return new WAContactsExtractorAndroid(file, new WAContactsDirectory()) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorAndroidFactory.this.getConnection();
                }
            };
        }

    }

    private class ExtractorIOSFactory extends ExtractorFactory {

        @Override
        public Extractor createMessageExtractor(File file, WAContactsDirectory directory) {
            return new ExtractorIOS(file, directory) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorIOSFactory.this.getConnection();
                }
            };
        }

        @Override
        public WAContactsExtractor createContactsExtractor(File file) {
            return new WAContactsExtractorIOS(file, new WAContactsDirectory()) {
                @Override
                protected Connection getConnection() throws SQLException {
                    return ExtractorIOSFactory.this.getConnection();
                }
            };
        }

    }

}
