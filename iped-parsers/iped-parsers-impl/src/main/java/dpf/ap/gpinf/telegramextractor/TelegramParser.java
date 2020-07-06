package dpf.ap.gpinf.telegramextractor;

import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3DBParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;

public class TelegramParser extends SQLite3DBParser {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2981296547291818873L;
	
	public Set<MediaType> getSupportedTypes(ParseContext context){
	    return MediaType.set(MediaType.parse("application/x-telegram-db"));
	}
	
	private SQLite3Parser sqliteParser = new SQLite3Parser();
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
	throws IOException{
		Connection conn= getConnection(stream,metadata,context);
		Extractor e=new Extractor(conn);
		e.performExtraction();
		
	}

}
