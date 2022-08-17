package iped.parsers.eventtranscript;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLite3Parser;

/**
 * @author Felipe Farias da Costa <felipecostasdc@gmail.com>
 */
public class EventTranscriptParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType EVENT_TRANSCRIPT = MediaType.application("x-event-transcript");

    public static final MediaType EVENT_TRANSCRIPT_HIST = MediaType.application("x-event-transcript-history");
    public static final MediaType EVENT_TRANSCRIPT_HIST_REG = MediaType.application("x-event-transcript-history-registry");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(EVENT_TRANSCRIPT);

    // Option to extract each history event registry as a subitem.
    private boolean extractEntries = true;

    // Fallback parser
    private SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }


    /**
     * Parses EventTranscript.db files
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(stream, tmp);
        File browserHistoryFile = tmp.createTemporaryFile();

        try (Connection connection = getConnection(stream, metadata, context)) {

            historyEntries(connection, metadata, context);


        } catch (Exception e) {

            sqliteParser.parse(stream, handler, metadata, context);
            throw new TikaException("SQLite Win10Timeline parsing exception", e);

        } finally {
            tmp.close();


        }
        
    }

    protected void historyEntries(Connection connection, Metadata metadata, ParseContext context)
            throws SQLException, TikaException {

        try (BrowserHistoryIterable historyEntriesIterable = new BrowserHistoryIterable(connection)) {
            for (BrowserHistoryEntry historyEntry : historyEntriesIterable) {
                // System.out.println(historyEntry.getTimestamp());
            }
        } catch (IOException e) {
            throw new TikaException("SQLite EventTranscript parsing exception", e);
        }
    }


    private class BrowserHistoryIterable implements Iterable<BrowserHistoryEntry>, Closeable {

        ResultSet rs;
        Statement statement;
        public BrowserHistoryIterable(Connection connection) throws SQLException {
            statement = connection.createStatement();
            rs = statement.executeQuery(HISTORY_QUERY);
        }

        @Override
        public Iterator<BrowserHistoryEntry> iterator() {
            return new Iterator<BrowserHistoryEntry>() {

                @Override
                public boolean hasNext() {
                    try {
                        return rs.next();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public BrowserHistoryEntry next() {
                    BrowserHistoryEntry historyEntry = new BrowserHistoryEntry();

                    try {
                        historyEntry.setUserSID(rs.getString("UserSID"));
                        historyEntry.setTimestamp(rs.getString("Timestamp"));
                        historyEntry.setLocale(rs.getString("LocaleName"));
                        historyEntry.setProducer(rs.getString("ProducerIDText"));
                        historyEntry.setTag(rs.getString("TagName"));
                        historyEntry.setFullEventName(rs.getString("FullEventName"));
                        historyEntry.setLoggingBinaryName(rs.getString("LoggingBinaryName"));
                        historyEntry.setFullEventNameHash(rs.getString("FullEventNameHash"));
                        historyEntry.setKeywords(rs.getString("Keywords"));
                        historyEntry.setGroupGUID(rs.getString("GroupGUID"));
                        historyEntry.setIsCore(rs.getString("IsCore"));
                        historyEntry.setCompressedPayloadSize(rs.getString("CompressedPayloadSize"));
                        historyEntry.setJSONPayload(rs.getString("JSONPayload"));
                    } catch (SQLException | ParseException e ) {
                        throw new RuntimeException(e);
                    }
                    return historyEntry;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public void close() throws IOException {
            try {
                rs.close();
                statement.close();
            } catch (SQLException e) {
                // swallow
            }
        }
    }


    /**
     * SQLite query based on
     * https://github.com/EricZimmerman/SQLECmd/blob/master/SQLMap/Maps/Windows_EventTranscriptDB_DataSampling.smap
     */
    private String HISTORY_QUERY = "SELECT events_persisted.sid AS UserSID,"
            + " datetime((events_persisted.timestamp/10000000) - 11644473600, 'unixepoch') AS Timestamp,"
            + " tag_descriptions.locale_name AS LocaleName,"
            + " producers.producer_id_text AS ProducerIDText,"
            + " tag_descriptions.tag_name AS TagName,"
            + " events_persisted.full_event_name AS FullEventName,"
            + " events_persisted.logging_binary_name AS LoggingBinaryName,"
            + " events_persisted.friendly_logging_binary_name AS FriendlyLoggingBinaryName,"
            + " events_persisted.full_event_name_hash AS FullEventNameHash,"
            + " events_persisted.event_keywords AS Keywords,"
            + " provider_groups.group_guid AS GroupGUID,"
            + " CASE"
            + " WHEN events_persisted.is_core = 0 THEN"
            + " 'No'"
            + " WHEN events_persisted.is_core = 1 THEN"
            + " 'Yes' ELSE 'Unknown'"
            + " END AS IsCore,"
            + " events_persisted.compressed_payload_size AS CompressedPayloadSize,"
            + " events_persisted.payload AS JSONPayload"
            + " FROM"
            + " events_persisted"
            + " LEFT JOIN producers ON events_persisted.producer_id = producers.producer_id"
            + " LEFT JOIN event_tags ON events_persisted.full_event_name_hash = event_tags.full_event_name_hash"
            + " LEFT JOIN tag_descriptions ON event_tags.tag_id = tag_descriptions.tag_id"
            + " LEFT JOIN provider_groups ON events_persisted.provider_group_id = provider_groups.group_id"
            + " WHERE"
            + " TagName = 'Browsing History'"
            + " ORDER BY"
            + " events_persisted.timestamp ASC;";
}