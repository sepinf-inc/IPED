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
import org.apache.tika.sax.ToXMLContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import iped3.util.BasicProps;
import iped3.util.ExtraProperties;

/**
 * Parser para histórico do Safari - SQLite3
 *
 * http://2016.padjo.org/tutorials/sqlite-your-browser-history/
 * https://stackoverflow.com/questions/34167003/what-format-is-the-safari-history-db-history-visits-visit-time-in
 * http://az4n6.blogspot.com/2014/07/safari-and-iphone-internet-history.html
 * 
 * @author Paulo César Herrmann Wanner <herrmann.pchw@dpf.gov.br>
 */
public class SafariSqliteParser extends AbstractSqliteBrowserParser {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final MediaType SAFARI_SQLITE = MediaType.application("x-safari-sqlite"); //$NON-NLS-1$

    public static final MediaType SAFARI_HISTORY = MediaType.application("x-safari-history"); //$NON-NLS-1$

    public static final MediaType SAFARI_HISTORY_REG = MediaType.application("x-safari-history-registry"); //$NON-NLS-1$

    public static final MediaType SAFARI_DOWNLOADS = MediaType.application("x-safari-downloads"); //$NON-NLS-1$

    public static final MediaType SAFARI_DOWNLOADS_REG = MediaType.application("x-safari-downloads-registry"); //$NON-NLS-1$

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(SAFARI_SQLITE);

    private static Logger LOGGER = LoggerFactory.getLogger(SafariSqliteParser.class);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        File historyFile = tmp.createTemporaryFile();
        File dbFile = TikaInputStream.get(stream, tmp).getFile();

        try (Connection connection = getConnection(dbFile)){
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                    new ParsingEmbeddedDocumentExtractor(context));

            List<SafariResumedVisit> resumedHistory = getResumedHistory(connection, metadata, context);
            List<SafariVisit> history = getHistory(connection, metadata, context);

            if (extractor.shouldParseEmbedded(metadata)) {
                try (FileOutputStream tmpHistoryFile = new FileOutputStream(historyFile)) {

                    ToXMLContentHandler historyHandler = new ToXMLContentHandler(tmpHistoryFile, "UTF-8"); //$NON-NLS-1$
                    Metadata historyMetadata = new Metadata();
                    historyMetadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_HISTORY.toString());
                    historyMetadata.add(Metadata.RESOURCE_NAME_KEY, "Safari History"); //$NON-NLS-1$
                    historyMetadata.add(ExtraProperties.ITEM_VIRTUAL_ID, String.valueOf(1));
                    historyMetadata.set(BasicProps.HASCHILD, "true"); //$NON-NLS-1$

                    parseSafariResumedHistory(stream, historyHandler, historyMetadata, context, resumedHistory);

                    try (FileInputStream fis = new FileInputStream(historyFile)) {
                        extractor.parseEmbedded(fis, handler, historyMetadata, true);
                    }
                }

                int i = 0;

                for (SafariVisit h : history) {
                    
                    if(!extractEntries)
                        break;

                    i++;
                    Metadata metadataHistory = new Metadata();

                    metadataHistory.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE, SAFARI_HISTORY_REG.toString());
                    metadataHistory.add(Metadata.RESOURCE_NAME_KEY, "Safari History Entry " + i); //$NON-NLS-1$
                    metadataHistory.add(TikaCoreProperties.TITLE, h.getTitle());
                    metadataHistory.set(ExtraProperties.ACCESSED, h.getVisitDate());
                    metadataHistory.set(ExtraProperties.VISIT_DATE, h.getVisitDate());
                    metadataHistory.add(ExtraProperties.URL, h.getUrl());
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

    private void parseSafariResumedHistory(InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context, List<SafariResumedVisit> resumedHistory)
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
            xHandler.characters("Safari Visited Sites Resumed History"); //$NON-NLS-1$
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

            for (SafariResumedVisit h : resumedHistory) {

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

    protected List<SafariResumedVisit> getResumedHistory(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<SafariResumedVisit> resumedHistory = new LinkedList<SafariResumedVisit>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // The safari history_items.visit_time is in (the number of) seconds since
            // January 1, 2001 UTC
            // To get a decent human value out of it, you must add 978307200 (the epoch for
            // 2001-01-01 00:00:00)
            // https://stackoverflow.com/questions/34167003/what-format-is-the-safari-history-db-history-visits-visit-time-in
            // java Date use epoch time in milliseconds
            String sql = "SELECT history_items.id, title, history_items.url, history_items.visit_count, ((latest + 978307200)*1000) " //$NON-NLS-1$
                    + "FROM history_items " //$NON-NLS-1$
                    + "INNER JOIN (SELECT history_item, title, MAX(visit_time) AS latest FROM history_visits GROUP BY history_item) " //$NON-NLS-1$
                    + "ON history_items.id = history_item ORDER BY history_items.visit_count DESC"; //$NON-NLS-1$

            LOGGER.info("SQL Query: " + sql);
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                // SafariVisits sv1 = new SafariVisits(rs.getLong(1), rs.getString(2),
                // rs.getString(3), rs.getLong(4), rs.getLong(5));
                // SafariVisits sv2 = new SafariVisits(rs.getLong(1), rs.getString(2),
                // rs.getString(3), rs.getLong(4), rs.getLong(5)*1000L);
                // SafariVisits sv3 = new SafariVisits(rs.getLong(1), rs.getString(2),
                // rs.getString(3), rs.getLong(4), rs.getLong(5)/1000);
                // LOGGER.info("ID: " + rs.getLong(1) + " TITLE: " + rs.getString(2) + " URL: "
                // + rs.getString(3) + " VISIT_COUNT: " + rs.getLong(4) + " VISIT_TIME: " +
                // rs.getLong(5));
                // LOGGER.info("SV1: " + sv1.getLastVisitDateAsString());
                // LOGGER.info("SV2: " + sv2.getLastVisitDateAsString());
                // LOGGER.info("SV3: " + sv3.getLastVisitDateAsString());
                resumedHistory.add(new SafariResumedVisit(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5)));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return resumedHistory;
    }

    protected List<SafariVisit> getHistory(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException {
        List<SafariVisit> history = new LinkedList<SafariVisit>();

        Statement st = null;
        try {
            st = connection.createStatement();
            // The safari history_items.visit_time is in (the number of) seconds since
            // January 1, 2001 UTC
            // To get a decent human value out of it, you must add 978307200 (the epoch for
            // 2001-01-01 00:00:00)
            // https://stackoverflow.com/questions/34167003/what-format-is-the-safari-history-db-history-visits-visit-time-in
            // java Date use epoch time in milliseconds
            String sql = "SELECT history_visits.id, title, ((visit_time + 978307200)*1000), url " //$NON-NLS-1$
                    + "FROM history_visits " //$NON-NLS-1$
                    + "INNER JOIN history_items " //$NON-NLS-1$
                    + "ON history_items.id = history_visits.history_item " //$NON-NLS-1$
                    + "ORDER BY visit_time"; //$NON-NLS-1$
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                history.add(new SafariVisit(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return history;
    }

}
