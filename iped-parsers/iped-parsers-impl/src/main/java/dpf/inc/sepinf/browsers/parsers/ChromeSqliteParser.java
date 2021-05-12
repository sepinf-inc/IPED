package dpf.inc.sepinf.browsers.parsers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.parsers.jdbc.SQLite3Parser;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para histórico do Chrome
 *
 * https://www.forensicswiki.org/wiki/Google_Chrome
 * https://www.acquireforensics.com/blog/google-chrome-browser-forensics.html
 * http://paper.ijcsns.org/07_book/201609/20160919.pdf
 * 
 * @author Paulo César Herrmann Wanner <herrmann.pchw@dpf.gov.br>
 */
public class ChromeSqliteParser extends AbstractSqliteBrowserParser {

    // Visited sites
    // SELECT datetime(((visits.visit_time/1000000)-11644473600), "unixepoch"),
    // urls.url, urls.title FROM urls, visits WHERE urls.id = visits.url;

    // Downloaded files overview
    // SELECT datetime(((downloads.start_time/1000000)-11644473600), "unixepoch"),
    // downloads.tab_url, downloads.current_path, downloads.received_bytes,
    // downloads.total_bytes FROM downloads;
    // SELECT datetime(((downloads.start_time/1000000)-11644473600), "unixepoch"),
    // downloads.target_path, downloads_url_chains.url, downloads.received_bytes,
    // downloads.total_bytes FROM downloads, downloads_url_chains WHERE downloads.id
    // = downloads_url_chains.id;

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final MediaType CHROME_SQLITE = MediaType.application("x-chrome-sqlite"); //$NON-NLS-1$

    public static final MediaType CHROME_HISTORY = MediaType.application("x-chrome-history"); //$NON-NLS-1$

    public static final MediaType CHROME_HISTORY_REG = MediaType.application("x-chrome-history-registry"); //$NON-NLS-1$

    public static final MediaType CHROME_DOWNLOADS = MediaType.application("x-chrome-downloads"); //$NON-NLS-1$

    public static final MediaType CHROME_DOWNLOADS_REG = MediaType.application("x-chrome-downloads-registry"); //$NON-NLS-1$

    public static final MediaType CHROME_SEARCHES = MediaType.application("x-chrome-searches"); //$NON-NLS-1$

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(CHROME_SQLITE);

    private SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File downloadsFile = tmp.createTemporaryFile();
        File historyFile = tmp.createTemporaryFile();
        File searchFile = tmp.createTemporaryFile();

        try (Connection connection = getConnection(tis, metadata, context)) {

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            List<ResumedVisit> resumedHistory = getResumedHistory(connection, metadata, context);
            List<Visit> history = getHistory(connection, metadata, context);
            List<Download> downloads = getDownloads(connection, metadata, context);
            List<Search> searches = getSearchTerms(connection, metadata, context);

            if (extractor.shouldParseEmbedded(metadata)) {

                try (FileOutputStream tmpDownloadsFile = new FileOutputStream(downloadsFile)) {

                    ToXMLContentHandler downloadsHandler = new ToXMLContentHandler(tmpDownloadsFile, "UTF-8"); //$NON-NLS-1$
                    Metadata downloadsMetadata = new Metadata();
                    downloadsMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_DOWNLOADS.toString());
                    downloadsMetadata.add(Metadata.RESOURCE_NAME_KEY, "Chrome Downloads"); //$NON-NLS-1$
                    downloadsMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
                    downloadsMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$

                    parseChromeDownloads(downloadsHandler, downloadsMetadata, context, downloads);

                    try (FileInputStream fis = new FileInputStream(downloadsFile)) {
                        extractor.parseEmbedded(fis, handler, downloadsMetadata, true);
                    }
                }

                int i = 0;

                for (Download d : downloads) {

                    if (!extractEntries)
                        break;

                    i++;
                    Metadata metadataDownload = new Metadata();

                    metadataDownload.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_DOWNLOADS_REG.toString());
                    metadataDownload.add(Metadata.RESOURCE_NAME_KEY, "Chrome Download Entry " + i); //$NON-NLS-1$
                    metadataDownload.add(ExtraProperties.URL, d.getUrlFromDownload());
                    metadataDownload.add(ExtraProperties.LOCAL_PATH, d.getDownloadedLocalPath());
                    metadataDownload.set(TikaCoreProperties.CREATED, d.getDownloadedDate());
                    metadataDownload.set(ExtraProperties.DOWNLOAD_DATE, d.getDownloadedDate());
                    if (d.getTotalBytes() != null)
                        metadataDownload.add(ExtraProperties.DOWNLOAD_TOTAL_BYTES, d.getTotalBytes().toString());
                    if (d.getReceivedBytes() != null)
                        metadataDownload.add(ExtraProperties.DOWNLOAD_RECEIVED_BYTES, d.getReceivedBytes().toString());
                    metadataDownload.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(0));
                    metadataDownload.set(BasicProps.LENGTH, "");

                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataDownload, true);
                }

                try (FileOutputStream tmpHistoryFile = new FileOutputStream(historyFile)) {

                    ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-8"); //$NON-NLS-1$
                    Metadata historyMetadata = new Metadata();
                    historyMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_HISTORY.toString());
                    historyMetadata.add(Metadata.RESOURCE_NAME_KEY, "Chrome History"); //$NON-NLS-1$
                    historyMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                    historyMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$

                    parseChromeResumedHistory(historyHandler, historyMetadata, context, resumedHistory);

                    try (FileInputStream fis = new FileInputStream(historyFile)) {
                        extractor.parseEmbedded(fis, handler, historyMetadata, true);
                    }
                }

                i = 0;

                for (Visit h : history) {

                    if (!extractEntries)
                        break;

                    i++;
                    Metadata metadataHistory = new Metadata();

                    metadataHistory.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_HISTORY_REG.toString());
                    metadataHistory.add(Metadata.RESOURCE_NAME_KEY, "Chrome History Entry " + i); //$NON-NLS-1$
                    metadataHistory.add(TikaCoreProperties.TITLE, h.getTitle());
                    metadataHistory.set(ExtraProperties.ACCESSED, h.getVisitDate());
                    metadataHistory.set(ExtraProperties.VISIT_DATE, h.getVisitDate());
                    metadataHistory.add(ExtraProperties.URL, h.getUrl());
                    metadataHistory.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(1));
                    metadataHistory.set(BasicProps.LENGTH, "");

                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataHistory, true);
                }

                try (FileOutputStream tmpSearchesFile = new FileOutputStream(searchFile)) {

                    ToXMLContentHandler searchesHandler = new ToXMLContentHandler(tmpSearchesFile, "UTF-8"); //$NON-NLS-1$
                    Metadata searchesMetadata = new Metadata();
                    searchesMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, CHROME_SEARCHES.toString());
                    searchesMetadata.add(Metadata.RESOURCE_NAME_KEY, "Chrome Searches"); //$NON-NLS-1$
                    searchesMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
                    searchesMetadata.set(BasicProps.HASCHILD, "false"); //$NON-NLS-1$

                    parseChromeSearches(searchesHandler, searchesMetadata, context, searches);

                    try (FileInputStream fis = new FileInputStream(searchFile)) {
                        extractor.parseEmbedded(fis, handler, searchesMetadata, true);
                    }
                }
            }

        } catch (Exception e) {

            sqliteParser.parse(tis, handler, metadata, context);

            throw new TikaException("SQLite parsing exception", e); //$NON-NLS-1$

        } finally {
            tmp.close();
        }
    }

    private void parseChromeDownloads(ContentHandler handler, Metadata metadata, ParseContext context,
            List<Download> downloads) throws IOException, SAXException, TikaException {

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
            xHandler.characters("DOWNLOAD DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("DOWNLOADED FILE"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("RECEIVED BYTES"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("TOTAL BYTES"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$

            int i = 1;

            for (Download d : downloads) {
                xHandler.startElement("tr"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDownloadedDateAsString());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDownloadedLocalPath());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getReceivedBytes() != null ? d.getReceivedBytes().toString() : "-"); //$NON-NLS-1$
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getTotalBytes() != null ? d.getTotalBytes().toString() : "-"); //$NON-NLS-1$
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getUrlFromDownload());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }

            xHandler.endElement("table"); //$NON-NLS-1$

            xHandler.endDocument();

        } finally {
            if (xHandler != null)
                xHandler.endDocument();
        }
    }

    private void parseChromeResumedHistory(ContentHandler handler, Metadata metadata, ParseContext context,
            List<ResumedVisit> resumedHistory) throws IOException, SAXException, TikaException {

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

            for (ResumedVisit h : resumedHistory) {

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

        } finally {
            if (xHandler != null)
                xHandler.endDocument();
        }
    }

    private void parseChromeSearches(ContentHandler handler, Metadata metadata, ParseContext context,
            List<Search> searches) throws IOException, SAXException, TikaException {

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
            xHandler.characters("Chrome Searches Terms"); //$NON-NLS-1$
            xHandler.endElement("h2"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$
            xHandler.startElement("br"); //$NON-NLS-1$

            xHandler.startElement("table"); //$NON-NLS-1$

            xHandler.startElement("tr"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters(""); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("LAST SEARCH DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("SEARCH TERMS"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("TITLE"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$

            int i = 1;

            for (Search s : searches) {
                xHandler.startElement("tr"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(s.getLastVisitDateAsString());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(s.getTerms());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(s.getTitle());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(s.getUrl());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.endElement("tr"); //$NON-NLS-1$

                i++;
            }

            xHandler.endElement("table"); //$NON-NLS-1$

            xHandler.endDocument();

        } finally {
            if (xHandler != null)
                xHandler.endDocument();
        }
    }

    protected List<ResumedVisit> getResumedHistory(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<ResumedVisit> resumedHistory = new LinkedList<ResumedVisit>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // The chrome visits.visit_time is in (the number of) microseconds since January
            // 1, 1601 UTC
            // java Date use epoch time in milliseconds
            String sql = "SELECT urls.id, urls.title, urls.url, urls.visit_count, ((urls.last_visit_time/1000)-11644473600000) " //$NON-NLS-1$
                    + "FROM urls " //$NON-NLS-1$
                    + "ORDER BY urls.visit_count DESC;"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                resumedHistory.add(new ResumedVisit(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getLong(5)));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return resumedHistory;
    }

    protected List<Visit> getHistory(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<Visit> history = new LinkedList<Visit>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // The chrome visits.visit_time is in (the number of) microseconds since January
            // 1, 1601 UTC
            // java Date use epoch time in milliseconds
            String sql = "SELECT visits.id, urls.title, ((visits.visit_time/1000)-11644473600000), urls.url " //$NON-NLS-1$
                    + "FROM urls, visits " //$NON-NLS-1$
                    + "WHERE urls.id = visits.url;"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                history.add(new Visit(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return history;
    }

    protected List<Download> getDownloads(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<Download> downloads = new LinkedList<Download>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // The Chrome downloads.start_time is in (the number of) microseconds since
            // January 1, 1601 UTC
            // java Date use epoch time in milliseconds
            ResultSet rs = null;
            try {
                String sql = "SELECT downloads.id, ((downloads.start_time/1000)-11644473600000), downloads_url_chains.url, downloads.current_path, downloads.received_bytes, downloads.total_bytes " //$NON-NLS-1$
                        + "FROM downloads, downloads_url_chains WHERE downloads.id = downloads_url_chains.id;"; //$NON-NLS-1$
                rs = st.executeQuery(sql);
            } catch (Exception e) {
                // Old Chrome versions
                String sql = "SELECT downloads.id, ((downloads.start_time/1000)-11644473600000), downloads.url, downloads.full_path, downloads.received_bytes, downloads.total_bytes " //$NON-NLS-1$
                        + "FROM downloads;"; //$NON-NLS-1$
                rs = st.executeQuery(sql);
            }

            while (rs.next()) {
                downloads.add(new Download(String.valueOf(rs.getLong(1)), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getLong(6), rs.getLong(5)));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return downloads;
    }

    protected List<Search> getSearchTerms(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<Search> searches = new LinkedList<Search>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // The chrome visits.visit_time is in (the number of) microseconds since January
            // 1, 1601 UTC
            // java Date use epoch time in milliseconds
            String sql = "SELECT urls.id, ((urls.last_visit_time/1000)-11644473600000), term, urls.title, urls.url " //$NON-NLS-1$
                    + "FROM urls, keyword_search_terms " //$NON-NLS-1$
                    + "WHERE urls.id = keyword_search_terms.url_id " //$NON-NLS-1$
                    + "ORDER BY urls.last_visit_time DESC;"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                searches.add(
                        new Search(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5)));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return searches;
    }

//    public static void main(String[] args) {
//
//        try {
//            String filepath = "/home/herrmann/Documents/BrowsersArtifacts/History"; //$NON-NLS-1$
//            InputStream input = new FileInputStream(filepath);
//            ChromeSqliteParser parser = new ChromeSqliteParser();
//            ParseContext context = new ParseContext();
//            ToXMLContentHandler handler = new ToXMLContentHandler(new FileOutputStream("/tmp/saida.html"), "UTF-8"); //$NON-NLS-1$
//            Metadata metadata = new Metadata();
//            metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
//                    MediaType.application("x-chrome-sqlite").toString()); //$NON-NLS-1$
//            context.set(Parser.class, parser);
//
//            parser.parse(input, handler, metadata, context);
//
//        } catch (Exception e1) {
//            e1.printStackTrace();
//        }
//
//    }
}
