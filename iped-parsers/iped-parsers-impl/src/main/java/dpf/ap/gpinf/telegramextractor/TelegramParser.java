package dpf.ap.gpinf.telegramextractor;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Set;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TelegramParser extends SQLite3DBParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2981296547291818873L;
	public static final MediaType TELEGRAM_DB=MediaType.parse("application/x-telegram-db");
	public Set<MediaType> getSupportedTypes(ParseContext context){
	    return MediaType.set(TELEGRAM_DB);
	}
	public static final MediaType TELEGRAM_CHAT = MediaType.parse("application/x-telegram-chat");
	
	private SQLite3Parser sqliteParser = new SQLite3Parser();
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
	throws IOException,SAXException{
		Connection conn= getConnection(stream,metadata,context);
		Extractor e=new Extractor(conn);
		IItemSearcher searcher = context.get(IItemSearcher.class);
		e.setSearcher(searcher);
		e.performExtraction();
		ReportGenerator r=new ReportGenerator();
		EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
		
		for(Chat c:e.chatList) {
			System.out.println("teste telegram");
			byte[] bytes=r.generateChatHtml(c);
			Metadata chatMetadata = new Metadata();
			 chatMetadata.set(TikaCoreProperties.TITLE, c.getName());
             chatMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, TELEGRAM_CHAT.toString());
             chatMetadata.set(ExtraProperties.ITEM_VIRTUAL_ID, Long.toString(c.getId()));
             
             ByteArrayInputStream chatStream = new ByteArrayInputStream(bytes);
             extractor.parseEmbedded(chatStream, handler, chatMetadata, false);
             
		
			
		}
		
	}

}
