package iped.parsers.whatsapp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import com.drew.lang.Charsets;
import com.whatsapp.MediaData;

import iped.parsers.sqlite.SQLite3DBParser;

/**
 *
 * @author PCF HAUCK
 */
public class LinkExtractor implements Closeable {

    private static final Logger logger = Logger.getLogger(LinkExtractor.class.getName());

    private File dbFile;
    private Connection con;
    private HashSet<String> hashes;
    private ArrayList<LinkDownloader> links;
    private int connTimeout;
    private int readTimeout;

    public LinkExtractor(File dbFile, HashSet<String> hashes, int connTimeout, int readTimeout) {
        this.dbFile = dbFile;
        this.hashes = hashes;
        this.con = createConnection(dbFile.getAbsolutePath());
        this.connTimeout = connTimeout;
        this.readTimeout = readTimeout;
        links = new ArrayList<>();
    }

    public Connection createConnection(String dbname) {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbname);
        } catch (Exception ex) {
            String msg = "Error getting connection when processing " + dbFile.getAbsolutePath();
            logger.log(Level.WARNING, msg, ex);
        }

        return null;
    }

    public void getKeyFromMediaKey(byte[] mediaKey) {

    }

    public static int tot = 0;

    private class HKDF {
        private HMac hMacHash = new HMac(new SHA256Digest());

        public byte[] extract(byte[] salt, byte[] inputKeyMaterial) {
            hMacHash.init(new KeyParameter(salt));
            byte[] output = new byte[32];

            hMacHash.update(inputKeyMaterial, 0, inputKeyMaterial.length);
            hMacHash.doFinal(output, 0);

            return output;
        }

        public byte[] expand(byte[] prk, byte[] info, int outputSize) {
            int iterations = (int) Math.ceil((double) outputSize / (double) 32);
            byte[] mixin = new byte[0];
            ByteArrayOutputStream results = new ByteArrayOutputStream();
            int remainingBytes = outputSize;

            for (int i = 1; i <= iterations; i++) {

                hMacHash.init(new KeyParameter(prk));

                byte[] stepResult = new byte[32];

                hMacHash.update(mixin, 0, mixin.length);

                hMacHash.update(info, 0, info.length);

                hMacHash.update((byte) i);

                hMacHash.doFinal(stepResult, 0);

                int stepSize = Math.min(remainingBytes, stepResult.length);

                results.write(stepResult, 0, stepSize);

                mixin = stepResult;
                remainingBytes -= stepSize;
            }

            return results.toByteArray();
        }
    }

    private byte[] deriveCipherKey(byte[] mediakey, String mediaType) {

        HKDF hkg = new HKDF();

        byte[] key = hkg.expand(hkg.extract(new byte[32], mediakey), mediaType.getBytes(Charsets.UTF_8), 112);

        return key;

    }

    public byte[] getCipherKey(byte[] rawData, byte[] mediakey, String mediaType) {
        if (mediakey != null) {
            return deriveCipherKey(mediakey, mediaType);
        }
        if (rawData == null) {
            return null;
        }
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(rawData);
            ObjectInput in = new ObjectInputStream(bis);
            MediaData media = (MediaData) in.readObject();
            if (media.mediaKey != null) {
                return deriveCipherKey(media.mediaKey, mediaType);
            }

        } catch (Exception ex) {
            String msg = "Error getting cipher key when processing " + dbFile.getAbsolutePath();
            logger.log(Level.WARNING, msg, ex);
        }
        return null;

    }


    public static String capitalize(String aux) {
        if (aux != null && !aux.isEmpty()) {
            aux = aux.substring(0, 1).toUpperCase() + aux.substring(1);

        }
        return aux;
    }

    public void extractLinks() throws SQLException, DecoderException {

        if (con == null) {
            return;
        }

        String sql = getQuery();

        StringBuilder base64Hashes = null;
        for (String hash : hashes) {
            hash = Base64.getEncoder().encodeToString(Hex.decodeHex(hash));
            if (base64Hashes == null) {
                base64Hashes = new StringBuilder();
            } else {
                base64Hashes.append(",");
            }
            base64Hashes.append('\'').append(hash).append('\'');
        }
        try (PreparedStatement stmt = con.prepareStatement(sql.replace("?", base64Hashes.toString()))) {
            ResultSet rs = stmt.executeQuery();
            // URLs of the same media may differ only by their query (e.g. "&mms3=true")
            HashSet<String> seen = new HashSet<>();
            while (rs.next()) {
                String link = rs.getString("url");
                String hash = rs.getString("hash");

                if (!isMediaUrl(link) || !seen.add(hash + getPath(link))) {
                    continue;
                }

                String decoded = new String(Hex.encodeHex(Base64.getDecoder().decode(hash), false));
                if (!hashes.contains(decoded)) {
                    continue;
                }
                String tipo = rs.getString("tipo");
                if (tipo == null) {
                    continue;
                }
                String mediaType = tipo;
                if (tipo.indexOf("/") >= 0) {
                    mediaType = tipo.substring(0, tipo.indexOf("/"));
                }
                if (mediaType.equals("application")) {
                    mediaType = "Document";
                } else {
                    mediaType = capitalize(mediaType).trim();
                }
                mediaType = "WhatsApp " + mediaType + " Keys";

                tipo = tipo.substring(tipo.indexOf("/") + 1);

                byte[] rawData = rs.getBytes("data");
                byte[] mediaKey=rs.getBytes("mediaKey");
                if (mediaKey != null && mediaKey.length == 76) {
                    mediaKey=Arrays.copyOfRange(mediaKey, 2, 34);
                }
                byte[] key = getCipherKey(rawData, mediaKey, mediaType);
                if (key != null) {
                    byte[] cipherkey = Arrays.copyOfRange(key, 16, 48);
                    byte[] iv = Arrays.copyOfRange(key, 0, 16);
                    LinkDownloader ld = new LinkDownloader(link, hash, connTimeout, readTimeout, cipherkey, iv);
                    if (ld.getHash() != null && ld.getFileName() != null) {
                        links.add(ld);
                    }
                } else {
                    tot++;
                }


            }
        }

    }

    /**
     * Checks if the URL points to a WhatsApp media host, as the database content
     * is not trusted and the URL will be requested. URLs of the "mm*" hosts must be
     * signed ("oh" parameter), as the server does not accept the other ones
     * anymore: old URLs like "https://mmg.whatsapp.net/d/f/....enc" ("Non direct
     * path download is not supported") and paths without query ("Bad URL hash").
     */
    static boolean isMediaUrl(String link) {
        try {
            URI uri = new URI(link);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) {
                return false;
            }
            if (host.equals(STATIC_MEDIA_HOST)) {
                return true;
            }
            String query = uri.getQuery();
            return host.startsWith("mm") && host.endsWith(".whatsapp.net") && query != null
                    && (query.startsWith("oh=") || query.contains("&oh="));
        } catch (Exception e) {
            return false;
        }
    }

    private static String getPath(String link) {
        int idx = link.indexOf('?');
        return idx < 0 ? link : link.substring(0, idx);
    }

    @Override
    public void close() throws IOException {
        try {
            if (con != null && !con.isClosed()) {
                this.con.close();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    public ArrayList<LinkDownloader> getLinks() {
        return links;
    }

    private String getQuery() throws SQLException {

        String sql = sql_android_old;
        if (SQLite3DBParser.containsTable("message_media", con)) {
            sql = sql_android;
            if (SQLite3DBParser.checkIfColumnExists(con, "message_media", "direct_path")) {
                sql = sql_android_direct_path;
            }
        }
        if (SQLite3DBParser.containsTable("ZWAMEDIAITEM", con)) {
            sql = sql_ios;
        }
        return sql;


    }

    // host of some medias sent by WhatsApp itself (e.g. "/downloadable?category=PSA&...")
    private static final String STATIC_MEDIA_HOST = "static.whatsapp.net";

    /*
     * Media URLs are filtered by host and not by the ".enc" extension, as newer URLs
     * do not have it (e.g. "https://mmg.whatsapp.net/o1/v/t62.7118-24/...?ccb=...").
     */
    private static String urlFilter(String column) {
        return "(" + column + " like 'https://mm%.whatsapp.net/%' or " + column + " like 'https://" + STATIC_MEDIA_HOST + "/%')";
    }

    // host used by WhatsApp to build the media URL from the direct path
    private static final String DEFAULT_MEDIA_HOST = "https://mmg.whatsapp.net";

    public static final String sql_ios = "SELECT ZMEDIAURL as url, ZVCARDNAME as hash, ZVCARDSTRING as tipo, null as data, ZMESSAGE as _id, ZMEDIAKEY as mediaKey from ZWAMEDIAITEM "
            + "where " + urlFilter("url") + " AND mediaKey is not null and hash is not NULL AND hash in (?) AND length(mediaKey)=76 group by url";

    public static final String sql_android_old = "SELECT m.media_url as url,m.media_hash as hash ,m.media_mime_type as tipo,m.thumb_image as data, m._id, null as mediaKey FROM messages m "
            + "where " + urlFilter("m.media_url") + " and m.media_hash is not null and m.media_hash in (?) group by url";

    public static final String sql_android = "SELECT message_url as url,file_hash as hash ,mime_type as tipo,null as data, message_row_id as _id, media_key as mediaKey FROM  message_media  "
            + "where " + urlFilter("message_url") + " and file_hash is not null and file_hash in (?) group by url";

    /*
     * The direct path is what WhatsApp currently uses to download medias. It may
     * exist when message_url is empty and it is an alternative (signed) URL for
     * medias with old static URLs. The URLs from message_url come first, so they
     * are the ones kept when both have the same path.
     */
    public static final String sql_android_direct_path = "SELECT * FROM ("
            + "SELECT message_url as url,file_hash as hash ,mime_type as tipo,null as data, message_row_id as _id, media_key as mediaKey, 0 as source FROM  message_media  "
            + "where " + urlFilter("message_url")
            + " UNION SELECT '" + DEFAULT_MEDIA_HOST + "' || direct_path as url,file_hash as hash ,mime_type as tipo,null as data, message_row_id as _id, media_key as mediaKey, 1 as source FROM  message_media  "
            + "where direct_path like '/%') where hash is not null and hash in (?) group by url order by source";

}
