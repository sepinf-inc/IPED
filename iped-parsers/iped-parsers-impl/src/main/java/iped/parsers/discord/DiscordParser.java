package iped.parsers.discord;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

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
import org.apache.tika.sax.XHTMLContentHandler;
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
import iped.parsers.discord.cache.CacheAddr.InputStreamNotAvailable;
import iped.parsers.discord.cache.Index;
import iped.parsers.discord.json.DiscordAttachment;
import iped.parsers.discord.json.DiscordRoot;
import iped.parsers.standard.StandardParser;
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

    // TODO

    private static final long serialVersionUID = 1L;
    // public static final MediaType DISCORD_INDEX_MIME_TYPE =
    // MediaType.application("application/x-discord-index");
    // public static final MediaType CHROME_INDEX_MIME_TYPE =
    // MediaType.application("application/x-chrome-cache-index");

    public static final String DATA_MIME_TYPE_V2_0 = "data-v20/x-discord-chat";
    public static final String DATA_MIME_TYPE_V2_1 = "data-v21/x-discord-chat";

    public static final String CHAT_MIME_TYPE = "application/x-discord-chat+json";
    public static final String MSG_MIME_TYPE = "message/x-discord-message";
    public static final String CALL_MIME_TYPE = "call/x-discord-call";
    public static final String ATTACH_MIME_TYPE = "message/x-discord-attachment";

    private static Logger LOGGER = LoggerFactory.getLogger(Index.class);

    private static final Set<MediaType> SUPPORTED_TYPES = new HashSet<MediaType>(
            Arrays.asList(MediaType.parse(CHAT_MIME_TYPE)));

    static
    {
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
    public void parse(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType.equals(CHAT_MIME_TYPE)) {
            parseDiscord(indexFile, handler, metadata, context);
        } else {
            parseCacheIndex(indexFile, handler, metadata, context);
        }
    }

    // this code may be removed when backward parsersconfig.xml compatibility were
    // not more desired
    @Deprecated
    public void parseCacheIndex(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // checks if CacheIndexParser wasn't already executed
        if (context.get(CacheIndexParser.class) == null) {
            // if not, force its execution
            if (cacheIndexParser == null) {
                cacheIndexParser = new CacheIndexParser();
            }
            cacheIndexParser.parse(indexFile, handler, metadata, context);
        }
    }

    public void parseDiscord(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        IItemSearcher searcher = context.get(IItemSearcher.class);
        IItemReader item = context.get(IItemReader.class);

        String commonQuery = BasicProps.EVIDENCE_UUID + ":" + item.getDataSource().getUUID() + " AND "
                + BasicProps.PARENTID + ":" + item.getParentId() + " AND NOT " + BasicProps.LENGTH + ":0 AND "
                + CacheIndexParser.IS_CACHE_INDEX_ENTRY.replace(":", "\\:") + ":true";

        try (InputStream is = TikaInputStream.get(indexFile, new TemporaryResources())) {

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            List<DiscordRoot> discordRoot = null;
            try {
                discordRoot = mapper.readValue(is, new TypeReference<List<DiscordRoot>>() {
                });
            } catch (JsonProcessingException ex) {
                LOGGER.error("Invalid JSON inside cache entry " + item.getPath());
                ex.printStackTrace();
            }

            if (!discordRoot.isEmpty()) {
                metadata.set(BasicProps.HASCHILD, Boolean.TRUE.toString());

                HashMap<String, byte[]> avatarCache = new HashMap<>();
                // Checking if the image file is cached, to do so, iterates through all authors
                // and attachments to check if they are in the case, comparing their attributes
                for (DiscordRoot dr : discordRoot) {
                    if (dr.getAuthor().getAvatar() != null) {
                        byte[] avatar = avatarCache.get(dr.getAuthor().getAvatar());
                        if (avatar != null) {
                            dr.getAuthor().setAvatarBytes(avatar);
                            break;
                        } else {
                            try {
                                List<IItemReader> avatars = searcher.search(commonQuery + " AND " + BasicProps.NAME
                                        + ":" + dr.getAuthor().getAvatar() + "*");
                                for (IItemReader avatarItem : avatars) {
                                    try (InputStream is2 = avatarItem.getBufferedInputStream()) {
                                        BufferedImage img = ImageUtil.getSubSampledImage(is2, 64, 64);
                                        img = ImageUtil.getOpaqueImage(img);
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        ImageIO.write(img, "jpg", baos);
                                        avatar = baos.toByteArray();
                                        dr.getAuthor().setAvatarBytes(avatar);
                                        avatarCache.put(dr.getAuthor().getAvatar(), avatar);
                                        break;
                                    } catch (InputStreamNotAvailable e) {
                                        // ignore
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.warn("Exception decoding Discord avatar", e);
                            }
                        }
                    }
                }
            }

            String chatName = "DiscordChat id(" + discordRoot.get(0).getId() + ")";

            metadata.set("URL", item.getName());
            metadata.set(TikaCoreProperties.TITLE, chatName);
            metadata.set(StandardParser.INDEXER_CONTENT_TYPE, CHAT_MIME_TYPE);

            for (DiscordRoot dr : discordRoot) {
                for (DiscordAttachment da : dr.getAttachments()) {
                    try {
                        String[] parts = da.getUrl().split("https://cdn.discordapp.com/attachments/");

                        if (parts.length > 1) {
                            List<IItemReader> atts = searcher.search(commonQuery + " AND "
                                    + CacheIndexParser.CACHE_URL.replace(":", "\\:") + ":\"" + parts[1] + "\"");
                            for (IItemReader attsItem : atts) {
                                da.setMediaHash(attsItem.getHash());
                                da.setContent_type(attsItem.getMediaType().toString());
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Exception decoding Discord attachment", e);
                    }

                    if (da.getMediaHash() != null) {
                        metadata.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + da.getMediaHash());
                    }
                }
            }

            // Sort messages by timestamp in ascending order
            Collections.sort(discordRoot);

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            new DiscordHTMLReport().printHTML(discordRoot, xhtml, searcher);

            extractMessages(chatName, discordRoot, handler, extractor, 0);
        }
    }

    private void extractMessages(String chatName, List<DiscordRoot> discordRoot, ContentHandler handler,
            EmbeddedDocumentExtractor extractor, int chatVirtualId) throws SAXException, IOException {
        int msgCount = 0;

        // Checking Participants
        LinkedHashSet<String> participants = new LinkedHashSet<String>();

        for (DiscordRoot d : discordRoot) {
            participants.add(d.getAuthor().getFullUsername());
        }

        // Set metadata
        for (DiscordRoot d : discordRoot) {

            Metadata meta = new Metadata();
            meta.set(TikaCoreProperties.TITLE, chatName + "_message_" + msgCount++);
            meta.set(StandardParser.INDEXER_CONTENT_TYPE, MSG_MIME_TYPE);
            meta.set(ExtraProperties.MESSAGE_DATE, d.getTimestamp());
            meta.set(ExtraProperties.MESSAGE_BODY, d.getMessageContent());
            meta.set(ExtraProperties.USER_NAME, d.getAuthor().getFullUsername());
            meta.add(ExtraProperties.PARTICIPANTS, participants.toString());
            meta.set(ExtraProperties.PARENT_VIRTUAL_ID, Integer.toString(chatVirtualId));
            meta.set(ExtraProperties.PARENT_VIEW_POSITION, String.valueOf(d.getId()));
            meta.set(org.apache.tika.metadata.Message.MESSAGE_FROM, d.getAuthor().getFullUsername());
            meta.set(ExtraProperties.DECODED_DATA, Boolean.TRUE.toString());

            // Add "Message-TO" field
            for (String participant : participants) {
                if (participant.length() <= 1) {
                    // In cases where only one participant sends messages, it is not possible to
                    // determine the participants as only the participant list of the calls are
                    // cached.
                } else if (!participant.equals(d.getAuthor().getFullUsername())) {
                    meta.add(org.apache.tika.metadata.Message.MESSAGE_TO, participant);
                }
            }

            for (DiscordAttachment da : d.getAttachments()) {
                if (da.getMediaHash() != null) {
                    meta.add(ExtraProperties.LINKED_ITEMS, BasicProps.HASH + ":" + da.getMediaHash());
                    meta.set(StandardParser.INDEXER_CONTENT_TYPE, ATTACH_MIME_TYPE);
                    // if (m.isFromMe())
                    // meta.add(ExtraProperties.SHARED_HASHES, m.getMediaHash());
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
