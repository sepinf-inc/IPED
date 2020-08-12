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
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import iped3.search.IItemSearcher;
import iped3.util.ExtraProperties;

public class TelegramParser extends SQLite3DBParser {

    /**
     * 
     */
    private static final long serialVersionUID = -2981296547291818873L;
    private static final int MAXMSGS = 5000;
    public static final MediaType TELEGRAM_DB = MediaType.parse("application/x-telegram-db");
    public static final MediaType TELEGRAM_CHAT = MediaType.parse("application/x-telegram-chat");
    public static final MediaType TELEGRAM_CONTACT = MediaType.parse("contact/x-telegram-contact");

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set(TELEGRAM_DB);
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        try (Connection conn = getConnection(stream, metadata, context)) {
            IItemSearcher searcher = context.get(IItemSearcher.class);
            Extractor e = new Extractor(conn);
            e.setSearcher(searcher);
            e.performExtraction();
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

            for (Chat c : e.getChatList()) {
                try {
                    c.getMessages().addAll(e.extractMessages(c));

                    for (int i = 0; i * MAXMSGS < c.getMessages().size(); i++) {
                        byte[] bytes = r.generateChatHtml(c, i * MAXMSGS, (i + 1) * MAXMSGS);
                        Metadata chatMetadata = new Metadata();
                        String title = "Telegram_";
                        if (c.isGroup()) {
                            title += "Group";
                        } else {
                            title += "Chat";
                        }
                        title += "_" + c.getName() + "_" + (i + 1);
                        chatMetadata.set(TikaCoreProperties.TITLE, title);
                        chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CHAT.toString());
                        chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Long.toString(c.getId()));

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

}
