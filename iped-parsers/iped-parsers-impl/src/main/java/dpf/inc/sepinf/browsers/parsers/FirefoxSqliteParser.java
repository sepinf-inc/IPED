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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.openjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para histórico do Firefox
 *
 * https://developer.mozilla.org/en-US/docs/Mozilla/Tech/Places/Database
 * https://forensicswiki.org/wiki/Mozilla_Firefox
 * 
 * @author Paulo César Herrmann Wanner <herrmann.pchw@dpf.gov.br>
 */
public class FirefoxSqliteParser extends AbstractParser {

    public static final MediaType MOZ_PLACES = MediaType.application("x-firefox-places"); //$NON-NLS-1$

    public static final MediaType MOZ_HISTORY = MediaType.application("x-firefox-history"); //$NON-NLS-1$

    public static final MediaType MOZ_HISTORY_REG = MediaType.application("x-firefox-history-registry"); //$NON-NLS-1$

    public static final MediaType MOZ_BOOKMARKS = MediaType.application("x-firefox-bookmarks"); //$NON-NLS-1$

    public static final MediaType MOZ_BOOKMARKS_REG = MediaType.application("x-firefox-bookmarks-registry"); //$NON-NLS-1$

    public static final MediaType MOZ_DOWNLOADS = MediaType.application("x-firefox-downloads"); //$NON-NLS-1$

    public static final MediaType MOZ_DOWNLOADS_REG = MediaType.application("x-firefox-downloads-registry"); //$NON-NLS-1$

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(MOZ_PLACES);

    private static final String SQLITE_CLASS_NAME = "org.sqlite.JDBC"; //$NON-NLS-1$

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        File bookmarksFile = tmp.createTemporaryFile();
        File historyFile = tmp.createTemporaryFile();
        File downloadFile = tmp.createTemporaryFile();
        File dbFile = TikaInputStream.get(stream, tmp).getFile();
        
        try (Connection connection = getConnection(dbFile)){

            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            List<ResumedVisit> resumedHistory = getResumedHistory(connection, metadata, context);
            List<Visit> history = getHistory(connection, metadata, context);
            List<FirefoxMozBookmark> bookmarks = getBookmarks(connection, metadata, context);
            List<Download> downloads = getDownloads(connection, metadata, context);

            if (extractor.shouldParseEmbedded(metadata)) {

                /* BOOKMARK */
                try (FileOutputStream tmpBookmarksFile = new FileOutputStream(bookmarksFile)) {

                    ToXMLContentHandler bookmarksHandler = new ToXMLContentHandler(tmpBookmarksFile, "UTF-8"); //$NON-NLS-1$
                    Metadata bookmarksMetadata = new Metadata();
                    bookmarksMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MOZ_BOOKMARKS.toString());
                    bookmarksMetadata.add(Metadata.RESOURCE_NAME_KEY, "Firefox Bookmarks"); //$NON-NLS-1$
                    bookmarksMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(0));
                    bookmarksMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$

                    parseFirefoxBookmarks(stream, bookmarksHandler, bookmarksMetadata, context, bookmarks);

                    try (FileInputStream fis = new FileInputStream(bookmarksFile)) {
                        extractor.parseEmbedded(fis, handler, bookmarksMetadata, true);
                    }
                }

                int i = 0;
                for (FirefoxMozBookmark b : bookmarks) {

                    i++;
                    Metadata metadataBookmark = new Metadata();

                    metadataBookmark.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MOZ_BOOKMARKS_REG.toString());
                    metadataBookmark.add(Metadata.RESOURCE_NAME_KEY, "Firefox Bookmark Entry " + i); //$NON-NLS-1$
                    metadataBookmark.add(TikaCoreProperties.TITLE, b.getTitle());
                    metadataBookmark.set(TikaCoreProperties.CREATED, b.getDateAdded());
                    metadataBookmark.set(TikaCoreProperties.MODIFIED, b.getLastModified());
                    metadataBookmark.add(TikaCoreProperties.IDENTIFIER, b.getUrl());
                    metadataBookmark.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(0));

                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataBookmark, true);
                }

                /* HISTORY */
                try (FileOutputStream tmpHistoryFile = new FileOutputStream(historyFile)) {

                    ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-8"); //$NON-NLS-1$
                    Metadata historyMetadata = new Metadata();
                    historyMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MOZ_HISTORY.toString());
                    historyMetadata.add(Metadata.RESOURCE_NAME_KEY, "Firefox History"); //$NON-NLS-1$
                    historyMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                    historyMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$

                    parseFirefoxResumedHistory(stream, historyHandler, historyMetadata, context, resumedHistory);

                    try (FileInputStream fis = new FileInputStream(historyFile)) {
                        extractor.parseEmbedded(fis, handler, historyMetadata, true);
                    }
                }

                i = 0;
                for (Visit h : history) {

                    i++;
                    Metadata metadataHistory = new Metadata();

                    metadataHistory.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MOZ_HISTORY_REG.toString());
                    metadataHistory.add(Metadata.RESOURCE_NAME_KEY, "Firefox History Entry " + i); //$NON-NLS-1$
                    metadataHistory.add(TikaCoreProperties.TITLE, h.getTitle());
                    metadataHistory.set(TikaCoreProperties.CREATED, h.getVisitDate());
                    metadataHistory.add(TikaCoreProperties.IDENTIFIER, h.getUrl());
                    metadataHistory.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(1));

                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataHistory, true);
                }

                /* DOWNLOAD */
                try (FileOutputStream tmpDownloadFile = new FileOutputStream(downloadFile)) {

                    ToXMLContentHandler downloadsHandler = new ToXMLContentHandler(tmpDownloadFile, "UTF-8"); //$NON-NLS-1$
                    Metadata downloadsMetadata = new Metadata();
                    downloadsMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MOZ_DOWNLOADS.toString());
                    downloadsMetadata.add(Metadata.RESOURCE_NAME_KEY, "Firefox Downloads"); //$NON-NLS-1$
                    downloadsMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(2));
                    downloadsMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$

                    parseFirefoxDownloads(stream, downloadsHandler, downloadsMetadata, context, downloads);

                    try (FileInputStream fis = new FileInputStream(downloadFile)) {
                        extractor.parseEmbedded(fis, handler, downloadsMetadata, true);
                    }
                }

                i = 0;
                for (Download d : downloads) {

                    i++;
                    Metadata metadataDownload = new Metadata();

                    metadataDownload.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, MOZ_DOWNLOADS_REG.toString());
                    metadataDownload.add(Metadata.RESOURCE_NAME_KEY, "Firefox Download Entry " + i); //$NON-NLS-1$
                    metadataDownload.add(TikaCoreProperties.IDENTIFIER, d.getUrlFromDownload());
                    metadataDownload.add(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, d.getDownloadedLocalPath());
                    metadataDownload.set(TikaCoreProperties.CREATED, d.getDownloadedDate());
                    metadataDownload.add(ExtraProperties.PARENT_VIRTUAL_ID, String.valueOf(2));

                    extractor.parseEmbedded(new EmptyInputStream(), handler, metadataDownload, true);
                }
            }

        } catch (SQLException e) {
            throw new TikaException("SQLite parsing exception", e); //$NON-NLS-1$
        } finally {
            tmp.close();
        }
    }

    private void parseFirefoxBookmarks(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, List<FirefoxMozBookmark> bookmarks) throws IOException, SAXException, TikaException {

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
            xHandler.characters("Mozilla Firefox Bookmarked Sites"); //$NON-NLS-1$
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
            xHandler.characters("ADDED DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("LAST MODIFIED DATE (UTC)"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.endElement("tr"); //$NON-NLS-1$

            int i = 1;

            for (FirefoxMozBookmark b : bookmarks) {
                xHandler.startElement("tr"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(Integer.toString(i));
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(b.getTitle());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(b.getDateAddedAsString());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(b.getLastModifiedAsString());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(b.getUrl());
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

    private void parseFirefoxDownloads(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, List<Download> downloads) throws IOException, SAXException, TikaException {

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
            xHandler.characters("Mozilla Firefox Downloads"); //$NON-NLS-1$
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
            xHandler.characters("URL"); //$NON-NLS-1$
            xHandler.endElement("th"); //$NON-NLS-1$

            xHandler.startElement("th"); //$NON-NLS-1$
            xHandler.characters("PATH"); //$NON-NLS-1$
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
                xHandler.characters(d.getUrlFromDownload());
                xHandler.endElement("td"); //$NON-NLS-1$

                xHandler.startElement("td"); //$NON-NLS-1$
                xHandler.characters(d.getDownloadedLocalPath());
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

    private void parseFirefoxResumedHistory(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, List<ResumedVisit> resumedHistory) throws IOException, SAXException, TikaException {

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
            xHandler.characters("Mozilla Firefox Visited Sites Resumed History"); //$NON-NLS-1$
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
    
    protected Connection getConnection(File dbFile) throws IOException, TikaException {
        String connectionString = getConnectionString(dbFile);
        Connection connection = null;
        try {
            Class.forName(getJDBCClassName());
        } catch (ClassNotFoundException e) {
            throw new TikaException(e.getMessage());
        }
        try {
            connection = DriverManager.getConnection(connectionString);
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        return connection;
    }

    protected String getConnectionString(File dbFile) throws IOException {
        return "jdbc:sqlite:" + dbFile.getAbsolutePath(); //$NON-NLS-1$
    }

    protected String getJDBCClassName() {
        return SQLITE_CLASS_NAME;
    }

    protected List<ResumedVisit> getResumedHistory(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<ResumedVisit> resumedHistory = new LinkedList<ResumedVisit>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // moz_places save date as Unix epoch time in microseconds
            // java Date use epoch time in milliseconds
            String sql = "SELECT moz_places.id, moz_places.title, moz_places.url, visit_count, latest/1000 " //$NON-NLS-1$
                    + "FROM moz_places " //$NON-NLS-1$
                    + "INNER JOIN (SELECT place_id, MAX(visit_date) AS latest FROM moz_historyvisits GROUP BY place_id) " //$NON-NLS-1$
                    + "ON moz_places.id = place_id ORDER BY moz_places.visit_count DESC;"; //$NON-NLS-1$
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
            // moz_places save date as Unix epoch time in microseconds
            // java Date use epoch time in milliseconds
            String sql = "SELECT moz_places.id, moz_places.title, moz_historyvisits.visit_date/1000, moz_places.url " //$NON-NLS-1$
                    + "FROM moz_places, moz_historyvisits " //$NON-NLS-1$
                    + "WHERE moz_places.id = moz_historyvisits.place_id ORDER BY moz_historyvisits.visit_date;"; //$NON-NLS-1$
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

    protected List<FirefoxMozBookmark> getBookmarks(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<FirefoxMozBookmark> bookmarks = new LinkedList<FirefoxMozBookmark>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // moz_places save date as Unix epoch time in microseconds
            // java Date use epoch time in milliseconds
            String sql = "SELECT moz_bookmarks.id, moz_bookmarks.title, moz_places.url, moz_bookmarks.dateAdded/1000, moz_bookmarks.lastModified/1000 " //$NON-NLS-1$
                    + "FROM moz_places, moz_bookmarks " //$NON-NLS-1$
                    + "WHERE moz_places.id = moz_bookmarks.fk ORDER BY moz_bookmarks.dateAdded;"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                bookmarks.add(new FirefoxMozBookmark(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getLong(5)));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return bookmarks;
    }

    private List<Download> getDownloads(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException, JsonParseException, JsonMappingException, IOException {
        List<Download> downloads = new LinkedList<Download>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // moz_annos save date as Unix epoch time in milliseconds
            // java Date use epoch time in milliseconds
            // moz_annos save information from downloaded files
            String sql = "SELECT moz_places.id, moz_places.url, path.content, attributes.content " //$NON-NLS-1$
                    + "FROM moz_places " //$NON-NLS-1$
                    + "INNER JOIN moz_annos AS path " //$NON-NLS-1$
                    + "ON (moz_places.id = path.place_id AND path.anno_attribute_id = 3) " //$NON-NLS-1$
                    + "INNER JOIN moz_annos AS attributes " //$NON-NLS-1$
                    + "ON (moz_places.id = attributes.place_id AND attributes.anno_attribute_id = 4) "; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                // 1- id, 2- date, 3- url, 4- path
                long id = rs.getLong(1);
                String url = rs.getString(2);
                String path = rs.getString(3);
                String att = rs.getString(4);
                long date = Long.parseLong((att.split(","))[1].split(":")[1]);
                downloads.add(new Download(id, date, url, path));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return downloads;
    }

    public static void main(String[] args) {

        try {
            String filepath = "/tmp/places.sqlite"; //$NON-NLS-1$
            InputStream input = new FileInputStream(filepath);
            FirefoxSqliteParser parser = new FirefoxSqliteParser();
            ParseContext context = new ParseContext();
            ToXMLContentHandler handler = new ToXMLContentHandler(new FileOutputStream("/tmp/saida.html"), "UTF-8"); //$NON-NLS-1$
            Metadata metadata = new Metadata();
            metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                    MediaType.application("x-firefox-places").toString()); //$NON-NLS-1$
            context.set(Parser.class, parser);

            parser.parse(input, handler, metadata, context);

        } catch (Exception e1) {
            e1.printStackTrace();
        }

    }

}