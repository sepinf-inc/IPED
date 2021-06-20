package gpinf.hashdb;

import static gpinf.hashdb.HashDB.hashBytesLen;
import static gpinf.hashdb.HashDB.hashBytesToStr;
import static gpinf.hashdb.HashDB.hashStrToBytes;
import static gpinf.hashdb.HashDB.hashType;
import static gpinf.hashdb.HashDB.hashTypes;
import static gpinf.hashdb.HashDB.toSet;
import static gpinf.hashdb.HashDB.toStr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
    private static final String nsrlMainFileName = "NSRLFile.txt";
    private static final String nsrlMainZipFileName = "NSRLFile.txt.zip";
    private static final String nsrlProdFileName = "NSRLProd.txt";
    private static final String nsrlProductCode = "ProductCode";
    private static final String nsrlProductName = "ProductName";
    private static final String nsrlSpecialCode = "SpecialCode";
    private static final String nsrlSetPropertyValue = "NSRL";
    private static final String nsrlPrefix = "nsrl";

    private static final String setPropertyName = "set";
    private static final String statusPropertyName = "status";

    private static final String vicDataModel = "http://github.com/ICMEC/ProjectVic/DataModels/1.3.xml#Media";
    private static final String vicSetPropertyValue = "ProjectVIC";
    private static final String[] vicStatusPropertyValues = new String[] {"known","pedo","pedo"};
    private static final String vicPrefix = "vic";
    private static final String photoDnaPropertyName = "photoDna";
    private static final int photoDnaBase64Len = 192;    

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
    private PreparedStatement stmtRemoveAllHashProperties;
    private PreparedStatement stmtSelectHashProperties;
    private PreparedStatement[] stmtSelectHash;
    private final Map<String, Integer> propertyNameToId = new HashMap<String, Integer>();
    private Map<Integer, String> nsrlProdCodeToName;
    private ProcessMode mode = ProcessMode.UNDEFINED;
    private int totIns, totRem, totUpd, totSkip, totComb, totIgn, totNoProd;
    private boolean dbExists = true, skipOpt, inputFolderUsed;

    public static void main(String[] args) {
        HashDBTool tool = new HashDBTool();
        boolean success = tool.run(args);
        tool.finish(success);
    }

    boolean run(String[] args) {
        if (!parseParameters(args)) return false;
        if (!checkInputFiles()) return false;
        if (inputs.isEmpty()) System.exit(0);
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
                propertyNameToId.put(name.toLowerCase(), id);
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

    private boolean process(byte[][] newHashes, Map<Integer, Set<String>> newProperties) throws Exception {
        if (newProperties.isEmpty()) return true;
        if (isZeroLength(newHashes)) {
            totIgn++;
            return true;
        }
        int mask = 0;
        for (int i = 0; i < newHashes.length; i++) {
            if (newHashes[i] != null) mask |= 1 << i;
        }
        if (mask == 0) return true;
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
                Map<Integer, Set<String>> currProperties = getProperties(prevHashId);
                if (otherPrevHashIds != null) {
                    for (int id : otherPrevHashIds) {
                        Map<Integer, Set<String>> otherProperties = getProperties(id);
                        if (otherProperties == null) return false;
                        if (!removeHash(id)) return false;
                        if (!currProperties.equals(otherProperties)) {
                            Map<Integer, Set<String>> propertiesToAdd = new HashMap<Integer, Set<String>>();
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
                    Map<Integer, Set<String>> propertiesToAdd = new HashMap<Integer, Set<String>>();
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

    private void mergeProperties(Map<Integer, Set<String>> newProp, Map<Integer, Set<String>> oldProp, ProcessMode m, Map<Integer, Set<String>> propertiesToAdd, Set<Integer> propertiesToRemove) {
        if (m == ProcessMode.REPLACE_ALL) {
            propertiesToRemove.addAll(oldProp.keySet());
            propertiesToRemove.removeAll(newProp.keySet());
        }
        for (int id : newProp.keySet()) {
            Set<String> av = newProp.get(id);
            Set<String> bv = oldProp.get(id);
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
                    if (bv.removeAll(av)) {
                        propertiesToRemove.add(id);
                        propertiesToAdd.put(id, bv);
                    }
                }
            } else if (m == ProcessMode.MERGE) {
                if (bv == null) {
                    propertiesToAdd.put(id, av);
                } else if (bv.addAll(av)) {
                    propertiesToRemove.add(id);
                    propertiesToAdd.put(id, bv);
                }
            }
        }
    }

    private void mergeHashes(byte[][] a, byte[][] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null) {
                a[i] = b[i];
            }
        }
    }
    
    private boolean isZeroLength(byte[][] hashes) {
        for (int i = 0; i < hashes.length; i++) {
            byte[] h = hashes[i];
            if (h != null && Arrays.equals(h, HashDB.zeroLengthHash[i])) {
                return true;
            }
        }
        return false;
    }

    private boolean equalsHashes(byte[][] a, byte[][] b) {
        boolean ok = true;
        for (int i = 0; i < a.length; i++) {
            byte[] ai = a[i];
            byte[] bi = b[i];
            if (ai == null && bi != null) return false;
            if (ai != null && bi == null) return false;
            if (ai != null && bi != null && !Arrays.equals(ai, bi)) {
                ok = false;
                break;
            }
        }
        if (!ok) {
            System.out.println("\nWARNING: Unexpected hash inconsistency!");
            System.out.println("\tNew:");
            for (int i = 0; i < a.length; i++) {
                if (a[i] != null) System.out.println("\t\t" + hashTypes[i] + "\t" + hashBytesToStr(a[i]));
            }
            System.out.println("\tCurrent:");
            for (int i = 0; i < b.length; i++) {
                if (b[i] != null) System.out.println("\t\t" + hashTypes[i] + "\t" + hashBytesToStr(b[i]));
            }
            System.out.println();
        }
        return ok;
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

    private Map<Integer, Set<String>> getProperties(int hashId) {
        try {
            stmtSelectHashProperties.setInt(1, hashId);
            ResultSet rs = stmtSelectHashProperties.executeQuery();
            Map<Integer, Set<String>> prop = new HashMap<Integer, Set<String>>();
            while (rs.next()) {
                int propId = rs.getInt(1);
                String value = rs.getString(2);
                prop.put(propId, toSet(value));
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
            stmtRemoveAllHashProperties.setInt(1, hashId);
            stmtRemoveAllHashProperties.executeUpdate();
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

    private boolean insertHashProperties(int hashId, Map<Integer, Set<String>> properties) {
        try {
            for (int propId : properties.keySet()) {
                stmtInsertHashProperty.setInt(1, hashId);
                stmtInsertHashProperty.setInt(2, propId);
                stmtInsertHashProperty.setString(3, toStr(properties.get(propId)));
                stmtInsertHashProperty.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean updateHashProperties(int hashId, Map<Integer, Set<String>> properties) {
        try {
            for (int propId : properties.keySet()) {
                stmtUpdateHashProperty.setString(1, toStr(properties.get(propId)));
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
            config.setJournalMode(JournalMode.DELETE);
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
        totIns = totRem = totUpd = totSkip = totComb = totIgn = totNoProd = 0;
        System.out.println("\nReading " + (type == FileType.INPUT ? "" : type.toString() + " ") + "file " + file.getPath() + "...");
        if (type == FileType.NSRL_PROD) return readNSRLProd(file);
        if (type == FileType.PROJECT_VIC) return readProjectVIC(file);
        BufferedReader in = null;
        ZipInputStream zipInput = null;
        try {
            int setPropertyId = type == FileType.NSRL_MAIN || type == FileType.NSRL_MAIN_ZIP ? getPropertyId(setPropertyName) : -1;
            long len = file.length();
            if (type == FileType.NSRL_MAIN_ZIP) {
                zipInput = new ZipInputStream(new FileInputStream(file));
                ZipEntry entry = null;
                while ((entry = zipInput.getNextEntry()) != null) {
                    if (entry.getName().equalsIgnoreCase(nsrlMainFileName)) {
                        len = entry.getSize();
                        in = new BufferedReader(new InputStreamReader(zipInput, StandardCharsets.ISO_8859_1));
                        break;
                    }
                }
                if (in == null) {
                    System.out.println("ERROR: Invalid NSRL ZIP file " + file.getPath() + ", NSRL text file entry not found.");
                    return false;
                }
            } else {
                in = new BufferedReader(new FileReader(file), 1 << 20);
            }
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
            List<String> header = new ArrayList<String>(parser.getHeaderNames());
            if (header == null || header.isEmpty()) {
                System.out.println("ERROR: Invalid file " + file.getPath() + ", header not found.");
                return false;
            }
            int[] colIdx = new int[header.size()];
            int nsrlProductCodeCol = -1;
            int photoDnaCol = -1;
            int numHashCols = 0;
            int numPropCols = 0;
            int[] hashCols = new int[header.size()];
            int[] propCols = new int[header.size()];
            for (int i = 0; i < header.size(); i++) {
                String col = header.get(i);
                int h = hashType(col);
                if (h >= 0) {
                    colIdx[i] = h;
                    hashCols[numHashCols++] = i;
                } else {
                    if (type == FileType.NSRL_MAIN || type == FileType.NSRL_MAIN_ZIP) {
                        if (!col.equals(nsrlProductCode) && !col.equals(nsrlSpecialCode)) continue;
                        if (col.equals(nsrlProductCode)) {
                            nsrlProductCodeCol = i;
                            col = nsrlProductName;
                        }
                        header.set(i, col = nsrlPrefix + col);
                    } 
                    if (col.equalsIgnoreCase(photoDnaPropertyName)) {
                        photoDnaCol = i;
                    }
                    colIdx[i] = getPropertyId(col);
                    propCols[numPropCols++] = i;
                }
            }
            propCols = Arrays.copyOf(propCols, numPropCols);
            hashCols = Arrays.copyOf(hashCols, numHashCols);

            byte[][] prevHashes = new byte[hashTypes.length][];
            byte[][] hashes = new byte[hashTypes.length][];
            long t = System.currentTimeMillis();
            int cnt = 0;
            int numCols = header.size();
            Map<Integer, Set<String>> properties = new HashMap<Integer, Set<String>>();
            Iterator<CSVRecord> it = parser.iterator();
            CSVRecord prevRecord = null;
            while (it.hasNext()) {
                CSVRecord record = it.next();
                cnt++;
                if ((cnt & 8191) == 0) updatePercentage(record.getCharacterPosition() / (double) len);
                if (!record.isConsistent()) {
                    in.close();
                    throw new RuntimeException("Record #" + cnt + ": number of columns does not match header columns " + record.size() + "/" + numCols + ".\n" + record.toString());
                }
                Arrays.fill(hashes, null);
                for (int i : hashCols) {
                    int idx = colIdx[i];
                    hashes[idx] = hashStrToBytes(record.get(i).trim(), hashBytesLen[idx]);
                }
                boolean sameHashes = true;
                for (int i = 0; i < hashes.length; i++) {
                    byte[] a = hashes[i];
                    byte[] b = prevHashes[i];
                    if (a == null && b == null) continue;
                    if (a == null || b == null || !Arrays.equals(a, b)) {
                        sameHashes = false;
                        break;
                    }
                }
                if (sameHashes) {
                    totComb++;
                    if (mode == ProcessMode.REPLACE_ALL || mode == ProcessMode.REMOVE_ALL) {
                        properties.clear();
                    }
                } else {
                    if (prevRecord != null && !process(prevHashes, properties)) {
                        in.close();
                        throw new RuntimeException("Record #" + (cnt - 1) + ": invalid content:\n" + prevRecord);
                    }
                    properties.clear();
                    System.arraycopy(hashes, 0, prevHashes, 0, hashes.length);
                }
                for (int i : propCols) {
                    String val = record.get(i).trim();
                    if (!val.isEmpty()) {
                        if (i == photoDnaCol) {
                            if (val.length() != photoDnaBase64Len) {
                                in.close();
                                throw new RuntimeException("Record #" + cnt + ": invalid PhotoDna size content:\n" + record);
                            }
                        } else if (i == nsrlProductCodeCol) {
                            int prodCode = Integer.parseInt(val);
                            val = nsrlProdCodeToName.get(prodCode);
                            if (val == null) {
                                val = "Product #" + prodCode; 
                                totNoProd++;
                            }
                        }
                        int idx = colIdx[i];
                        merge(properties, idx, val);
                    }
                }
                if (setPropertyId != -1) {
                    merge(properties, setPropertyId, nsrlSetPropertyValue);
                }
                prevRecord = record;
            }
            if (!process(prevHashes, properties)) {
                in.close();
                throw new RuntimeException("Record #" + (cnt - 1) + ": invalid content:\n" + prevRecord);
            }
            updatePercentage(-1);
            System.out.println("\r" + cnt + " line" + (cnt == 1 ? "" : "s") + " read in " + endTime(t));
            printTotals();
        } catch (Exception e) {
            System.out.println("Error reading file: " + file);
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception e) {}
            try {
                if (zipInput != null) zipInput.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private void merge(Map<Integer, Set<String>> properties, int key, String newVal) {
        Set<String> st = properties.get(key);
        if (st == null) {
            properties.put(key, st = new HashSet<String>());
        } else if (mode == ProcessMode.REPLACE) {
            st.clear();
        }
        st.add(newVal);
    }

    private boolean readProjectVIC(File file) {
        FileInputStream is = null;
        RandomAccessFile raf = null;
        JsonParser jp = null;
        String hashName = null;
        try {
            long t = System.currentTimeMillis();
            int setPropertyId = getPropertyId(setPropertyName);
            int statusPropertyId = getPropertyId(statusPropertyName);
            int photoDnaPropertyId = getPropertyId(photoDnaPropertyName);

            JsonFactory jfactory = new JsonFactory();
            raf = new RandomAccessFile(file, "r");
            is = new FileInputStream(raf.getFD());
            jp = jfactory.createParser(is);
            if (jp.nextToken() != JsonToken.START_OBJECT) {
                throw new RuntimeException("Error: root JSON should be an object.");
            }

            byte[][] hashes = new byte[hashTypes.length][];
            Map<Integer, Set<String>> properties = new HashMap<Integer, Set<String>>();
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
                                    merge(properties, getPropertyId(vicPrefix + name), String.valueOf(cat));
                                    if (cat < vicStatusPropertyValues.length) {
                                        merge(properties, statusPropertyId, vicStatusPropertyValues[cat]);
                                    }
                                }
                            } else if ("MD5".equalsIgnoreCase(name) || "SHA1".equalsIgnoreCase(name)) {
                                String value = jp.nextTextValue().trim();
                                int idx = hashType(name);
                                if (idx >= 0) {
                                    hashes[idx] = hashStrToBytes(value, hashBytesLen[idx]);
                                    hasHash = true;
                                }
                            } else if ("VictimIdentified".equalsIgnoreCase(name) || "OffenderIdentified".equalsIgnoreCase(name) || "IsDistributed".equalsIgnoreCase(name) || "Series".equalsIgnoreCase(name) || "IsPrecategorized".equalsIgnoreCase(name) || "Tags".equalsIgnoreCase(name)) {
                                String value = jp.nextTextValue();
                                if (value != null) {
                                    value = value.replace('|', ' ').trim();
                                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                                        value = value.toLowerCase();
                                    }
                                    if (!value.isEmpty()) {
                                        merge(properties, getPropertyId(vicPrefix + name), value);
                                    }
                                }
                            } else if (token == JsonToken.END_OBJECT) {
                                if (hasHash && !properties.isEmpty()) {
                                    merge(properties, setPropertyId, vicSetPropertyValue);
                                    process(hashes, properties);
                                    if ((cnt++ & 8191) == 0) {
                                        long pos = raf.getFilePointer();
                                        updatePercentage(pos / (double) len);
                                    }
                                    hasHash = false;
                                    hashName = null;
                                }
                            }
                        } else if (arrayDepth == 2) {
                            String name = jp.currentName();
                            if ("HashName".equalsIgnoreCase(name)) {
                                hashName = jp.nextTextValue();
                                if (hashName != null) {
                                    hashName = hashName.trim();
                                }
                            } else if ("HashValue".equalsIgnoreCase(name) && "PhotoDNA".equalsIgnoreCase(hashName)) {
                                String value = jp.nextTextValue();
                                if (value != null) {
                                    value = value.trim();
                                    if (value.length() == photoDnaBase64Len) {
                                        merge(properties, photoDnaPropertyId, value);
                                    }
                                }
                            }
                        }
                    } while (arrayDepth > 0);
                } else {
                    jp.close();
                    raf.close();
                    throw new RuntimeException("Error: Unexpected property in ProjectVic JSON '" + jp.currentName() + "'.");
                }
            }
            updatePercentage(-1);
            System.out.println("\r" + cnt + " records read in " + endTime(t));
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
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
            List<String> header = parser.getHeaderNames();
            if (header == null || header.isEmpty()) {
                System.out.println("ERROR: Invalid file " + file.getPath() + ", header not found.");
                return false;
            }
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
            Iterator<CSVRecord> it = parser.iterator();
            while (it.hasNext()) {
                CSVRecord record = it.next();
                cnt++;
                if (!record.isConsistent()) {
                    in.close();
                    throw new RuntimeException("Error: Record #" + cnt + ": number of columns does not match header columns " + record.size() + "/" + numCols + ".\n" + record);
                }
                int code = Integer.parseInt(record.get(colProdCode));
                String name = record.get(colProdName);
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
        if (totIgn > 0) System.out.println(totIgn + " zero length hash" + (totIgn == 1 ? " was" : "es were") + " ignored.");
        if (totComb > 0) System.out.println(totComb + " record" + (totComb == 1 ? "" : "s") + " combined.");
        if (totNoProd > 0) System.out.println("WARNING: " + totNoProd + " NSRL record" + (totNoProd == 1 ? "" : "s") + " with invalid product code.");
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
        Integer id = propertyNameToId.get(name.toLowerCase());
        if (id == null) {
            propertyNameToId.put(name.toLowerCase(), id = ++lastPropertyId);
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
            if (type == FileType.NSRL_MAIN || type == FileType.NSRL_MAIN_ZIP) {
                if (!checkNSRLHeader(file, type)) return false;
                File prodFile = new File(file.getParentFile(), nsrlProdFileName);
                if (!prodFile.exists() || !prodFile.isFile()) {
                    System.out.println("ERROR: File " + prodFile.getName() + " must be present in the same folder of NSRL main file (" + file.getName() + ").");
                    return false;
                }
                if (!checkNSRLHeader(prodFile, FileType.NSRL_PROD)) return false;
                //Insert NSRL Product file before the main file. 
                inputs.add(i++, prodFile);
            } else if (type == FileType.CSV || type == FileType.INPUT) {
                if (!checkInputHeader(file)) return false;
            } else if (type == FileType.PROJECT_VIC) {
                //Identification was based on its content (plus file extension) 
            } else {
                if (type == FileType.UNKNOWN) {
                    System.out.println("File " + file.getPath() + " skipped.");
                }
                inputs.remove(i--);
            }
        }
        for (int i = 0; i < inputs.size(); i++) {
            File f1 = inputs.get(i);
            FileType t1 = getFileType(f1);
            if (t1 == FileType.NSRL_MAIN_ZIP && f1.getParentFile() != null) {
                for (int j = 0; j < inputs.size(); j++) {
                    File f2 = inputs.get(j);
                    FileType t2 = getFileType(f2);
                    if (t2 == FileType.NSRL_MAIN) {
                        if (f1.getParentFile().equals(f2.getParentFile())) {
                            //If both TXT and ZIP are present, process only the TXT file
                            System.out.println("File " + f1.getPath() + " skipped.");
                            inputs.remove(i--);
                            break;
                        }
                    }
                }
            }
        }
        return true;
    }

    private FileType getFileType(File file) {
        String name = file.getName().toLowerCase();
        if (name.equalsIgnoreCase(nsrlMainFileName)) return FileType.NSRL_MAIN;
        if (name.equalsIgnoreCase(nsrlMainZipFileName)) return FileType.NSRL_MAIN_ZIP;
        if (name.equalsIgnoreCase(nsrlProdFileName)) return FileType.NSRL_PROD;
        if (name.endsWith(".csv")) return FileType.CSV;
        if (name.endsWith(".json") && isProjectVicJson(file)) return FileType.PROJECT_VIC;
        if (!inputFolderUsed) return FileType.INPUT;
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
        ZipInputStream zipInput = null;
        try {
            if (type == FileType.NSRL_MAIN_ZIP) {
                zipInput = new ZipInputStream(new FileInputStream(file));
                ZipEntry entry = null;
                while ((entry = zipInput.getNextEntry()) != null) {
                    if (entry.getName().equalsIgnoreCase(nsrlMainFileName)) {
                        in = new BufferedReader(new InputStreamReader(zipInput, StandardCharsets.ISO_8859_1));
                        break;
                    }
                }
                if (in == null) {
                    System.out.println("ERROR: Invalid NSRL ZIP file " + file.getPath() + ", NSRL text file entry not found.");
                    return false;
                }
            } else {
                in = new BufferedReader(new FileReader(file));
            }
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
            List<String> header = parser.getHeaderNames();
            if (header == null || header.isEmpty()) {
                System.out.println("ERROR: Invalid file " + file.getPath() + ", header not found.");
                return false;
            }
            boolean hasHash = false;
            boolean hasProductCode = false;
            boolean hasProductName = false;
            for (String col : header) {
                if (hashType(col) >= 0) hasHash = true;
                else if (col.equalsIgnoreCase(nsrlProductCode)) hasProductCode = true;
                else if (col.equalsIgnoreCase(nsrlProductName)) hasProductName = true;
            }
            if (!hasHash && (type == FileType.NSRL_MAIN || type == FileType.NSRL_MAIN_ZIP)) {
                System.out.println("ERROR: Invalid NSRL file " + file + ", no hash was found in its header.");
                return false;
            }
            if (!hasProductCode) {
                System.out.println("ERROR: Invalid NSRL file " + file + ", no product code was found in its header.");
                return false;
            }
            if (!hasProductName && type == FileType.NSRL_PROD) {
                System.out.println("ERROR: Invalid NSRL file " + file + ", no product name was found in its header.");
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
            try {
                if (zipInput != null) zipInput.close();
            } catch (Exception e) {}
        }
        return true;
    }

    private boolean checkInputHeader(File file) {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(file));
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(in);
            List<String> header = parser.getHeaderNames();
            if (header == null || header.isEmpty()) {
                System.out.println("ERROR: Invalid file " + file.getPath() + ", header not found.");
                return false;
            }
            boolean hasHash = false;
            boolean hasProperty = false;
            for (String col : header) {
                if (hashType(col) >= 0) hasHash = true;
                else hasProperty = true;
            }
            if (!hasHash) {
                System.out.println("ERROR: File " + file + " header must contain at least one hash column.");
                return false;
            }
            if (!hasProperty) {
                System.out.println("ERROR: File " + file + " header must contain at least one property column.");
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

    private String endTime(long start) {
        long time = (System.currentTimeMillis() - start) / 1000;
        return time + " second" + (time == 1 ? "." : "s.");
    }

    void finish(boolean success) {
        try {
            if (success) {
                long t = System.currentTimeMillis();
                System.out.println("\nCommiting changes...");
                connection.commit();
                System.out.println("Commit completed in " + endTime(t));

                if (!skipOpt) {
                    t = System.currentTimeMillis();
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
                    System.out.println("Optimization completed in " + endTime(t));
                }
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
            if (stmtRemoveAllHashProperties != null) stmtRemoveAllHashProperties.close();
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
            stmtRemoveAllHashProperties = connection.prepareStatement("delete from HASHES_PROPERTIES where HASH_ID=?");

            stmtSelectHashProperties = connection.prepareStatement("select PROPERTY_ID, VALUE from HASHES_PROPERTIES where HASH_ID=?");
            stmtSelectHashProperties.setFetchSize(64);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
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
                    inputFolderUsed = true;
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
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other process mode option.");
                    return false;
                }
                mode = ProcessMode.REPLACE;
            } else if (arg.equalsIgnoreCase("-replaceAll")) {
                if (mode != ProcessMode.UNDEFINED) {
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other process mode option.");
                    return false;
                }
                mode = ProcessMode.REPLACE_ALL;
            } else if (arg.equalsIgnoreCase("-remove")) {
                if (mode != ProcessMode.UNDEFINED) {
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other process mode option.");
                    return false;
                }
                mode = ProcessMode.REMOVE;
            } else if (arg.equalsIgnoreCase("-removeAll")) {
                if (mode != ProcessMode.UNDEFINED) {
                    System.out.println("ERROR: parameter '" + arg + "' can not be combined with other process mode option.");
                    return false;
                }
                mode = ProcessMode.REMOVE_ALL;
            } else if (arg.equalsIgnoreCase("-noOpt")) {
                skipOpt = true;
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

    private void usage() {
        System.out.println();
        System.out.println("IPED HashDB Tool");
        System.out.println("    Allows importing CSV files with a set of hashes and associated properties");
        System.out.println("    into a database that can be later used during IPED case processing, to");
        System.out.println("    search for these hashes and add their properties to the case item when a");
        System.out.println("    hit is found. NIST NSRL files and Project VIC JSON can also be imported.");
        System.out.println();
        System.out.println("Usage: java -jar iped-hashdb.jar -d <input file or folder> -o <output DB file>");
        System.out.println("            [-replace | -replaceAll | -remove | -removeAll] [-noOpt]");
        System.out.println();
        System.out.println("  -d");
        System.out.println("    Input files (can be used multiple times). If a folder is used, it processes");
        System.out.println("    all files with '.csv' extension, NSRL or Project Vic files. CSV Input files");
        System.out.println("    should use plain text format, one item per line, with columns separated by");
        System.out.println("    commas. The first line must be a header, defining the columns names. There");
        System.out.println("    must be one or more hash columns and one or more properties columns.");
        System.out.println("  -o");
        System.out.println("    Output database file. If it exists, data will be added to (or removed");
        System.out.println("    from) the existing database.");
        System.out.println();
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
        System.out.println("  -noOpt");
        System.out.println("    Skip optimizations (reclaim empty space and database analisys) executed");
        System.out.println("    after processing input file(s).");
    }

    enum ProcessMode {
        UNDEFINED, MERGE, REPLACE, REPLACE_ALL, REMOVE, REMOVE_ALL;
    }

    enum FileType {
        CSV, NSRL_MAIN, NSRL_MAIN_ZIP, NSRL_PROD, PROJECT_VIC, UNKNOWN, INPUT;
    }
}