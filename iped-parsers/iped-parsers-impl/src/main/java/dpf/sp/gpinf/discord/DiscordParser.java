package dpf.sp.gpinf.discord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import dpf.sp.gpinf.discord.cache.CacheEntry;
import dpf.sp.gpinf.discord.cache.Index;
import dpf.sp.gpinf.discord.json.DiscordRoot;
import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import iped3.io.IItemBase;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    public static final String INDEX_MIME_TYPE = "application/x-discord-index";
    public static final String CHAT_MIME_TYPE = "application/x-discord-chat";

    public static final String DATA_MIME_TYPE_V2_0 = "data-v20/x-discord-chat";
    public static final String DATA_MIME_TYPE_V2_1 = "data-v21/x-discord-chat";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.parse(INDEX_MIME_TYPE));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream indexFile, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));

        IItemSearcher searcher = context.get(IItemSearcher.class);

        if (searcher != null) {

            String commonQuery = BasicProps.PATH + ":\"AppData/Roaming/discord/cache\" AND " + BasicProps.CARVED + ":false AND NOT "
                    + BasicProps.TYPE + ":slack AND NOT " + BasicProps.LENGTH + ":0 AND NOT " + BasicProps.ISDIR + ":true";
            List<IItemBase> externalFiles = searcher.search(commonQuery + " AND " + BasicProps.NAME + ":f");
            List<IItemBase> dataFiles = searcher.search(commonQuery + " AND " + BasicProps.NAME + ":(\"data_0\"  OR \"data_1\" OR \"data_2\" OR \"data_3\")");

            Index index = new Index(indexFile, dataFiles, externalFiles);

            // Used to identify JSON files containing Discord chats
            CharSequence seq = "messages?limit=50";

            TikaException exception = null;

            for (CacheEntry ce : index.getLst()) {
                if (ce.getKey() != null && ce.getKey().contains(seq)) {

                    try (InputStream is = ce.getResponseDataStream()) {

                        ObjectMapper mapper = new ObjectMapper();
                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                        List<DiscordRoot> discordRoot = mapper.readValue(is, new TypeReference<List<DiscordRoot>>() {
                        });

                        if (discordRoot.isEmpty()) {
                            continue;
                        }

                        String chatName = "Discord Chat id=" + discordRoot.get(0).getId() + " author="
                                + discordRoot.get(0).getAuthor();

                        Metadata chatMeta = new Metadata();
                        chatMeta.set(TikaCoreProperties.TITLE, chatName);
                        chatMeta.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHAT_MIME_TYPE);

                        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, chatMeta);
                        byte[] relatorio = new DiscordHTMLReport().convertToHTML(discordRoot, xhtml);

                        InputStream targetStream = new ByteArrayInputStream(relatorio);
                        extractor.parseEmbedded(targetStream, handler, chatMeta, true);

                    } catch (Exception ex) {
                        if (exception == null) {
                            exception = new TikaException("DiscordParser parsing error.");
                        }
                        exception.addSuppressed(ex);
                    }
                }
            }

            if (exception != null) {
                throw exception;
            }
        }

    }
}