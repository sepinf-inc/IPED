package iped.parsers.zoomdpapi;

import org.sqlite.mc.SQLiteMCConfig;
import org.sqlite.mc.SQLiteMCSqlCipherConfig;
import org.sqlite.mc.HmacAlgorithm;
import org.sqlite.mc.KdfAlgorithm;

import java.io.File;
import java.sql.Connection;

/**
 * Opens SQLCipher-encrypted Zoom databases using the decrypted OSKEY.
 * Configures SQLCipher V4 with SHA-512 HMAC, 4000 KDF iterations,
 * and 1024-byte legacy page size.
 *
 * Uses the SQLiteMC extension (io.github.willena:sqlite-jdbc) which
 * configures cipher parameters before opening the database — required
 * because PRAGMA-based configuration is not possible after connection.
 *
 * @see <a href="https://github.com/Willena/sqlite-jdbc-crypt/issues/159">#159</a>
 * @author Calil Khalil (Hakal)
 */
public class ZoomDatabaseReader {

    private final File databaseFile;
    private final String decryptedKey;

    public ZoomDatabaseReader(File databaseFile, String decryptedKey) {
        this.databaseFile = databaseFile;
        this.decryptedKey = decryptedKey;
    }

    public Connection createConnection() throws Exception {
        SQLiteMCConfig config = SQLiteMCSqlCipherConfig.getV4Defaults()
            .setLegacyPageSize(1024)
            .setKdfIter(4000)
            .setHmacAlgorithm(HmacAlgorithm.SHA512)
            .setKdfAlgorithm(KdfAlgorithm.SHA512)
            .withKey(decryptedKey)
            .build();

        config.setReadOnly(true);

        return config.createConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
    }

}
