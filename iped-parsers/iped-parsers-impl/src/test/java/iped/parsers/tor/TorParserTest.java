package iped.parsers.tor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import iped.properties.ExtraProperties;
import junit.framework.TestCase;
import static org.junit.Assert.*;

public class TorParserTest extends TestCase {
	
	private static InputStream getStream(String name) {
		return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
	}
	
	@Test
	public void testTorParser1() throws IOException, SAXException, TikaException {
		TorTcParser parser = new TorTcParser();
		Metadata metadata = new Metadata();
		ContentHandler handler = new BodyContentHandler();
		ParseContext context = new ParseContext();
		parser.getSupportedTypes(context);
		try (InputStream stream = getStream("test-files/testTor1")) {
			parser.parse(stream, handler, metadata, context);
			assertEquals(metadata.get(TorTcParser.TORTC_BUILD_FLAGS), "IS_INTERNAL,NEED_CAPACITY");
			assertEquals(metadata.get(TorTcParser.TORTC_PURPOSE), "HS_CLIENT_REND");
			assertEquals(metadata.get(TorTcParser.TORTC_REND_QUERY), "facebookwkhpilnemxj7asaniu7vnjjbiltxjqhye3mhbshg7kx5tfyd");
			assertEquals(metadata.get(TorTcParser.TORTC_TIME_CREATED), "2023-07-03T17:05:24.214215Z");
			assertEquals(metadata.get(TorTcParser.TORTC_SOCKS_USERNAME).substring(1,metadata.get(TorTcParser.TORTC_SOCKS_USERNAME).length()-1), "facebookwkhpilnemxj7asaniu7vnjjbiltxjqhye3mhbshg7kx5tfyd.onion");
			assertEquals(metadata.get(TorTcParser.TORTC_SOCKS_PASSWORD).substring(1,metadata.get(TorTcParser.TORTC_SOCKS_PASSWORD).length()-2), "8b855a9b98c96ce9b877a17397d59945");
		}
	}
	
	@Test
	public void testTorParser2() throws IOException, SAXException, TikaException {
		TorTcParser parser = new TorTcParser();
		Metadata metadata = new Metadata();
		ContentHandler handler = new BodyContentHandler();
		ParseContext context = new ParseContext();
		parser.getSupportedTypes(context);
		try (InputStream stream = getStream("test-files/testTor2")) {
			parser.parse(stream, handler, metadata, context);
			assertEquals(metadata.get(TorTcParser.TORTC_BUILD_FLAGS), "IS_INTERNAL,NEED_CAPACITY");
			assertEquals(metadata.get(TorTcParser.TORTC_PURPOSE), "HS_CLIENT_REND");
			assertEquals(metadata.get(TorTcParser.TORTC_REND_QUERY), "facebook26qderizo52pigg5y4a2jsdhqz4odvvusaij4yhxehqngqad");
			assertEquals(metadata.get(TorTcParser.TORTC_TIME_CREATED), "2023-07-03T17:04:59.965391Z");
			assertEquals(metadata.get(TorTcParser.TORTC_SOCKS_USERNAME).substring(1,metadata.get(TorTcParser.TORTC_SOCKS_USERNAME).length()-1), "facebookwkhpilnemxj7asaniu7vnjjbiltxjqhye3mhbshg7kx5tfyd.onion");
			assertEquals(metadata.get(TorTcParser.TORTC_SOCKS_PASSWORD).substring(1,metadata.get(TorTcParser.TORTC_SOCKS_PASSWORD).length()-2), "8b855a9b98c96ce9b877a17397d59945");
		}
	}
}