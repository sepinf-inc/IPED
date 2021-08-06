package dpf.sp.gpinf.discord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dpf.sp.gpinf.discord.cache.CacheEntry;
import dpf.sp.gpinf.discord.cache.Index;
import dpf.sp.gpinf.discord.json.DiscordRoot;

import iped3.io.IItemBase;
import iped3.search.IItemSearcher;

/***
 * 
 * @author PCF Campanini
 *
 */
public class DiscordParser extends AbstractParser {

	private static final long serialVersionUID = 1L;
	public static final String INDEX_MIME_TYPE = "application/x-discord-chat";
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

		try {

			IItemSearcher searcher = context.get(IItemSearcher.class);

			if (searcher != null) {

				// List<IItemBase> todos =
				// searcher.search("path:\"AppData/Roaming/discord/cache\" AND carved:false");
				List<IItemBase> externalFiles = searcher.search(
						"path:\"AppData/Roaming/discord/cache\" AND carved:false AND name:\"f_*\" AND NOT type:fileslack");
				List<IItemBase> dataFiles = searcher.search(
						"path:\"AppData/Roaming/discord/cache\" AND carved:false AND (name:\"data_0\"  OR name:\"data_1\"  OR name:\"data_2\" OR name:\"data_3\")  AND NOT type:fileslack");

				Index index = new Index(indexFile, dataFiles, externalFiles);

				// Used to identify JSON files containing Discord chats
				CharSequence seq = "messages?limit=50";

				for (CacheEntry ce : index.getLst()) {
					if (ce.getKey() != null && ce.getKey().contains(seq)) {
						
						InputStream is = ce.getResponseDataStream();
						
						try {
	
							if (is != null) {
								List<DiscordRoot> discordRoot = new ObjectMapper().readValue(is, new TypeReference<List<DiscordRoot>>() {});

								metadata.set(TikaCoreProperties.TITLE, discordRoot.get(0).getId() + ":" + discordRoot.get(0).getAuthor());
								metadata.set(HttpHeaders.CONTENT_TYPE, "text/html");

								XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
								byte[] relatorio = new DiscordHTMLReport().convertToHTML(discordRoot, xhtml);

								InputStream targetStream = new ByteArrayInputStream(relatorio);
								extractor.parseEmbedded(targetStream, handler, metadata, true);
							} 
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			}

		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}
}