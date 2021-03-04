package gpinf.hashdb;

import static gpinf.hashdb.HashDB.hashTypes;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Encoding;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.SynchronousMode;

import dpf.sp.gpinf.indexer.util.HashValue;

public class HashDBDataSource {
    private Connection connection;
    private PreparedStatement[] stmtSelectHash;
    private PreparedStatement stmtSelectHashProperties;
    private PreparedStatement stmtSelectMD5;
    private boolean[] presentHashes;
    private final Map<Integer, String> propertyIdToName = new HashMap<Integer, String>();

    private static final String ledFileLength = "fileLength";
    private static final String ledFileExt = "fileExt";
    private static final String ledMd5_512 = "md5_512";
    private static final String ledMd5_64k = "md5_64k";

    private static final String photoDna = "photoDna";
    private static final int photoDnaBase64Len = 192;

    private static final String propertySet = "set";
    private static final String propertyStatus = "status";
    private static final String pedoStatus = "pedo";

    public HashDBDataSource(File dbFile) throws Exception {
        connect(dbFile);
        prepare();
        loadProperties();
    }

    public synchronized String getMD5(int hashId) {
        String md5 = null;
        try {
            stmtSelectMD5.setInt(1, hashId);
            ResultSet rs = stmtSelectMD5.executeQuery();
            if (rs.next()) {
                byte[] md5Bytes = rs.getBytes(1);
                md5 = HashDB.hashBytesToStr(md5Bytes);
            }
            rs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return md5;
    }

    public synchronized Map<String, List<String>> getProperties(int hashId) {
        Map<String, List<String>> properties = new HashMap<String, List<String>>();
        try {
            stmtSelectHashProperties.setInt(1, hashId);
            ResultSet rs = stmtSelectHashProperties.executeQuery();
            while (rs.next()) {
                int propertyId = rs.getInt(1);
                String propertyName = propertyIdToName.get(propertyId);
                if (propertyName != null) {
                    String value = rs.getString(2);
                    properties.put(propertyName, HashDB.toList(value));
                }
            }
            rs.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return properties;
    }

    public synchronized LedItem getLedItem(int hashId) {
        try {
            String md5 = null;
            stmtSelectMD5.setInt(1, hashId);
            ResultSet rs = stmtSelectMD5.executeQuery();
            if (rs.next()) {
                byte[] md5Bytes = rs.getBytes(1);
                md5 = HashDB.hashBytesToStr(md5Bytes);
            }
            rs.close();
            if (md5 == null) return null;

            long length = -1;
            String ext = null;
            stmtSelectHashProperties.setInt(1, hashId);
            rs = stmtSelectHashProperties.executeQuery();
            while (rs.next()) {
                int propertyId = rs.getInt(1);
                String propertyName = propertyIdToName.get(propertyId);
                if (propertyName != null) {
                    String value = rs.getString(2);
                    if (propertyName.equalsIgnoreCase(ledFileLength)) {
                        length = Long.parseLong(value);
                    } else if (propertyName.equalsIgnoreCase(ledFileExt)) {
                        ext = value;
                    }
                }
            }
            rs.close();
            if (length == -1) return null;
            return new LedItem(length, md5, ext);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized ArrayList<PhotoDnaItem> readPhotoDNA() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("select HASH_ID, VALUE from HASHES_PROPERTIES where PROPERTY_ID in (");
        boolean first = true;
        for (int propId : propertyIdToName.keySet()) {
            String propName = propertyIdToName.get(propId);
            if (propName.equalsIgnoreCase(photoDna)) {
                if (first) first = false;
                else sb.append(',');
                sb.append(propId);
            }
        }
        sb.append(')');
        if (first) return null;

        ArrayList<PhotoDnaItem> photoDNAHashSet = new ArrayList<PhotoDnaItem>();
        Statement stmt = connection.createStatement();
        stmt.setFetchSize(1024);
        ResultSet rs = stmt.executeQuery(sb.toString());
        Decoder decoderBase64 = Base64.getDecoder();
        while (rs.next()) {
            int hashId = rs.getInt(1);
            String value = rs.getString(2);
            if (value.length() == photoDnaBase64Len) {
                byte[] bytes = decoderBase64.decode(value);
                photoDNAHashSet.add(new PhotoDnaItem(hashId, new HashValue(bytes)));
            }
        }
        rs.close();
        stmt.close();
        return photoDNAHashSet;
    }

    public synchronized LedHashDB readLedHashDB() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("select HASH_ID, PROPERTY_ID, VALUE from HASHES_PROPERTIES where PROPERTY_ID in (");
        boolean first = true;
        for (int propId : propertyIdToName.keySet()) {
            String propName = propertyIdToName.get(propId);
            if (propName.equalsIgnoreCase(propertyStatus) || propName.equalsIgnoreCase(ledMd5_512) || propName.equalsIgnoreCase(ledMd5_64k)) {
                if (first) first = false;
                else sb.append(',');
                sb.append(propId);
            }
        }
        sb.append(')');
        if (first) return null;

        Map<Integer, HashValue> hashIdToMd5_512 = new HashMap<Integer, HashValue>();
        Map<Integer, HashValue> hashIdToMd5_64k = new HashMap<Integer, HashValue>();
        Map<HashValue, Integer> md5_64kToHashId = new HashMap<HashValue, Integer>();
        Set<Integer> setHashIds = new HashSet<Integer>();
        Statement stmt = connection.createStatement();
        stmt.setFetchSize(1024);
        ResultSet rs = stmt.executeQuery(sb.toString());
        while (rs.next()) {
            int hashId = rs.getInt(1);
            int propId = rs.getInt(2);
            String value = rs.getString(3);
            String propName = propertyIdToName.get(propId);
            if (propName.equalsIgnoreCase(propertyStatus)) {
                if (HashDB.containsIgnoreCase(value, pedoStatus)) {
                    setHashIds.add(hashId);
                }
            } else if (propName.equalsIgnoreCase(ledMd5_512)) {
                if (value.length() == 32) {
                    hashIdToMd5_512.put(hashId, new HashValue(value));
                }
            } else if (propName.equalsIgnoreCase(ledMd5_64k)) {
                if (value.length() == 32) {
                    HashValue hv = new HashValue(value);
                    hashIdToMd5_64k.put(hashId, hv);
                    md5_64kToHashId.put(hv, hashId);
                }
            }
        }
        rs.close();
        stmt.close();

        hashIdToMd5_512.keySet().retainAll(setHashIds);
        if (hashIdToMd5_512.isEmpty()) return null;

        hashIdToMd5_64k.keySet().retainAll(setHashIds);
        if (hashIdToMd5_64k.isEmpty()) return null;
        setHashIds = null;

        List<HashValue> listMd5_512 = new ArrayList<HashValue>(hashIdToMd5_512.values());
        hashIdToMd5_512 = null;
        Collections.sort(listMd5_512);
        HashValue prev = null;
        int cnt = 0;
        for (HashValue curr : listMd5_512) {
            if (curr.equals(prev)) continue;
            cnt++;
            prev = curr;
        }
        byte[] md5_512 = new byte[16 * cnt];
        int pos = 0;
        prev = null;
        for (HashValue hv : listMd5_512) {
            if (hv.equals(prev)) continue;
            System.arraycopy(hv.getBytes(), 0, md5_512, pos, 16);
            pos += 16;
            prev = hv;
        }
        listMd5_512 = null;

        List<HashValue> listMd5_64k = new ArrayList<HashValue>(hashIdToMd5_64k.values());
        Collections.sort(listMd5_64k);
        byte[] md5_64k = new byte[16 * listMd5_64k.size()];
        int[] hashIds = new int[listMd5_64k.size()];
        cnt = 0;
        pos = 0;
        for (HashValue hv : listMd5_64k) {
            System.arraycopy(hv.getBytes(), 0, md5_64k, pos, 16);
            pos += 16;
            hashIds[cnt++] = md5_64kToHashId.get(hv);
        }
        LedHashDB ledHashDB = new LedHashDB(md5_512, md5_64k, hashIds);
        return ledHashDB;
    }

    public synchronized List<String> lookupSets(String algorithm, String hash) throws Exception {
        int idx = HashDB.hashType(algorithm);
        if (idx < 0) return null;
        byte[][] hashes = new byte[HashDB.hashTypes.length][];
        hashes[idx] = HashDB.hashStrToBytes(hash, HashDB.hashBytesLen[idx]);
        Map<String, String> properties = new HashMap<String, String>();
        lookup(hashes, properties);
        if (properties.isEmpty()) return null;

        boolean pedo = false;
        List<String> hashSets = null;
        for (String prop : properties.keySet()) {
            if (prop.equalsIgnoreCase(propertyStatus)) {
                if (HashDB.containsIgnoreCase(properties.get(prop), pedoStatus)) {
                    pedo = true;
                }
            } else if (prop.equalsIgnoreCase(propertySet)) {
                if (hashSets == null) hashSets = new ArrayList<String>();
                hashSets.addAll(HashDB.toSet(properties.get(prop)));
            }
        }
        return pedo ? hashSets : null;
    }

    public synchronized void lookup(byte[][] hashes, Map<String, String> properties) throws Exception {
        int mask = 0;
        for (int i = 0; i < hashes.length; i++) {
            if (hashes[i] != null && presentHashes[i]) {
                mask |= 1 << i;
            }
        }
        if (mask == 0) return;
        PreparedStatement stmtSelect = stmtSelectHash[mask];
        int k = 0;
        for (int i = 0; i < hashes.length; i++) {
            byte[] h = hashes[i];
            if (h != null && presentHashes[i]) stmtSelect.setBytes(++k, h);
        }
        ResultSet rs1 = stmtSelect.executeQuery();
        while (rs1.next()) {
            int hashId = rs1.getInt(1);
            stmtSelectHashProperties.setInt(1, hashId);
            ResultSet rs2 = stmtSelectHashProperties.executeQuery();
            while (rs2.next()) {
                int propertyId = rs2.getInt(1);
                String propertyName = propertyIdToName.get(propertyId);
                if (propertyName != null) {
                    String propertyValue = rs2.getString(2);
                    String prev = properties.get(propertyName);
                    if (prev != null) {
                        propertyValue = HashDB.mergeProperties(propertyValue, prev);
                    }
                    properties.put(propertyName, propertyValue);
                }
            }
            rs2.close();
        }
        rs1.close();
    }

    public synchronized void close() {
        if (stmtSelectMD5 != null) {
            try {
                stmtSelectMD5.close();
            } catch (Exception e) {}
        }
        if (stmtSelectHash != null) {
            for (PreparedStatement stmt : stmtSelectHash) {
                try {
                    if (stmt != null) stmt.close();
                } catch (Exception e) {}
            }
        }
        if (stmtSelectHashProperties != null) {
            try {
                stmtSelectHashProperties.close();
            } catch (Exception e) {}
        }
        try {
            if (connection != null) connection.close();
        } catch (Exception e) {}
    }

    private void connect(File dbFile) throws Exception {
        SQLiteConfig config = new SQLiteConfig();
        config.setEncoding(Encoding.UTF8);
        config.setSynchronous(SynchronousMode.OFF);
        config.setJournalMode(JournalMode.OFF);
        config.setReadOnly(true);
        connection = config.createConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    }

    private void prepare() throws Exception {
        presentHashes = new boolean[hashTypes.length];
        Statement stmt = connection.createStatement();
        for (int i = 0; i < hashTypes.length; i++) {
            StringBuilder sb = new StringBuilder();
            sb.append("select 1 from HASHES where ");
            sb.append(hashTypes[i]);
            sb.append(" is not null limit 1");
            ResultSet rs = stmt.executeQuery(sb.toString());
            if (rs.next()) {
                presentHashes[i] = true;
            }
        }
        stmt.close();

        StringBuilder sb = new StringBuilder();
        sb.append("select HASH_ID from HASHES where ");
        stmtSelectHash = new PreparedStatement[1 << hashTypes.length];
        NEXT: for (int i = 1; i < stmtSelectHash.length; i++) {
            StringBuilder sb1 = new StringBuilder(sb);
            boolean first = true;
            for (int j = 0; j < hashTypes.length; j++) {
                if (((1 << j) & i) != 0) {
                    if (!presentHashes[j]) continue NEXT;
                    if (first) first = false;
                    else sb1.append(" OR ");
                    sb1.append(hashTypes[j]).append("=?");
                }
            }
            stmtSelectHash[i] = connection.prepareStatement(sb1.toString());
            stmtSelectHash[i].setFetchSize(hashTypes.length);
        }

        stmtSelectHashProperties = connection.prepareStatement("select PROPERTY_ID, VALUE from HASHES_PROPERTIES where HASH_ID=?");
        stmtSelectHashProperties.setFetchSize(64);

        stmtSelectMD5 = connection.prepareStatement("select MD5 from HASHES where HASH_ID=?");
    }

    private void loadProperties() throws Exception {
        Statement stmt = connection.createStatement();
        stmt.execute("select PROPERTY_ID, PROPERTY_NAME from PROPERTIES");
        stmt.setFetchSize(1024);
        ResultSet rs = stmt.getResultSet();
        while (rs.next()) {
            int id = rs.getInt(1);
            String name = rs.getString(2);
            propertyIdToName.put(id, name);
        }
        rs.close();
        stmt.close();
    }
}