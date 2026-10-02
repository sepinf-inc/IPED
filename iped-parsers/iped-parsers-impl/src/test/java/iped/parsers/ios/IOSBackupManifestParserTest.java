package iped.parsers.ios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IOSBackupManifestParserTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesSyntheticManifestCatalog() throws Exception {
        File manifest = temporaryFolder.newFile("Manifest.db"); //$NON-NLS-1$
        createSyntheticManifest(manifest);

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler(-1);
        IOSBackupManifestParser parser = new IOSBackupManifestParser();

        try (FileInputStream stream = new FileInputStream(manifest)) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        String report = handler.toString();
        assertTrue(report.contains("HomeDomain")); //$NON-NLS-1$
        assertTrue(report.contains("Library/SMS/sms.db")); //$NON-NLS-1$
        assertTrue(report.contains("MediaDomain")); //$NON-NLS-1$
        assertTrue(report.contains("Media/DCIM/100APPLE")); //$NON-NLS-1$
        assertTrue(report.contains("SYMBOLIC_LINK")); //$NON-NLS-1$
        assertTrue(report.contains("Total catalog entries: 3")); //$NON-NLS-1$
        assertEquals("3", metadata.get("iosBackup:entryCount")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void createSyntheticManifest(File manifest) throws Exception {
        Class.forName("org.sqlite.JDBC"); //$NON-NLS-1$
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + manifest.getAbsolutePath()); //$NON-NLS-1$
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE Files (fileID TEXT, domain TEXT, relativePath TEXT, " //$NON-NLS-1$
                    + "flags INTEGER, file BLOB)"); //$NON-NLS-1$
            statement.execute("CREATE TABLE Properties (key TEXT, value BLOB)"); //$NON-NLS-1$

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO Files (fileID, domain, relativePath, flags, file) VALUES (?, ?, ?, ?, ?)")) { //$NON-NLS-1$
                insertEntry(insert, "1111111111111111111111111111111111111111", "HomeDomain", //$NON-NLS-1$ //$NON-NLS-2$
                        "Library/SMS/sms.db", 1, new byte[] { 1, 2, 3 }); //$NON-NLS-1$
                insertEntry(insert, "2222222222222222222222222222222222222222", "MediaDomain", //$NON-NLS-1$ //$NON-NLS-2$
                        "Media/DCIM/100APPLE", 2, null); //$NON-NLS-1$
                insertEntry(insert, "3333333333333333333333333333333333333333", "RootDomain", //$NON-NLS-1$ //$NON-NLS-2$
                        "private/var/mobile/link", 4, new byte[] { 4 }); //$NON-NLS-1$
            }
        }
    }

    private void insertEntry(PreparedStatement insert, String fileId, String domain, String relativePath,
            int flags, byte[] metadata) throws Exception {
        insert.setString(1, fileId);
        insert.setString(2, domain);
        insert.setString(3, relativePath);
        insert.setInt(4, flags);
        insert.setBytes(5, metadata);
        insert.executeUpdate();
    }
}
