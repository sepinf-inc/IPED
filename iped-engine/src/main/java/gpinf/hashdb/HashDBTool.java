package gpinf.hashdb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConfig.Encoding;
import org.sqlite.SQLiteConfig.JournalMode;
import org.sqlite.SQLiteConfig.LockingMode;
import org.sqlite.SQLiteConfig.SynchronousMode;
import org.sqlite.SQLiteConfig.TransactionMode;
import org.sqlite.SQLiteOpenMode;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class HashDBTool {
    private static final String[] hashTypes = new String[] {"MD5","SHA1","SHA256","EDONKEY"};
    private static final int[] hashBytesLen = new int[] {16,20,32,16};

    private static final String nsrlMainFileName = "NSRLFile.txt";
    private static final String nsrlProdFileName = "NSRLProd.txt";
    private static final String nsrlProductCode = "ProductCode";
    private static final String nsrlProductName = "ProductName";
    private static final String nsrlSpecialCode = "SpecialCode";
    private static final String nsrlSetPropertyValue = "NSRL";

    private static final String setPropertyName = "Set";
    private static final String statusPropertyName = "Status";

    private static final String vicDataModel = "http://github.com/ICMEC/ProjectVic/DataModels/1.3.xml#Media";
    private static final String vicSetPropertyValue = "ProjectVIC";
    private static final String vicStatusPropertyValue = "Pedo";

    private final List<File> inputs = new ArrayList<File>();
    private File output;
    private int lastHashId, lastPropertyId;
    private Connection connection;
    private PreparedStatement stmtInsertHash;
    private PreparedStatement stmtInsertProperty;
    private PreparedStatement stmtInsertHashProperty;
    private PreparedStatement stmtUpdateHashProperty;
    private PreparedStatement stmtRemoveHash;
    private PreparedStatement stmtUpdateHash;
    private PreparedStatement stmtRemoveHashProperties;
    private PreparedStatement stmtSelectHashProperties;
    private PreparedStatement[] stmtSelectHash;
    private final Map<String, Integer> propertyNameToId = new HashMap<String, Integer>();
    private Map<Integer, String> nsrlProdCodeToName;
    private ProcessMode mode = ProcessMode.UNDEFINED;
    private int totIns, totRem, totUpd, totSkip;
    private boolean dbExists;

    public static void main(String[] args) {
        HashDBTool tool = new HashDBTool();
        boolean success = tool.run(args);
        tool.finish(success);
    }

    private boolean run(String[] args) {
        if (!parseParameters(args)) return false;
        if (!checkInputFiles()) return false;
        dbExists = output.exists();
        if (!connect()) return false;
        if (!dbExists && !createDatabase()) return false;
        if (!prepare()) return false;
        if (!initSequences()) return false;
        if (!loadProperties()) return false;
        if (!readFiles()) return false;
        return true;
    }

    private boolean initSequences() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("select max(HASH_ID) from HASHES");
            while (rs.next()) {
                lastHashId = rs.getInt(1);
            }
            System.out.println("Last HASH_ID = " + lastHashId);
            rs.close();
            rs = stmt.executeQuery("select max(PROPERTY_ID) from PROPERTIES");
            while (rs.next()) {
                lastPropertyId = rs.getInt(1);
            }
            System.out.println("Last PROPERTY_ID = " + lastPropertyId);
            rs.close();
            stmt.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean loadProperties() {
        try {
            Statement stmt = connection.createStatement();
            stmt.execute("select PROPERTY_ID, PROPERTY_NAME from PROPERTIES");
            stmt.setFetchSize(1024);
            ResultSet rs = stmt.getResultSet();
            while (rs.next()) {
                int id = rs.getInt(1);
                String name = rs.getString(2);
                propertyNameToId.put(name, id);
            }
            System.out.println("Properties loaded = " + propertyNameToId.size());
            rs.close();
            stmt.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean process(byte[][] newHashes, Map<Integer, String> newProperties) throws Exception {
        if (newProperties.isEmpty()) return false;
        int mask = 0;
        for (int i = 0; i < newHashes.length; i++) {
            if (newHashes[i] != null) mask |= 1 << i;
        }
        if (mask == 0) return false;
        PreparedStatement stmtSelect = stmtSelectHash[mask];
        int k = 0;
        for (byte[] h : newHashes) {
            if (h != null) stmtSelect.setBytes(++k, h);
        }
        ResultSet rs = stmtSelect.executeQuery();
        byte[][] currHashes = new byte[hashTypes.length][];
        List<Integer> otherPrevHashIds = null;
        int prevHashId = -1;
        while (rs.next()) {
            int hashId = rs.getInt(1);
            if (prevHashId == -1) {
                prevHashId = hashId;
            } else {
                if (otherPrevHashIds == null) otherPrevHashIds = new ArrayList<Integer>();
                otherPrevHashIds.add(hashId);
            }
            for (int i = 0; i < hashTypes.length; i++) {
                byte[] h = rs.getBytes(2 + i);
                if (h != null && currHashes[i] == null) {
                    currHashes[i] = h.clone();
                }
            }
        }
        rs.close();
        if (prevHashId == -1) {
            if (mode != ProcessMode.REMOVE && mode != ProcessMode.REMOVE_ALL) {
                totIns++;
                int hashId = ++lastHashId;
                if (!insertHash(hashId, newHashes)) return false;
                if (!insertHashProperties(hashId, newProperties)) return false;
            }
        } else {
            if (mode == ProcessMode.REMOVE_ALL) {
                if (!removeHash(prevHashId)) return false;
                totRem++;
                if (otherPrevHashIds != null) {
                    for (int id : otherPrevHashIds) {
                        if (!removeHash(id)) return false;
                    }
                }
            } else {
                Map<Integer, String> currProperties = getProperties(prevHashId);
                if (otherPrevHashIds != null) {
                    for (int id : otherPrevHashIds) {
                        Map<Integer, String> otherProperties = getProperties(id);
                        if (otherProperties == null) return false;
                        if (!removeHash(id)) return false;
                        if (!currProperties.equals(otherProperties)) {
                            Map<Integer, String> propertiesToAdd = new HashMap<Integer, String>();
                            Set<Integer> propertiesToRemove = new HashSet<Integer>();
                            mergeProperties(currProperties, otherProperties, ProcessMode.MERGE, propertiesToAdd, propertiesToRemove);
                            if (propertiesToAdd.keySet().equals(propertiesToRemove)) {
                                if (!updateHashProperties(prevHashId, propertiesToAdd)) return false;
                            } else {
                                if (!removeHashProperties(prevHashId, propertiesToRemove)) return false;
                                if (!insertHashProperties(prevHashId, propertiesToAdd)) return false;
                            }
                        }
                    }
                }
                if (newProperties.equals(currProperties)) {
                    if (mode == ProcessMode.REMOVE) {
                        totRem++;
                        if (!removeHash(prevHashId)) return false;
                    } else {
                        totSkip++;
                    }
                } else {
                    Map<Integer, String> propertiesToAdd = new HashMap<Integer, String>();
                    Set<Integer> propertiesToRemove = new HashSet<Integer>();
                    mergeProperties(newProperties, currProperties, mode, propertiesToAdd, propertiesToRemove);
                    if (propertiesToAdd.isEmpty() && propertiesToRemove.equals(currProperties.keySet())) {
                        totRem++;
                        if (!removeHash(prevHashId)) return false;
                    } else if (propertiesToAdd.isEmpty() && propertiesToRemove.isEmpty()) {
                        totSkip++;
                    } else {
                        totUpd++;
                        if (propertiesToAdd.keySet().equals(propertiesToRemove)) {
                            if (!updateHashProperties(prevHashId, propertiesToAdd)) return false;
                        } else {
                            if (!removeHashProperties(prevHashId, propertiesToRemove)) return false;
                            if (!insertHashProperties(prevHashId, propertiesToAdd)) return false;
                        }
                    }
                }
                if (otherPrevHashIds != null || !equalsHashes(newHashes, currHashes)) {
                    mergeHashes(newHashes, currHashes);
                    updateHashes(prevHashId, newHashes);
                }
            }
        }
        return true;

    }

    private void mergeProperties(Map<Integer, String> a, Map<Integer, String> b, ProcessMode m, Map<Integer, String> propertiesToAdd, Set<Integer> propertiesToRemove) {
        if (m == ProcessMode.REPLACE_ALL) {
            propertiesToRemove.addAll(b.keySet());
            propertiesToRemove.removeAll(a.keySet());
        }
        for (int id : a.keySet()) {
            String av = a.get(id);
            String bv = b.get(id);
            if (m == ProcessMode.REPLACE_ALL) {
                if (!av.equals(bv)) {
                    if (bv != null) propertiesToRemove.add(id);
                    propertiesToAdd.put(id, av);
                }
            } else if (m == ProcessMode.REPLACE) {
                if (!av.equals(bv)) {
                    if (bv != null) propertiesToRemove.add(id);
                    propertiesToAdd.put(id, av);
                }
            } else if (m == ProcessMode.REMOVE) {
                if (av.equals(bv)) {
                    propertiesToRemove.add(id);
                } else if (bv != null) {
                    Set<String> as = toSet(av);
                    Set<String> bs = toSet(bv);
                    bs.removeAll(as);
                    propertiesToRemove.add(id);
                    propertiesToAdd.put(id, toStr(bs));
                }
            } else if (m == ProcessMode.MERGE) {
                if (!av.equals(bv)) {
                    if (bv == null) {
                        propertiesToAdd.put(id, av);
                    } else {
                        Set<String> as = toSet(av);
                        Set<String> bs = toSet(bv);
                        bs.addAll(as);
                        propertiesToRemove.add(id);
                        propertiesToAdd.put(id, toStr(bs));
                    }
                }
            }
        }
    }

    private String toStr(Set<String> set) {
        String[] c = new String[set.size()];
        int i = 0;
        for (String a : set) {
            c[i++] = a;
        }
        Arrays.sort(c);
        StringBuilder sb = new StringBuilder();
        for (String a : c) {
            if (sb.length() > 0) sb.append('|');
            sb.append(a);
        }
        return sb.toString();
    }

    private Set<String> toSet(String val) {
        Set<String> set = new HashSet<String>();
        String[] c = val.split("\\|");
        for (String a : c) {
            set.add(a);
        }
        return set;
    }

    private void mergeHashes(byte[][] a, byte[][] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null) {
                a[i] = b[i];
            }
        }
    }

    private boolean equalsHashes(byte[][] a, byte[][] b) {
        for (int i = 0; i < a.length; i++) {
            byte[] ai = a[i];
            byte[] bi = b[i];
            if (ai == null && bi != null) return false;
            if (ai != null && bi == null) return false;
            if (ai != null && bi != null && !Arrays.equals(ai, bi)) {
                System.out.println("ERROR: Unexpected hash inconsistency!");
                System.out.println(Arrays.toString(ai));
                System.out.println(Arrays.toString(bi));
                return false;
            }
        }
        return true;
    }

    private boolean removeHashProperties(int hashId, Set<Integer> properties) {
        try {
            for (int propId : properties) {
                stmtRemoveHashProperties.setInt(1, hashId);
                stmtRemoveHashProperties.setInt(2, propId);
                stmtRemoveHashProperties.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;

    }

    private Map<Integer, String> getProperties(int hashId) {
        try {
            stmtSelectHashProperties.setInt(1, hashId);
            ResultSet rs = stmtSelectHashProperties.executeQuery();
            Map<Integer, String> prop = new HashMap<Integer, String>();
            while (rs.next()) {
                int propId = rs.getInt(1);
                String value = rs.getString(2);
                prop.put(propId, value);
            }
            if (prop.isEmpty()) return null;
            return prop;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean removeHash(int hashId) {
        try {
            stmtRemoveHashProperties.setInt(1, hashId);
            stmtRemoveHashProperties.executeUpdate();
            stmtRemoveHash.setInt(1, hashId);
            stmtRemoveHash.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean insertHash(int hashId, byte[][] hashes) {
        try {
            stmtInsertHash.setInt(1, hashId);
            for (int i = 0; i < hashes.length; i++) {
                stmtInsertHash.setBytes(i + 2, hashes[i]);
            }
            stmtInsertHash.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean insertHashProperties(int hashId, Map<Integer, String> properties) {
        try {
            for (int propId : properties.keySet()) {
                stmtInsertHashProperty.setInt(1, hashId);
                stmtInsertHashProperty.setInt(2, propId);
                stmtInsertHashProperty.setString(3, properties.get(propId));
                stmtInsertHashProperty.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean updateHashProperties(int hashId, Map<Integer, String> properties) {
        try {
            for (int propId : properties.keySet()) {
                stmtUpdateHashProperty.setString(1, properties.get(propId));
                stmtUpdateHashProperty.setInt(2, hashId);
                stmtUpdateHashProperty.setInt(3, propId);
                stmtUpdateHashProperty.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean updateHashes(int hashId, byte[][] hashes) {
        try {
            for (int i = 0; i < hashes.length; i++) {
                stmtUpdateHash.setBytes(i + 1, hashes[i]);
            }
            stmtUpdateHash.setInt(hashes.length + 1, hashId);
            stmtUpdateHash.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private byte[] hashStrToBytes(String s, int len) throws RuntimeException {
        if (s.length() == 0) {
            return null;
        }
        if (s.length() != len << 1) {
            throw new RuntimeException("Invalid hash length: " + s);
        }
        byte[] ret = new byte[s.length() >>> 1];
        for (int i = 0; i < s.length(); i += 2) {
            int a = val(s.charAt(i));
            int b = val(s.charAt(i + 1));
            if (a < 0) throw new RuntimeException("Invalid hash value: " + s);
            ret[i >>> 1] = (byte) ((a << 4) | b);
        }
        return ret;
    }

    public static final String hashBytesToStr(byte[] bytes) {
        final char[] tos = "0123456789abcdef".toCharArray();
        char[] c = new char[bytes.length << 1];
        int k = 0;
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            c[k++] = tos[b >>> 4];
            c[k++] = tos[b & 15];
        }
        return new String(c);
    }

    public static final int val(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'A' && c <= 'Z') return c - 'A' + 10;
        if (c >= 'a' && c <= 'z') return c - 'a' + 10;
        return -1;
    }

    private boolean createDatabase() {
        Statement statement = null;
        try {
            statement = connection.createStatement();
            StringBuilder sb = new StringBuilder();
            sb.append("create table HASHES (HASH_ID integer primary key");
            for (String s : hashTypes) {
                sb.append(", ").append(s).append(" blob");
            }
            sb.append(")");
            statement.executeUpdate(sb.toString());

            for (String s : hashTypes) {
                sb.delete(0, sb.length());
                sb.append("create unique index IDX_");
                sb.append(s);
                sb.append(" on HASHES (");
                sb.append(s);
                sb.append(")");
                statement.executeUpdate(sb.toString());
            }

            sb.delete(0, sb.length());
            sb.append("create table HASHES_PROPERTIES (HASH_ID integer, PROPERTY_ID integer, VALUE text NOT NULL, ");
            sb.append("primary key (HASH_ID, PROPERTY_ID))");
            statement.executeUpdate(sb.toString());

            sb.delete(0, sb.length());
            sb.append("create table PROPERTIES (PROPERTY_ID integer, PROPERTY_NAME text NOT NULL, ");
            sb.append("primary key (PROPERTY_ID))");
            statement.executeUpdate(sb.toString());

            System.out.println("Database tables and indexes created.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
            } catch (Exception e) {}
        }
        return false;
    }

    private boolean connect() {
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(false);
            config.setCacheSize(131072);
            config.setPageSize(8192);
            config.setEncoding(Encoding.UTF8);
            config.setLockingMode(LockingMode.EXCLUSIVE);
            config.setSynchronous(SynchronousMode.NORMAL);
            config.setJournalMode(JournalMode.WAL);
            config.setOpenMode(SQLiteOpenMode.EXCLUSIVE);
            config.setTransactionMode(TransactionMode.EXCLUSIVE);
            config.enforceForeignKeys(false);
            connection = config.createConnection("jdbc:sqlite:" + output.getAbsolutePath());
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            connection.setAutoCommit(false);
            System.out.println("Connected to database " + output.getPath());
            return true;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    private boolean readFiles() {
        for (File file : inputs) {
            if (!readFile(file)) return false;
        }
        return true;
    }

    private boolean readFile(File file) {
        FileType type = getFileType(file);
        totIns = totRem = totUpd = totSkip = 0;
        System.out.println("\nReading " + type.toString() + " file " + file.getPath() + "...");
        if (type == FileType.NSRL_PROD) return readNSRLProd(file);
        if (type == FileType.PROJECT_VIC) return readProjectVIC(file);
        BufferedReader in = null;
        try {
            int setPropertyId = type == FileType.NSRL_MAIN ? getPropertyId(setPropertyName) : -1;
            long len = file.length();
            in = new BufferedReader(new FileReader(file), 1 << 20);
            String line = in.readLine();
            long pos = line.length() + 2;
            List<String> header = splitLine(line);
            int[] colIdx = new int[header.size()];
            Arrays.fill(colIdx, Integer.MIN_VALUE);
            int nsrlProductCodeCol = -1;
            for (int i = 0; i < header.size(); i++) {
                String col = header.get(i);
                int h = hashType(col);
                if (h >= 0) {
                    colIdx[i] = -h - 1;
                } else {
                    if (type == FileType.NSRL_MAIN) {
                        if (!col.equals(nsrlProductCode) && !col.equals(nsrlSpecialCode)) continue;
                        if (col.equals(nsrlProductCode)) nsrlProductCodeCol = i;
                        header.set(i, col = "NSRL" + col);
                    }
                    colIdx[i] = getPropertyId(col);
                }
            }
            byte[][] hashes = new byte[hashTypes.length][];
            long t = System.currentTimeMillis();
            int cnt = 0;
            int numCols = header.size();
            Map<Integer, String> properties = new HashMap<Integer, String>();
            while ((line = in.readLine()) != null) {
                pos += line.length() + 2;
                if (line.charAt(0) == '#') continue;
                cnt++;
                List<String> cols = splitLine(line);
                if (cols.size() != numCols) {
                    in.close();
                    throw new RuntimeException("Line #" + cnt + ": number of columns does not match header columns " + cols.size() + "/" + numCols + ".\n" + line);
                }
                properties.clear();
                Arrays.fill(hashes, null);
                for (int i = 0; i < numCols; i++) {
                    int idx = colIdx[i];
                    if (idx == Integer.MIN_VALUE) continue;
                    String val = cols.get(i);
                    if (idx < 0) {
                        idx = -idx - 1;
                        hashes[idx] = hashStrToBytes(val, hashBytesLen[idx]);
                    } else if (!val.isEmpty()) {
                        if (i == nsrlProductCodeCol) val = nsrlProdCodeToName.get(Integer.parseInt(val));
                        properties.put(idx, val);
                    }
                }
                if (setPropertyId != -1) properties.put(setPropertyId, nsrlSetPropertyValue);
                if (!process(hashes, properties)) {
                    in.close();
                    throw new RuntimeException("Line #" + cnt + ": invalid content:\n" + line);
                }
                if ((cnt & 8191) == 0) updatePercentage(pos / (double) len);
            }
            updatePercentage(-1);
            t = System.currentTimeMillis() - t;
            System.out.println("\r" + cnt + " lines read in " + t / 1000 + " seconds.");
            printTotals();
        } catch (Exception e) {
            System.out.println("Error reading file: " + file);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private boolean readProjectVIC(File file) {
        FileInputStream is = null;
        RandomAccessFile raf = null;
        JsonParser jp = null;
        try {
            long t = System.currentTimeMillis();
            int setPropertyId = getPropertyId(setPropertyName);
            int statusPropertyId = getPropertyId(statusPropertyName);
            JsonFactory jfactory = new JsonFactory();
            raf = new RandomAccessFile(file, "r");
            is = new FileInputStream(raf.getFD());
            jp = jfactory.createParser(is);
            if (jp.nextToken() != JsonToken.START_OBJECT) {
                throw new RuntimeException("Error: root JSON should be an object.");
            }

            byte[][] hashes = new byte[hashTypes.length][];
            Map<Integer, String> properties = new HashMap<Integer, String>();
            boolean hasHash = false;
            int cnt = 0;
            long len = file.length();
            while (jp.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jp.getCurrentName();
                if (!fieldName.equals("odata.metadata") || !jp.nextTextValue().equals(vicDataModel)) {
                    jp.close();
                    raf.close();
                    throw new RuntimeException("Error: Unknown ProjectVic JSON data model!");
                }
                if (jp.nextFieldName().equals("value")) {
                    int arrayDepth = 0;
                    do {
                        JsonToken token = jp.nextToken();
                        if (token == JsonToken.START_ARRAY) arrayDepth++;
                        else if (token == JsonToken.END_ARRAY) arrayDepth--;
                        else if (arrayDepth == 1) {
                            String name = jp.currentName();
                            if (token == JsonToken.START_OBJECT) {
                                Arrays.fill(hashes, null);
                                properties.clear();
                                hasHash = false;
                            } else if ("Category".equals(name)) {
                                int cat = jp.nextIntValue(-1);
                                if (cat >= 0) {
                                    properties.put(getPropertyId("VIC" + name), String.valueOf(cat));
                                    if (cat == 1 || cat == 2) properties.put(statusPropertyId, vicStatusPropertyValue);
                                }
                            } else if ("MD5".equals(name) || "SHA1".equals(name)) {
                                String value = jp.nextTextValue();
                                int idx = hashType(name);
                                if (idx >= 0) {
                                    hashes[idx] = hashStrToBytes(value, hashBytesLen[idx]);
                                    hasHash = true;
                                }
                            } else if ("VictimIdentified".equals(name) || "OffenderIdentified".equals(name) || "IsDistributed".equals(name) || "Series".equals(name) || "IsPrecategorized".equals(name) || "Tags".equals(name)) {
                                String value = jp.nextTextValue();
                                if (value != null) {
                                    if (value.indexOf('|') >= 0) value.replace('|', ' ');
                                    value = value.trim();
                                    if (!value.isEmpty()) properties.put(getPropertyId("VIC" + name), value);
                                }
                            } else if (token == JsonToken.END_OBJECT) {
                                if (hasHash && !properties.isEmpty()) {
                                    properties.put(setPropertyId, vicSetPropertyValue);
                                    process(hashes, properties);
                                    if ((cnt++ & 8191) == 0) {
                                        long pos = raf.getFilePointer();
                                        updatePercentage(pos / (double) len);
                                    }
                                    hasHash = false;
                                }
                            }
                        } else if (arrayDepth == 2) {}
                    } while (arrayDepth > 0);
                } else {
                    jp.close();
                    raf.close();
                    throw new RuntimeException("Error: Unexpected property in ProjectVic JSON '" + jp.currentName() + "'.");
                }
            }
            updatePercentage(-1);
            t = System.currentTimeMillis() - t;
            System.out.println("\r" + cnt + " records read in " + t / 1000 + " seconds.");
            printTotals();
        } catch (Exception e) {
            System.out.println("Error reading file: " + file);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (jp != null) jp.close();
            } catch (Exception e) {}
            try {
                if (is != null) is.close();
            } catch (Exception e) {}
            try {
                if (raf != null) raf.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private boolean readNSRLProd(File file) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file), 1 << 20);
            String line = in.readLine();
            List<String> header = splitLine(line);
            int colProdCode = -1;
            int colProdName = -1;
            for (int i = 0; i < header.size(); i++) {
                String col = header.get(i);
                if (col.equals(nsrlProductCode)) colProdCode = i;
                else if (col.equals(nsrlProductName)) colProdName = i;
            }
            if (colProdCode < 0 || colProdName < 0) {
                in.close();
                throw new RuntimeException("Error: Product code and name must be present in header columns.");
            }
            int cnt = 0;
            int numCols = header.size();
            nsrlProdCodeToName = new HashMap<Integer, String>();
            while ((line = in.readLine()) != null) {
                if (line.charAt(0) == '#') continue;
                cnt++;
                List<String> cols = splitLine(line);
                if (cols.size() != numCols) {
                    in.close();
                    throw new RuntimeException("Error: Line #" + cnt + ": number of columns does not match header columns " + cols.size() + "/" + numCols + ".\n" + line);
                }
                int code = Integer.parseInt(cols.get(colProdCode));
                String name = cols.get(colProdName);
                nsrlProdCodeToName.put(code, name);
            }
            System.out.println(cnt + " lines read.");
        } catch (Exception e) {
            System.out.println("Error reading file: " + file);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private void printTotals() {
        if (totIns > 0) System.out.println(totIns + " hash" + (totIns == 1 ? "" : "es") + " inserted.");
        if (totRem > 0) System.out.println(totRem + " hash" + (totRem == 1 ? "" : "es") + " removed.");
        if (totUpd > 0) System.out.println(totUpd + " hash" + (totUpd == 1 ? "" : "es") + " updated.");
        if (totSkip > 0) System.out.println(totSkip + " hash" + (totSkip == 1 ? " was" : "es were") + " already in the database.");
    }

    private static void updatePercentage(double pct) {
        char[] bar = new char[60];
        Arrays.fill(bar, ' ');
        if (pct >= 0) {
            if (pct > 1) pct = 1;
            bar[1] = '[';
            int len = (int) Math.round(pct * (bar.length - 3));
            for (int i = 0; i < len; i++) {
                bar[i + 2] = i == len - 1 ? '>' : '=';
            }
            bar[bar.length - 1] = ']';
            char[] s = (String.format("%.1f", pct * 100) + "%").toCharArray();
            System.arraycopy(s, 0, bar, (bar.length - s.length) / 2, s.length);
        }
        bar[0] = '\r';
        System.out.print(new String(bar));
    }

    private int getPropertyId(String name) throws Exception {
        Integer id = propertyNameToId.get(name);
        if (id == null) {
            propertyNameToId.put(name, id = ++lastPropertyId);
            stmtInsertProperty.setInt(1, id);
            stmtInsertProperty.setString(2, name);
            stmtInsertProperty.executeUpdate();
        }
        return id;
    }

    private boolean checkInputFiles() {
        for (int i = 0; i < inputs.size(); i++) {
            File file = inputs.get(i);
            FileType type = getFileType(file);
            if (type == FileType.NSRL_MAIN) {
                if (!checkNSRLHeader(file, type)) return false;
                File prodFile = new File(file.getParentFile(), nsrlProdFileName);
                if (!prodFile.exists() || !prodFile.isFile()) {
                    System.out.println("ERROR: File " + prodFile.getName() + " must be present in the same folder of NSRL main file (" + file.getName() + ").");
                    return false;
                }
                if (!checkNSRLHeader(prodFile, FileType.NSRL_PROD)) return false;
                inputs.add(i++, prodFile); //Insert NSRL Product file before the main file. 
            } else if (type == FileType.CSV) {
                if (!checkInputHeader(file)) return false;
            } else if (type == FileType.PROJECT_VIC) {
                //Identification was based on its contest (plus file extension) 
            } else {
                if (type == FileType.UNKNOWN) {
                    System.out.println("File " + file.getPath() + " skipped.");
                }
                inputs.remove(i--);
            }
        }
        return true;
    }

    private FileType getFileType(File file) {
        String name = file.getName().toLowerCase();
        if (name.equalsIgnoreCase(nsrlMainFileName)) return FileType.NSRL_MAIN;
        if (name.equalsIgnoreCase(nsrlProdFileName)) return FileType.NSRL_PROD;
        if (name.endsWith(".csv")) return FileType.CSV;
        if (name.endsWith(".json") && isProjectVicJson(file)) return FileType.PROJECT_VIC;
        return FileType.UNKNOWN;
    }

    private boolean isProjectVicJson(File file) {
        Reader reader = null;
        JsonParser jp = null;
        try {
            JsonFactory jfactory = new JsonFactory();
            reader = Files.newBufferedReader(file.toPath());
            jp = jfactory.createParser(reader);
            if (jp.nextToken() != JsonToken.START_OBJECT) return false;
            if (jp.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jp.getCurrentName();
                if (fieldName != null && fieldName.equals("odata.metadata") && vicDataModel.equals(jp.nextTextValue())) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (jp != null) jp.close();
            } catch (Exception e) {}
            try {
                if (reader != null) reader.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private boolean checkNSRLHeader(File file, FileType type) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            String line = in.readLine();
            List<String> header = splitLine(line);
            boolean hasHash = false;
            boolean hasProductCode = false;
            boolean hasProductName = false;
            for (String col : header) {
                if (hashType(col) >= 0) hasHash = true;
                else if (col.equalsIgnoreCase(nsrlProductCode)) hasProductCode = true;
                else if (col.equalsIgnoreCase(nsrlProductName)) hasProductName = true;
            }
            if (!hasHash && type == FileType.NSRL_MAIN) {
                System.out.println("ERROR: Invalid NSRL file " + file + ", no hash was found in its header.\n" + line);
                return false;
            }
            if (!hasProductCode) {
                System.out.println("ERROR: Invalid NSRL file " + file + ", no product code was found in its header.\n" + line);
                return false;
            }
            if (!hasProductName && type == FileType.NSRL_PROD) {
                System.out.println("ERROR: Invalid NSRL file " + file + ", no product name was found in its header.\n" + line);
                return false;
            }
        } catch (Exception e) {
            System.out.println("Error reading file: " + file);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private boolean checkInputHeader(File file) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            String line = in.readLine();
            List<String> header = splitLine(line);
            boolean hasHash = false;
            boolean hasProperty = false;
            for (String col : header) {
                if (hashType(col) >= 0) hasHash = true;
                else hasProperty = true;
            }
            if (!hasHash) {
                System.out.println("ERROR: File " + file + " header must contain at least one hash column.\n" + line);
                return false;
            }
            if (!hasProperty) {
                System.out.println("ERROR: File " + file + " header must contain at least one property column.\n" + line);
                return false;
            }
        } catch (Exception e) {
            System.out.println("Error reading file: " + file);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private void deleteUnused() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate("delete from HASHES where not exists (select 1 from HASHES_PROPERTIES where HASH_ID = HASHES.HASH_ID)");
        stmt.executeUpdate("delete from PROPERTIES where not exists (select 1 from HASHES_PROPERTIES where PROPERTY_ID = PROPERTIES.PROPERTY_ID)");
        stmt.close();
    }

    private void vacuum() throws SQLException {
        connection.setAutoCommit(true);
        Statement stmt = connection.createStatement();
        stmt.execute("vacuum");
        stmt.close();
        connection.setAutoCommit(false);
    }

    private void analyze() throws SQLException {
        connection.setAutoCommit(true);
        Statement stmt = connection.createStatement();
        stmt.execute("analyze");
        stmt.close();
        connection.setAutoCommit(false);
    }

    private void finish(boolean success) {
        try {
            if (success) {
                long t = System.currentTimeMillis();
                System.out.println("\nOptimizing the database...");
                try {
                    deleteUnused();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                connection.commit();
                try {
                    analyze();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    vacuum();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                t = System.currentTimeMillis() - t;
                System.out.println("Optimization completed in " + t / 1000 + " seconds.");
            } else {
                if (connection != null) {
                    connection.rollback();
                }
            }
        } catch (Exception e) {}
        try {
            if (stmtInsertHash != null) stmtInsertHash.close();
            if (stmtInsertProperty != null) stmtInsertProperty.close();
            if (stmtInsertHashProperty != null) stmtInsertHashProperty.close();
            if (stmtUpdateHashProperty != null) stmtUpdateHashProperty.close();
            if (stmtRemoveHash != null) stmtRemoveHash.close();
            if (stmtUpdateHash != null) stmtUpdateHash.close();
            if (stmtRemoveHashProperties != null) stmtRemoveHashProperties.close();
            if (stmtSelectHashProperties != null) stmtSelectHashProperties.close();
            if (stmtSelectHash != null) {
                for (PreparedStatement stmt : stmtSelectHash) {
                    if (stmt != null) stmt.close();
                }
            }
            if (connection != null) connection.close();
        } catch (Exception e) {}
        try {
            if (!success && !dbExists && output != null && output.exists()) {
                output.delete();
            }
        } catch (Exception e) {}
    }

    private boolean prepare() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("insert into HASHES (HASH_ID");
            for (String h : hashTypes) {
                sb.append(", ").append(h);
            }
            sb.append(") values (?");
            for (int i = 0; i < hashTypes.length; i++) {
                sb.append(", ?");
            }
            sb.append(")");
            stmtInsertHash = connection.prepareStatement(sb.toString());

            sb.delete(0, sb.length());
            sb.append("update HASHES set ");
            for (int i = 0; i < hashTypes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(hashTypes[i]).append("=?");
            }
            sb.append(" where HASH_ID=?");
            stmtUpdateHash = connection.prepareStatement(sb.toString());

            stmtInsertProperty = connection.prepareStatement("insert into PROPERTIES (PROPERTY_ID, PROPERTY_NAME) values (?, ?)");
            stmtInsertHashProperty = connection.prepareStatement("insert into HASHES_PROPERTIES (HASH_ID, PROPERTY_ID, VALUE) values (?, ?, ?)");
            stmtUpdateHashProperty = connection.prepareStatement("update HASHES_PROPERTIES set VALUE=? where HASH_ID=? and PROPERTY_ID=?");

            //Prepared statements for each possible combination of hash types
            sb.delete(0, sb.length());
            sb.append("select HASH_ID");
            for (String h : hashTypes) {
                sb.append(", ").append(h);
            }
            sb.append(" from HASHES where ");
            stmtSelectHash = new PreparedStatement[1 << hashTypes.length];
            for (int i = 1; i < stmtSelectHash.length; i++) {
                StringBuilder sb1 = new StringBuilder(sb);
                boolean first = true;
                for (int j = 0; j < hashTypes.length; j++) {
                    if (((1 << j) & i) != 0) {
                        if (first) first = false;
                        else sb1.append(" OR ");
                        sb1.append(hashTypes[j]).append("=?");
                    }
                }
                stmtSelectHash[i] = connection.prepareStatement(sb1.toString());
                stmtSelectHash[i].setFetchSize(hashTypes.length);
            }

            stmtRemoveHash = connection.prepareStatement("delete from HASHES where HASH_ID=?");
            stmtRemoveHashProperties = connection.prepareStatement("delete from HASHES_PROPERTIES where HASH_ID=? and PROPERTY_ID=?");
            stmtSelectHashProperties = connection.prepareStatement("select PROPERTY_ID, VALUE from HASHES_PROPERTIES where HASH_ID=?");
            stmtSelectHashProperties.setFetchSize(64);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private int hashType(String col) {
        if (col.indexOf("-") > 0) col = col.replace("-", "");
        for (int i = 0; i < hashTypes.length; i++) {
            if (col.equalsIgnoreCase(hashTypes[i])) return i;
        }
        return -1;
    }

    private boolean parseParameters(String[] args) {
        if (args.length == 0) {
            usage();
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = i == args.length - 1 ? null : args[i + 1];
            if (arg.equalsIgnoreCase("-d")) {
                if (value == null) {
                    System.out.println("ERROR: -d must be followed by a file or a folder.");
                    return false;
                }
                File in = new File(value);
                if (!in.exists()) {
                    System.out.println("ERROR: Input file/folder '" + in + "' not found.");
                    return false;
                }
                if (in.isDirectory()) {
                    File[] files = in.listFiles();
                    for (File file : files) {
                        if (file.isFile()) {
                            inputs.add(file);
                        }
                    }
                } else if (in.isFile()) {
                    inputs.add(in);
                }
                i++;
            } else if (arg.equalsIgnoreCase("-o")) {
                if (value == null) {
                    System.out.println("ERROR: -o must be followed by a file.");
                    return false;
                }
                if (output != null) {
                    System.out.println("ERROR: -o must be used only once.");
                    return false;
                }
                output = new File(value);
                if (output.exists() && output.isDirectory()) {
                    System.out.println("ERROR: Output must be a file, not a folder.");
                    return false;
                }
                i++;
            } else if (arg.equalsIgnoreCase("-replace")) {
                if (mode != ProcessMode.UNDEFINED) {
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other options.");
                    return false;
                }
                mode = ProcessMode.REPLACE;
            } else if (arg.equalsIgnoreCase("-replaceAll")) {
                if (mode != ProcessMode.UNDEFINED) {
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other options.");
                    return false;
                }
                mode = ProcessMode.REPLACE_ALL;
            } else if (arg.equalsIgnoreCase("-remove")) {
                if (mode != ProcessMode.UNDEFINED) {
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other options.");
                    return false;
                }
                mode = ProcessMode.REMOVE;
            } else if (arg.equalsIgnoreCase("-removeAll")) {
                if (mode != ProcessMode.UNDEFINED) {
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other options.");
                    return false;
                }
                mode = ProcessMode.REMOVE_ALL;
            } else {
                System.out.println("ERROR: unknown parameter '" + arg + "'.");
                return false;
            }
        }
        if (inputs.isEmpty()) {
            System.out.println("ERROR: No input file/folder defined (-d <input file or folder>).");
            return false;
        }
        if (output == null) {
            System.out.println("ERROR: No output file defined (-o <output database file>).");
            return false;
        }
        if (mode == ProcessMode.UNDEFINED) mode = ProcessMode.MERGE;
        return true;
    }

    private static List<String> splitLine(String input) {
        List<String> result = new ArrayList<String>();
        int start = 0;
        boolean inQuotes = false;
        for (int i = 0; i <= input.length(); i++) {
            if (i == input.length() || (input.charAt(i) == ',' && !inQuotes)) {
                String s = input.substring(start, i).trim();
                if (s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                    s = s.substring(1, s.length() - 1);
                }
                s.replace('|', ' ');
                result.add(s);
                start = i + 1;
            } else if (input.charAt(i) == '"') {
                inQuotes = !inQuotes;
            }
        }
        return result;
    }

    private void usage() {
        System.out.println("IPED HashDBTool");
        System.out.println("  Allows importing CSV files with a set of hashes and associated properties");
        System.out.println("  into a database that can be later used during IPED case processing, to");
        System.out.println("  search for these hashes and add their properties to the case item when a");
        System.out.println("  hit is found. NIST NSRL files and Project VIC JSON can also be imported.");
        System.out.println("Usage: java -jar hashdb.jar -d <input file or folder> -o <output DB file>");
        System.out.println("            [-replace | -replaceAll | -remove | -removeAll]");
        System.out.println("  -d");
        System.out.println("    Input files (can be used multiple times). If a folder is used, it processes");
        System.out.println("    all files with '.csv' extension, NSRL or Project Vic files. CSV Input files");
        System.out.println("    should use plain text format, one item per line, with columns separated by");
        System.out.println("    commas. The first line must be a header, defining the columns names. There");
        System.out.println("     must be one or more hash columns and one or more properties columns.");
        System.out.println("  -o");
        System.out.println("    Output database file. If it exists, data will be added to (or removed");
        System.out.println("    from) the existing database.");
        System.out.println("Optional parameters:");
        System.out.println("  -replace");
        System.out.println("    When importing new files, if an existing property is already present,");
        System.out.println("    with a different value, the default is to merge, keeping both values. Use");
        System.out.println("    this option to replace, instead of merging, the value associated with the");
        System.out.println("    existing property.");
        System.out.println("  -replaceAll");
        System.out.println("    Same as -replace, but will remove all previously existing values, not");
        System.out.println("    only the ones associated with properties being added.");
        System.out.println("  -remove");
        System.out.println("    The default behavior is to add CSV files to the database. Use this");
        System.out.println("    parameter to remove items. It will remove only properties/values present");
        System.out.println("    in the current file. If there are no property remaining, the hash itself");
        System.out.println("    is removed.");
        System.out.println("  -removeAll");
        System.out.println("    Remove all references to the hashes present in the input files.");
    }

    enum ProcessMode {
        UNDEFINED, MERGE, REPLACE, REPLACE_ALL, REMOVE, REMOVE_ALL;
    }

    enum FileType {
        CSV, NSRL_MAIN, NSRL_PROD, PROJECT_VIC, UNKNOWN;
    }
}