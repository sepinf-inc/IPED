package iped.parsers.ios;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.sqlite.SQLite3DBParser;
import iped.parsers.sqlite.SQLite3Parser;

public class IOSBackupManifestParser extends SQLite3DBParser {

    private static final long serialVersionUID = 1L;

    public static final MediaType IOS_BACKUP_MANIFEST =
            MediaType.application("x-ios-backup-manifest-db"); //$NON-NLS-1$

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(IOS_BACKUP_MANIFEST);
    private static final String META_PREFIX = "iosBackup:"; //$NON-NLS-1$

    private final IOSBackupManifestReader manifestReader = new IOSBackupManifestReader();
    private final SQLite3Parser sqliteParser = new SQLite3Parser();

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        TemporaryResources temporaryResources = new TemporaryResources();
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, temporaryResources);
            boolean specializedOutputStarted = false;

            try (Connection connection = getConnection(tikaStream, metadata, context)) {
                manifestReader.validateSchema(connection);
                specializedOutputStarted = true;
                XHTMLContentHandler xhtml = startReport(handler, metadata);
                long[] emittedCount = { 0 };
                long count;
                try {
                    count = manifestReader.readEntries(connection, entry -> {
                        emitReportEntry(xhtml, entry);
                        emittedCount[0]++;
                    });
                } catch (SQLException readException) {
                    try {
                        endIncompleteReport(xhtml, emittedCount[0]);
                    } catch (SAXException closeException) {
                        readException.addSuppressed(closeException);
                    }
                    metadata.set(META_PREFIX + "entryCount", Long.toString(emittedCount[0])); //$NON-NLS-1$
                    metadata.set(META_PREFIX + "incomplete", Boolean.TRUE.toString()); //$NON-NLS-1$
                    throw readException;
                }
                endReport(xhtml, count);
                metadata.set(META_PREFIX + "entryCount", Long.toString(count)); //$NON-NLS-1$

            } catch (Exception specializedException) {
                if (!specializedOutputStarted) {
                    try (InputStream fallbackStream = new FileInputStream(tikaStream.getFile())) {
                        sqliteParser.parse(fallbackStream, handler, metadata, context);
                        return;
                    } catch (Exception fallbackException) {
                        specializedException.addSuppressed(fallbackException);
                    }
                }
                throw new TikaException("Error parsing iOS backup Manifest.db", specializedException); //$NON-NLS-1$
            }
        } finally {
            temporaryResources.close();
        }
    }

    private XHTMLContentHandler startReport(ContentHandler handler, Metadata metadata) throws SAXException {
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement("head"); //$NON-NLS-1$
        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters("table {border-collapse: collapse;} th, td {border: 1px solid black; padding: 3px;}"); //$NON-NLS-1$
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.endElement("head"); //$NON-NLS-1$
        xhtml.startElement("h2"); //$NON-NLS-1$
        xhtml.characters("iOS Backup Manifest Catalog"); //$NON-NLS-1$
        xhtml.endElement("h2"); //$NON-NLS-1$
        xhtml.startElement("table"); //$NON-NLS-1$
        xhtml.startElement("tr"); //$NON-NLS-1$
        addCell(xhtml, "th", "File ID"); //$NON-NLS-1$ //$NON-NLS-2$
        addCell(xhtml, "th", "Domain"); //$NON-NLS-1$ //$NON-NLS-2$
        addCell(xhtml, "th", "Relative Path"); //$NON-NLS-1$ //$NON-NLS-2$
        addCell(xhtml, "th", "Type"); //$NON-NLS-1$ //$NON-NLS-2$
        addCell(xhtml, "th", "Flags"); //$NON-NLS-1$ //$NON-NLS-2$
        addCell(xhtml, "th", "Metadata Bytes"); //$NON-NLS-1$ //$NON-NLS-2$
        xhtml.endElement("tr"); //$NON-NLS-1$
        return xhtml;
    }

    private void emitReportEntry(XHTMLContentHandler xhtml, IOSBackupManifestEntry entry) throws SAXException {
        xhtml.startElement("tr"); //$NON-NLS-1$
        addCell(xhtml, "td", entry.getFileId()); //$NON-NLS-1$
        addCell(xhtml, "td", entry.getDomain()); //$NON-NLS-1$
        addCell(xhtml, "td", entry.getRelativePath()); //$NON-NLS-1$
        addCell(xhtml, "td", entry.getType().name()); //$NON-NLS-1$
        addCell(xhtml, "td", Integer.toString(entry.getFlags())); //$NON-NLS-1$
        addCell(xhtml, "td", Integer.toString(entry.getMetadataSize())); //$NON-NLS-1$
        xhtml.endElement("tr"); //$NON-NLS-1$
    }

    private void endReport(XHTMLContentHandler xhtml, long count) throws SAXException {
        xhtml.endElement("table"); //$NON-NLS-1$
        xhtml.startElement("p"); //$NON-NLS-1$
        xhtml.characters("Total catalog entries: " + count); //$NON-NLS-1$
        xhtml.endElement("p"); //$NON-NLS-1$
        xhtml.endDocument();
    }

    private void endIncompleteReport(XHTMLContentHandler xhtml, long count) throws SAXException {
        xhtml.endElement("table"); //$NON-NLS-1$
        xhtml.startElement("p"); //$NON-NLS-1$
        xhtml.characters("Catalog reading stopped after " + count + " entries."); //$NON-NLS-1$ //$NON-NLS-2$
        xhtml.endElement("p"); //$NON-NLS-1$
        xhtml.endDocument();
    }

    private void addCell(XHTMLContentHandler xhtml, String element, String value) throws SAXException {
        xhtml.startElement(element);
        xhtml.characters(value == null ? "" : value); //$NON-NLS-1$
        xhtml.endElement(element);
    }

}
