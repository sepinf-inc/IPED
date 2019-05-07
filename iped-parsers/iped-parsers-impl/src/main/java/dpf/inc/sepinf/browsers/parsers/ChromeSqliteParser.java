package dpf.inc.sepinf.browsers.parsers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para histórico do Chrome
 *
 * https://www.forensicswiki.org/wiki/Google_Chrome
 * https://www.acquireforensics.com/blog/google-chrome-browser-forensics.html
 * 
 * @author Paulo César Herrmann Wanner <herrmann.pchw@dpf.gov.br>
 */
public class ChromeSqliteParser extends AbstractParser {

	// Visited sites
	//SELECT datetime(((visits.visit_time/1000000)-11644473600), "unixepoch"), urls.url, urls.title FROM urls, visits WHERE urls.id = visits.url;
	
	// Downloaded files overview
	//SELECT datetime(((downloads.start_time/1000000)-11644473600), "unixepoch"), downloads.tab_url, downloads.current_path, downloads.received_bytes, downloads.total_bytes FROM downloads;	
	//SELECT datetime(((downloads.start_time/1000000)-11644473600), "unixepoch"), downloads.target_path, downloads_url_chains.url, downloads.received_bytes, downloads.total_bytes FROM downloads, downloads_url_chains WHERE downloads.id = downloads_url_chains.id;
	
	public static final MediaType CHROME_SQLITE = MediaType.application("x-chrome-sqlite"); //$NON-NLS-1$
	
	public static final MediaType CHROME_HISTORY = MediaType.application("x-chrome-history"); //$NON-NLS-1$
	
	public static final MediaType CHROME_HISTORY_REG = MediaType.application("x-chrome-history-registry"); //$NON-NLS-1$
	
	public static final MediaType CHROME_DOWNLOADS = MediaType.application("x-chrome-downloads"); //$NON-NLS-1$
	
	public static final MediaType CHROME_DOWNLOADS_REG = MediaType.application("x-chrome-downloads-registry"); //$NON-NLS-1$
	
	private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(CHROME_SQLITE);
	
	private static final String SQLITE_CLASS_NAME = "org.sqlite.JDBC"; //$NON-NLS-1$
	
	private Connection connection;

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
			throws IOException, SAXException, TikaException {
		
		TemporaryResources tmp = new TemporaryResources();
		File downloadsFile = tmp.createTemporaryFile();
		File historyFile = tmp.createTemporaryFile();

		try {
			
			EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
			connection = getConnection(stream, metadata, context);
			
			List<ChromeVisits> resumedHistory = getResumedHistory(connection, metadata, context);
			List<ChromeHistory> history = getHistory(connection, metadata, context);
			List<ChromeDownloads> downloads = getDownloads(connection, metadata, context);
			
			
			if (extractor.shouldParseEmbedded(metadata)) {
				
				try (FileOutputStream tmpDownloadsFile = new FileOutputStream(downloadsFile)) {
					
					ToXMLContentHandler downloadsHandler = new ToXMLContentHandler(tmpDownloadsFile, "UTF-8"); //$NON-NLS-1$
					Metadata downloadsMetadata = new Metadata();
					downloadsMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_DOWNLOADS.toString());
					downloadsMetadata.add(Metadata.RESOURCE_NAME_KEY, "Chrome Downloads"); //$NON-NLS-1$
					downloadsMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
					downloadsMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$
					
					parseChromeDownloads(stream, downloadsHandler, downloadsMetadata, context, downloads);
					
					try (FileInputStream fis = new FileInputStream(downloadsFile)) {
						extractor.parseEmbedded(fis, handler, downloadsMetadata, true);
					}
				}

				int i = 0;
				
				for (ChromeDownloads d : downloads) {
					
					i++;
					Metadata metadataDownload = new Metadata();
					
					metadataDownload.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_DOWNLOADS_REG.toString()); 
					metadataDownload.add(Metadata.RESOURCE_NAME_KEY, "Chrome Download Entry " + i); //$NON-NLS-1$
					metadataDownload.add(TikaCoreProperties.IDENTIFIER, d.getUrlFromDownload());
					metadataDownload.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, d.getDownloadedLocalPath());
					metadataDownload.set(TikaCoreProperties.CREATED, d.getDownloadedDate());
					metadataDownload.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(0));
					
					extractor.parseEmbedded(new EmptyInputStream(), handler, metadataDownload, true);
				}
				
				try (FileOutputStream tmpHistoryFile = new FileOutputStream(historyFile)) {
			
					ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-8"); //$NON-NLS-1$
					Metadata historyMetadata = new Metadata();
					historyMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_HISTORY.toString());
					historyMetadata.add(Metadata.RESOURCE_NAME_KEY, "Chrome History"); //$NON-NLS-1$
					historyMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
					historyMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$
					
					parseChromeResumedHistory(stream, historyHandler, historyMetadata, context, resumedHistory);
					
					try (FileInputStream fis = new FileInputStream(historyFile)) {
						extractor.parseEmbedded(fis, handler, historyMetadata, true);
					}
				}
				
				i = 0;
				
				for (ChromeHistory h : history) {
					
					i++;
					Metadata metadataHistory = new Metadata();
					
					metadataHistory.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_HISTORY_REG.toString()); 
					metadataHistory.add(Metadata.RESOURCE_NAME_KEY, "Chrome History Entry " + i); //$NON-NLS-1$
					metadataHistory.add(TikaCoreProperties.TITLE, h.getTitle());
					metadataHistory.set(TikaCoreProperties.CREATED, h.getVisitDate());
					metadataHistory.add(TikaCoreProperties.IDENTIFIER, h.getUrl());
					metadataHistory.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(1));
					
					extractor.parseEmbedded(new EmptyInputStream(), handler, metadataHistory, true);
				}
			}
	
				
		} catch (SQLException e) {
			throw new TikaException("SQLite parsing exception", e); //$NON-NLS-1$
		} finally {
			tmp.close();
		}
	}
	
	private void parseChromeDownloads(InputStream stream, ContentHandler handler, Metadata metadata,
			ParseContext context, List<ChromeDownloads> downloads) 
					throws IOException, SAXException, TikaException {
		
		XHTMLContentHandler xHandler = null;

        try {
        	
            xHandler = new XHTMLContentHandler(handler, metadata);
            xHandler.startDocument();
            
            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$
            
            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("Chrome Downloaded Files"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            
            xHandler.startElement("table"); //$NON-NLS-1$
            
            xHandler.startElement("tr"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("DOWNLOADED FILE"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("DOWNLOAD DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$
            
            int i = 1;
            
            for (ChromeDownloads d : downloads) {
            	xHandler.startElement("tr"); //$NON-NLS-1$
            	
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getUrlFromDownload());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDownloadedLocalPath());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDownloadedDateAsString());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }
            
            xHandler.endElement("table"); //$NON-NLS-1$           
            
            xHandler.endDocument();            
            
        } finally{
        	if(xHandler != null)
        		xHandler.endDocument();
        	try {
                close();
            } catch (Exception e) {
                //swallow
            }
        }

	
	}
	
	private void parseChromeResumedHistory(InputStream stream, ContentHandler handler, Metadata metadata,
			ParseContext context, List<ChromeVisits> resumedHistory) 
					throws IOException, SAXException, TikaException {
		
		XHTMLContentHandler xHandler = null;

        try {
        	
            xHandler = new XHTMLContentHandler(handler, metadata);
            xHandler.startDocument();
            
            xHandler.startElement("head"); //$NON-NLS-1$
            xHandler.startElement("style"); //$NON-NLS-1$
            xHandler.characters("table {border-collapse: collapse;} table, td, th {border: 1px solid black;}"); //$NON-NLS-1$
            xHandler.endElement("style"); //$NON-NLS-1$
            xHandler.endElement("head"); //$NON-NLS-1$
            
            xHandler.startElement("h2 align=center"); //$NON-NLS-1$
            xHandler.characters("Chrome Visited Sites Resumed History"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            
            xHandler.startElement("table"); //$NON-NLS-1$
            
            xHandler.startElement("tr"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("TITLE"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("VISIT COUNT"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("LAST VISIT DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$
            
            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$
        	
            int i = 1;
            
            for (ChromeVisits h : resumedHistory) {
            	
xHandler.startElement("tr"); //$NON-NLS-1$
            	
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(h.getTitle());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Long.toString(h.getVisitCount()));
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(h.getLastVisitDateAsString());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(h.getUrl());
                xHandler.endElement("td"); //$NON-NLS-1$
                
                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }
            
            xHandler.endElement("table"); //$NON-NLS-1$           
            
            xHandler.endDocument();            
            
        } finally{
        	if(xHandler != null)
        		xHandler.endDocument();
        	try {
                close();
            } catch (Exception e) {
                //swallow
            }
        }
	}
	
    protected Connection getConnection(InputStream stream, Metadata metadata, ParseContext context) throws IOException, TikaException {
        String connectionString = getConnectionString(stream, metadata, context);

        Connection connection = null;
        try {
            Class.forName(getJDBCClassName());
        } catch (ClassNotFoundException e) {
            throw new TikaException(e.getMessage());
        }
        try{
            connection = DriverManager.getConnection(connectionString);
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        return connection;
    }
    
    protected String getConnectionString(InputStream is,
                                               Metadata metadata, ParseContext parseContext) throws IOException {
    	File dbFile = TikaInputStream.get(is).getFile();
        return "jdbc:sqlite:"+dbFile.getAbsolutePath(); //$NON-NLS-1$
    }
    
    protected String getJDBCClassName() {
        return SQLITE_CLASS_NAME;
    }
    
    protected List<ChromeVisits> getResumedHistory(Connection connection, Metadata metadata,
            ParseContext context) throws SQLException {
		List<ChromeVisits> resumedHistory = new LinkedList<ChromeVisits>();
		
		Statement st = null;
		try {
			st = connection.createStatement();
			// The chrome visits.visit_time is in (the number of) microseconds since January 1, 1601 UTC
			// java Date use epoch time in milliseconds
			String sql = "SELECT urls.id, urls.title, urls.url, urls.visit_count, ((urls.last_visit_time/1000)-11644473600000) " //$NON-NLS-1$
							+ "FROM urls " //$NON-NLS-1$
							+ "ORDER BY urls.visit_count DESC;"; //$NON-NLS-1$
			ResultSet rs = st.executeQuery(sql);
		
			while (rs.next()) {
				resumedHistory.add(new ChromeVisits(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5)));
			}
		} finally {
			if (st != null)
			st.close();
		}
		return resumedHistory;
	}
    
    protected List<ChromeHistory> getHistory(Connection connection, Metadata metadata,
            ParseContext context) throws SQLException {
		List<ChromeHistory> history = new LinkedList<ChromeHistory>();
		
		Statement st = null;
		try {
			st = connection.createStatement();
			// The chrome visits.visit_time is in (the number of) microseconds since January 1, 1601 UTC
			// java Date use epoch time in milliseconds
			String sql = "SELECT visits.id, urls.title, ((visits.visit_time/1000)-11644473600000), urls.url " //$NON-NLS-1$
							+ "FROM urls, visits " //$NON-NLS-1$
							+ "WHERE urls.id = visits.url;"; //$NON-NLS-1$
			ResultSet rs = st.executeQuery(sql);
		
			while (rs.next()) {
				history.add(new ChromeHistory(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
			}
		} finally {
			if (st != null)
			st.close();
		}
		return history;
	}
    
    protected List<ChromeDownloads> getDownloads(Connection connection, Metadata metadata,
            ParseContext context) throws SQLException {
		List<ChromeDownloads> downloads = new LinkedList<ChromeDownloads>();
		
		Statement st = null;
		try {
			st = connection.createStatement();
			// The Chrome downloads.start_time is in (the number of) microseconds since January 1, 1601 UTC
			// java Date use epoch time in milliseconds
			String sql = "SELECT downloads.id, ((downloads.start_time/1000)-11644473600000), downloads.tab_url, downloads.current_path, downloads.received_bytes, downloads.total_bytes " //$NON-NLS-1$
							+ "FROM downloads;"; //$NON-NLS-1$
			ResultSet rs = st.executeQuery(sql);
		
			while (rs.next()) {
				downloads.add(new ChromeDownloads(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
			}
		} finally {
			if (st != null)
			st.close();
		}
		return downloads;
	}
    
    protected void close() throws SQLException, IOException {
        connection.close();
    }
    
    public static void main(String[] args) {

		try {
			String filepath = "/home/herrmann/Documents/BrowsersArtifacts/History"; //$NON-NLS-1$
			InputStream input = new FileInputStream(filepath);
			ChromeSqliteParser parser = new ChromeSqliteParser();
			ParseContext context = new ParseContext();
			ToXMLContentHandler handler = new ToXMLContentHandler(new FileOutputStream("/tmp/saida.html"), "UTF-8"); //$NON-NLS-1$
			Metadata metadata = new Metadata();
			metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MediaType.application("x-chrome-sqlite").toString()); //$NON-NLS-1$
			context.set(Parser.class, parser);

			parser.parse(input, handler, metadata, context);

		} catch (Exception e1) {
			e1.printStackTrace();
		}

	}
}
