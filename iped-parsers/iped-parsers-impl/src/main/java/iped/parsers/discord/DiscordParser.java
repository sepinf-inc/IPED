package iped.parsers.discord;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import iped.data.IItemReader;
import iped.parsers.browsers.chrome.CacheIndexParser;
import iped.parsers.chat.PartyStringBuilderFactory;
import iped.parsers.discord.cache.Index;
import iped.parsers.discord.json.DiscordAttachment;
import iped.parsers.discord.json.DiscordAuthor;
import iped.parsers.discord.json.DiscordRoot;
import iped.parsers.discord.json.DiscordSticker;
import iped.parsers.standard.StandardParser;
import iped.parsers.util.ConversationConstants;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;
import iped.utils.EmptyInputStream;
import iped.utils.ImageUtil;

/***
 *
 * @author PCF Campanini
 * @author PCF Patrick Dalla Bernardina
 *
 */
public class DiscordParser extends AbstractParser {

    private static final long serialVersionUID = 2785303004503782295L;

    private static final Logger logger = LoggerFactory.getLogger(CacheIndexParser.class);


    // public static final MediaType DISCORD_INDEX_MIME_TYPE =
    // MediaType.application("application/x-discord-index");
    // public static final MediaType CHROME_INDEX_MIME_TYPE =
    // MediaType.application("application/x-chrome-cache-index");

    public static final String DATA_MIME_TYPE_V2_0 = "data-v20/x-discord-chat";
    public static final String DATA_MIME_TYPE_V2_1 = "data-v21/x-discord-chat";

    public static final String CHAT_MIME_TYPE = "application/x-discord-chat+json";
    public static final String CHAT_MIME_TYPE_HTML = "application/x-discord-chat";
    public static final String MSG_MIME_TYPE = "message/x-discord-message";
    public static final String CALL_MIME_TYPE = "call/x-discord-call";
    public static final String ATTACH_MIME_TYPE = "message/x-discord-attachment";

    public static final MediaType DISCORD_ACCOUNT = MediaType.application("x-discord-account"); //$NON-NLS-1$

    private static Logger LOGGER = LoggerFactory.getLogger(Index.class);

    private static final Set<MediaType> SUPPORTED_TYPES = new HashSet<>(Arrays.asList(MediaType.parse(CHAT_MIME_TYPE)));
    private static final String ME_URL = "https://discord.com/api/v9/users/@me";

    static {
        // this code may be removed when backward parsersconfig.xml compatibility were
        // not more desired
        addChromeIndexMimeTypeSupport();
    }

    // this code may be removed when backward parsersconfig.xml compatibility were
    // not more desired
    @Deprecated
    static private void addChromeIndexMimeTypeSupport() {
        SUPPORTED_TYPES.add(CacheIndexParser.CHROME_INDEX_MIME_TYPE);
    }

    // this code may be removed when backward parsersconfig.xml compatibility were
    // not more desired
    @Deprecated
    CacheIndexParser cacheIndexParser;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        try {
        String contentType = metadata.get(HttpHeaders.CONTENT_TYPE);
        if (contentType.equals(CHAT_MIME_TYPE)) {
            parseDiscord(indexFile, handler, metadata, context);
        } else {
            parseCacheIndex(indexFile, handler, metadata, context);
        }
        } catch (Exception e) {
            logger.warn("Discord error", e);
            throw e;
        }
    }

    // this code may be removed when backward parsersconfig.xml compatibility were
    // not more desired
    @Deprecated
    public void parseCacheIndex(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        // checks if CacheIndexParser wasn't already executed
        if (context.get(CacheIndexParser.class) == null) {
            // if not, force its execution
            if (cacheIndexParser == null) {
                cacheIndexParser = new CacheIndexParser();
            }
            cacheIndexParser.parse(indexFile, handler, metadata, context);
        }
    }

    public void parseDiscord(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

        IItemSearcher searcher = context.get(IItemSearcher.class);
        IItemReader item = context.get(IItemReader.class);

        String commonQuery = BasicProps.EVIDENCE_UUID + ":" + item.getDataSource().getUUID() + " AND " + BasicProps.PARENTID + ":" + item.getParentId() + " AND NOT " + BasicProps.LENGTH + ":0 AND "
                + searcher.escapeQuery(CacheIndexParser.IS_CACHE_INDEX_ENTRY) + ":true";

        try (InputStream is = TikaInputStream.get(indexFile, new TemporaryResources())) {

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            List<DiscordRoot> discordRoot = null;
            try {
                discordRoot = mapper.readValue(is, new TypeReference<List<DiscordRoot>>() {
                });
            } catch (JsonProcessingException ex) {
                LOGGER.warn("Invalid JSON inside cache entry " + item.getPath(), ex);
                discordRoot = Collections.emptyList();
            }

            HashMap<String, byte[]> avatarCache = new HashMap<>();
            if (!discordRoot.isEmpty()) {
                metadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());

                // Checking if the image file is cached, to do so, iterates through all authors
                // and attachments to check if they are in the case, comparing their attributes
                for (DiscordRoot dr : discordRoot) {
                    if (dr.getAuthor().getAvatar() != null) {
                        byte[] avatar = avatarCache.get(dr.getAuthor().getAvatar());
                        if (avatar != null) {
                            dr.getAuthor().setAvatarBytes(avatar);
                        } else {
                            try {
                                List<IItemReader> avatars = searcher.search(commonQuery + " AND " + BasicProps.NAME + ":" + dr.getAuthor().getAvatar() + "*");
                                for (IItemReader avatarItem : avatars) {
                                    BufferedImage img = ImageUtil.getSubSampledImage(avatarItem, 64);
                                    if (img != null) {
                                        img = ImageUtil.getOpaqueImage(img);
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        ImageIO.write(img, "jpg", baos);
                                        avatar = baos.toByteArray();
                                        dr.getAuthor().setAvatarBytes(avatar);
                                        avatarCache.put(dr.getAuthor().getAvatar(), avatar);
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.warn("Exception decoding Discord avatar", e);
                            }
                        }
                    }
                }
            }

            String id = !discordRoot.isEmpty() ? discordRoot.get(0).getId() : "unknown";
            String chatName = "DiscordChat ID (" + id + ")";

            DiscordAuthor me = extractAccount(searcher, commonQuery, mapper, avatarCache, handler, extractor);

            if (discordRoot.isEmpty()) {
                return;
            }

            Metadata chatmetadata = new Metadata();
            int chatVirtualId = 0;
            chatmetadata.set("URL", item.getName());
            chatmetadata.set(TikaCoreProperties.TITLE, chatName);
            chatmetadata.set(StandardParser.INDEXER_CONTENT_TYPE, CHAT_MIME_TYPE_HTML);
            chatmetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Integer.toString(chatVirtualId));

            // Type
            chatmetadata.set(ExtraProperties.CONVERSATION_NAME, chatName);
            chatmetadata.set(ExtraProperties.CONVERSATION_TYPE, discordRoot.get(0).getType() + "");
            chatmetadata.set(ExtraProperties.CONVERSATION_ID, discordRoot.get(0).getId());

            // Account
            addPartyFields(chatmetadata, ExtraProperties.CONVERSATION_ACCOUNT, me);

            // Participants
            TreeMap<String, DiscordAuthor> participantsMap = new TreeMap<>();
            for (DiscordRoot d : discordRoot) {
                if (d.getAuthor() != null) {
                    participantsMap.putIfAbsent(d.getAuthor().getFullUsername(), d.getAuthor());
                }
            }
            Collection<DiscordAuthor> participants = participantsMap.values();
            for (DiscordAuthor participant : participants) {
                addPartyFields(chatmetadata, ExtraProperties.CONVERSATION_PARTICIPANTS, participant);
            }

            for (DiscordRoot dr : discordRoot) {
                for (DiscordSticker sticker : dr.getStickers()) {
                    try {
                        List<IItemReader> stickerItems = searcher.search(commonQuery + " AND " 
                                + searcher.escapeQuery(CacheIndexParser.CACHE_URL) + ":\"" + sticker.getId() + ".json\"" + " AND " 
                                + searcher.escapeQuery(CacheIndexParser.CACHE_URL) + ":\"discord\"");
                        for (IItemReader stickerItem : stickerItems) {
                            sticker.setMediaHash(stickerItem.getHash());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Exception decoding Discord attachment", e);
                    }

                    if (sticker.getMediaHash() != null) {
                        chatmetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + sticker.getMediaHash());
                    }

                }
                for (DiscordAttachment da : dr.getAttachments()) {
                    try {
                        String[] parts = da.getUrl().split("https://cdn.discordapp.com/attachments/");

                        if (parts.length > 1) {
                            long greater = 0;
                            List<IItemReader> atts = searcher.search(commonQuery + " AND " + CacheIndexParser.CACHE_URL.replace(":", "\\:") + ":\"" + parts[1] + "\"");
                            for (IItemReader attsItem : atts) {
                                if (da.getSize() > greater) {
                                    da.setMediaHash(attsItem.getHash());
                                    da.setContent_type(attsItem.getMediaType().toString());
                                    greater = da.getSize();
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Exception decoding Discord attachment", e);
                    }

                    if (da.getMediaHash() != null) {
                        chatmetadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + da.getMediaHash());
                    }

                }
            }

            // Sort messages by timestamp in ascending order
            Collections.sort(discordRoot);

            byte[] relatorio = new DiscordHTMLReport(me).convertToHTML(discordRoot, searcher);
            extractor.parseEmbedded(new ByteArrayInputStream(relatorio), handler, chatmetadata, false);

            extractMessages(chatName, discordRoot, me, participants, handler, extractor, chatVirtualId);
        }
    }

    private void addPartyFields(Metadata meta, String field, DiscordAuthor author) {
        if (author == null) {
            return;
        }
        meta.add(field,  PartyStringBuilderFactory.getBuilder("Discord")
                .withUserId(author.getId())
                .withName(author.getName())
                .withPhoneNumber(author.getPhone())
                .withUsername(author.getUsername())
                .build());
        meta.add(field + ExtraProperties.CONVERSATION_SUFFIX_ID, author.getId());
        meta.add(field + ExtraProperties.CONVERSATION_SUFFIX_NAME, author.getName());
        meta.add(field + ExtraProperties.CONVERSATION_SUFFIX_PHONE, author.getPhone());
        meta.add(field + ExtraProperties.CONVERSATION_SUFFIX_USERNAME, author.getFullUsername());
    }

    private DiscordAuthor extractAccount(IItemSearcher searcher, String commonQuery, ObjectMapper mapper, HashMap<String, byte[]> avatarCache, ContentHandler handler, EmbeddedDocumentExtractor extractor) {
        DiscordAuthor me = null;

        try {
            // find me info
            List<IItemReader> mes = searcher.search(commonQuery + " AND " + searcher.escapeQuery(CacheIndexParser.CACHE_URL) + ":\"" + ME_URL + "\"");
            for (IItemReader mei : mes) {
                if (mei.getName().equals("@me")) {
                    try (InputStream is2 = mei.getBufferedInputStream()) {
                        try {
                            byte[] mebytes = is2.readAllBytes();
                            me = mapper.readValue(mebytes, new TypeReference<DiscordAuthor>() {
                            });
                            Metadata memeta = new Metadata();
                            memeta.set(TikaCoreProperties.TITLE, me.getUsername());
                            memeta.set(ExtraProperties.USER_NAME, me.getName());
                            memeta.set(ExtraProperties.USER_PHONE, me.getPhone());
                            memeta.set(ExtraProperties.USER_ACCOUNT, me.getId());
                            memeta.set(ExtraProperties.USER_ACCOUNT_TYPE, "Discord");
                            memeta.set(ExtraProperties.USER_EMAIL, me.getEmail());
                            // memeta.set(ExtraProperties.USER_NOTES, me.getBio());
                            memeta.set(ExtraProperties.DECODED_DATA, Boolean.toString(true));

                            if (me.getAvatar() != null) {
                                byte[] meavatar = avatarCache.get(me.getAvatar());
                                memeta.set(ExtraProperties.THUMBNAIL_BASE64, Base64.getEncoder().encodeToString(meavatar));
                            }
                            memeta.set(StandardParser.INDEXER_CONTENT_TYPE, DISCORD_ACCOUNT.toString());
                            extractor.parseEmbedded(new ByteArrayInputStream(mebytes), handler, memeta, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Error searching for discord account:" + e.getClass().getCanonicalName());
        }
        return me;
    }

    private void extractMessages(String chatName, List<DiscordRoot> discordRoot, DiscordAuthor me, Collection<DiscordAuthor> participants, ContentHandler handler, EmbeddedDocumentExtractor extractor, int chatVirtualId) throws SAXException, IOException {
        int msgCount = 0;

        // Set metadata
        for (DiscordRoot d : discordRoot) {

            Metadata meta = new Metadata();
            meta.set(TikaCoreProperties.TITLE, chatName + "_message_" + msgCount++);
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, MSG_MIME_TYPE);
            meta.set(ExtraProperties.COMMUNICATION_DATE, d.getTimestamp());
            meta.set(ExtraProperties.MESSAGE_BODY, d.getMessageContent());
            meta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(chatVirtualId));
            meta.set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(d.getId()));
            meta.set(ExtraProperties.DECODED_DATA, Boolean.toString(true));

            // From
            addPartyFields(meta, ExtraProperties.COMMUNICATION_FROM, d.getAuthor());

            // Add "Message-TO" field.
            // In cases where only one participant sends messages, it is not possible to
            // determine the participants as only the participant list of the calls are cached.
            if (participants.size() <= 2) {
                for (DiscordAuthor participant : participants) {
                    if (!participant.getFullUsername().equals(d.getAuthor().getFullUsername())) {
                        addPartyFields(meta, ExtraProperties.COMMUNICATION_TO, participant);
                    }
                }
            } else {
                meta.set(ExtraProperties.COMMUNICATION_TO, chatName);
                meta.set(ExtraProperties.IS_GROUP_MESSAGE, Boolean.toString(true));
            }

            // Direction
            Boolean isFromMe = null;
            if (me != null) {
                isFromMe = me.getFullUsername().equals(d.getAuthor().getFullUsername());
                if (isFromMe) {
                    meta.set(ExtraProperties.COMMUNICATION_DIRECTION, ConversationConstants.DIRECTION_OUTGOING);
                } else {
                    meta.set(ExtraProperties.COMMUNICATION_DIRECTION, ConversationConstants.DIRECTION_INCOMING);
                }
            }

            for (DiscordAttachment da : d.getAttachments()) {
                if (da.getMediaHash() != null) {
                    meta.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + da.getMediaHash());
                    meta.set(StandardParser.INDEXER_CONTENT_TYPE, ATTACH_MIME_TYPE);
                    if (isFromMe != null && isFromMe) {
                         meta.add(ExtraProperties.SHARED_HASHES, da.getMediaHash());
                    }
                }
            }

            if (d.getCall() != null) {
                meta.set(StandardParser.INDEXER_CONTENT_TYPE, CALL_MIME_TYPE);
                meta.set("CallDetails", d.getCall().toString());
                if (d.getCall().getEndedTimestamp() != null) {
                    meta.set("EndTime", DateUtil.dateToString(d.getCall().getEndedTimestamp()));
                }
            }

            meta.set(BasicProps.LENGTH, ""); //$NON-NLS-1$
            extractor.parseEmbedded(new EmptyInputStream(), handler, meta, false);

        }
    }
}
